-- =============================================================================
-- V3 — Inventory
-- PRD §9. Backs the "Inventory accuracy" success metric (§2).
--
-- Design rule for this module: stock_transactions is an append-only ledger and is
-- the source of truth. inventory.qty_on_hand is a cached rollup of it. Any mutation
-- writes a ledger row, so on-hand can always be recomputed and diffed against the
-- cache. That is what makes accuracy auditable rather than aspirational.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- warehouses
-- address_id is added in V4, once addresses exists — keeps this file self-contained.
-- -----------------------------------------------------------------------------
CREATE TABLE warehouses (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code        TEXT        NOT NULL,
    name        TEXT        NOT NULL,
    city        TEXT,
    state       TEXT,
    pincode     TEXT,
    is_active   BOOLEAN     NOT NULL DEFAULT TRUE,

    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID,
    deleted_at  TIMESTAMPTZ,
    version     BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT warehouses_pincode_chk CHECK (pincode IS NULL OR pincode ~ '^[1-9][0-9]{5}$')
);

CREATE UNIQUE INDEX warehouses_code_uq ON warehouses (code) WHERE deleted_at IS NULL;

-- -----------------------------------------------------------------------------
-- suppliers
-- -----------------------------------------------------------------------------
CREATE TABLE suppliers (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code            TEXT        NOT NULL,
    name            TEXT        NOT NULL,
    contact_name    TEXT,
    contact_email   TEXT,
    contact_phone   TEXT,
    gstin           TEXT,
    fssai_license   TEXT,
    address_line    TEXT,
    city            TEXT,
    state           TEXT,
    pincode         TEXT,
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_by      UUID,
    deleted_at      TIMESTAMPTZ,
    version         BIGINT      NOT NULL DEFAULT 0,

    -- GSTIN: 2-digit state code, 10-char PAN, entity digit, 'Z', checksum.
    CONSTRAINT suppliers_gstin_chk CHECK (
        gstin IS NULL OR gstin ~ '^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$'
    ),
    CONSTRAINT suppliers_phone_chk CHECK (
        contact_phone IS NULL OR contact_phone ~ '^(\+91)?[6-9][0-9]{9}$'
    ),
    CONSTRAINT suppliers_fssai_chk CHECK (fssai_license IS NULL OR fssai_license ~ '^[0-9]{14}$')
);

CREATE UNIQUE INDEX suppliers_code_uq ON suppliers (code) WHERE deleted_at IS NULL;

-- -----------------------------------------------------------------------------
-- inventory  (stock position per variant per warehouse)
-- -----------------------------------------------------------------------------
CREATE TABLE inventory (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    warehouse_id   UUID        NOT NULL REFERENCES warehouses (id)      ON DELETE RESTRICT,
    variant_id     UUID        NOT NULL REFERENCES product_variants (id) ON DELETE RESTRICT,

    -- Physical units in the building.
    qty_on_hand    INT         NOT NULL DEFAULT 0,
    -- Units promised to carts/orders that have not shipped. Available = on_hand - reserved.
    -- Without this split, two customers can both buy the last unit and one gets an apology.
    qty_reserved   INT         NOT NULL DEFAULT 0,
    reorder_point  INT         NOT NULL DEFAULT 0,
    reorder_qty    INT         NOT NULL DEFAULT 0,

    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by     UUID,
    updated_by     UUID,
    deleted_at     TIMESTAMPTZ,
    -- Optimistic locking. Two concurrent decrements must not both succeed.
    version        BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT inventory_on_hand_nonneg  CHECK (qty_on_hand >= 0),
    CONSTRAINT inventory_reserved_nonneg CHECK (qty_reserved >= 0),
    -- We can never reserve more than physically exists. This is the invariant that
    -- makes overselling impossible at the database level rather than by convention.
    CONSTRAINT inventory_reserved_lte_on_hand CHECK (qty_reserved <= qty_on_hand),
    CONSTRAINT inventory_warehouse_variant_uq UNIQUE (warehouse_id, variant_id)
);

CREATE INDEX inventory_variant_idx ON inventory (variant_id) WHERE deleted_at IS NULL;
-- Drives the low-stock report.
CREATE INDEX inventory_reorder_idx ON inventory (warehouse_id)
    WHERE deleted_at IS NULL AND qty_on_hand <= reorder_point;

-- -----------------------------------------------------------------------------
-- inventory_batches  (FEFO — organic produce expires)
-- -----------------------------------------------------------------------------
CREATE TABLE inventory_batches (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inventory_id  UUID          NOT NULL REFERENCES inventory (id) ON DELETE CASCADE,
    lot_no        TEXT          NOT NULL,
    supplier_id   UUID REFERENCES suppliers (id) ON DELETE SET NULL,
    qty_received  INT           NOT NULL,
    qty_remaining INT           NOT NULL,
    cost_price    NUMERIC(12,2) NOT NULL,
    received_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    expiry_date   DATE,

    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_by    UUID,
    deleted_at    TIMESTAMPTZ,
    version       BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT batches_qty_chk       CHECK (qty_received > 0 AND qty_remaining >= 0),
    CONSTRAINT batches_remaining_chk CHECK (qty_remaining <= qty_received),
    CONSTRAINT batches_cost_chk      CHECK (cost_price >= 0),
    CONSTRAINT batches_lot_uq        UNIQUE (inventory_id, lot_no)
);

-- FEFO allocation: pick the earliest-expiring batch with stock left.
CREATE INDEX batches_fefo_idx ON inventory_batches (inventory_id, expiry_date)
    WHERE deleted_at IS NULL AND qty_remaining > 0;
-- Drives the expiry-tracking alert (PRD §9).
CREATE INDEX batches_expiry_idx ON inventory_batches (expiry_date)
    WHERE deleted_at IS NULL AND qty_remaining > 0;

-- -----------------------------------------------------------------------------
-- stock_transactions  (APPEND-ONLY LEDGER — no soft delete, no version)
-- Deleting stock history breaks reconciliation. Corrections are new reversing rows.
-- -----------------------------------------------------------------------------
CREATE TABLE stock_transactions (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inventory_id  UUID        NOT NULL REFERENCES inventory (id) ON DELETE RESTRICT,
    batch_id      UUID REFERENCES inventory_batches (id) ON DELETE RESTRICT,
    type          TEXT        NOT NULL,
    -- Signed: +receipt, -sale. Sum over an inventory_id must equal qty_on_hand.
    qty_delta     INT         NOT NULL,
    -- What caused this movement: ('ORDER', order_id), ('PURCHASE_ORDER', po_id), etc.
    -- Deliberately not an FK — it points at several different tables, and some
    -- (orders) do not exist until V4.
    ref_type      TEXT,
    ref_id        UUID,
    note          TEXT,

    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by    UUID,

    CONSTRAINT stock_tx_type_chk CHECK (type IN (
        'RECEIPT', 'SALE', 'RETURN', 'ADJUSTMENT', 'TRANSFER_IN', 'TRANSFER_OUT',
        'DAMAGE', 'EXPIRY', 'RESERVATION', 'RELEASE'
    )),
    -- A zero-delta movement is always a bug.
    CONSTRAINT stock_tx_delta_chk CHECK (qty_delta <> 0)
);

CREATE INDEX stock_tx_inventory_idx ON stock_transactions (inventory_id, created_at DESC);
CREATE INDEX stock_tx_ref_idx       ON stock_transactions (ref_type, ref_id);
CREATE INDEX stock_tx_batch_idx     ON stock_transactions (batch_id);

-- Enforce append-only at the database, not by convention. An UPDATE or DELETE here
-- means a bug or a bad actor; either way it must fail loudly rather than silently
-- rewrite financial history.
CREATE OR REPLACE FUNCTION reject_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION '% is append-only: % is not permitted', TG_TABLE_NAME, TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER stock_transactions_append_only
    BEFORE UPDATE OR DELETE ON stock_transactions
    FOR EACH ROW EXECUTE FUNCTION reject_mutation();

-- -----------------------------------------------------------------------------
-- purchase_orders
-- -----------------------------------------------------------------------------
CREATE TABLE purchase_orders (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    po_number     TEXT          NOT NULL,
    supplier_id   UUID          NOT NULL REFERENCES suppliers (id)  ON DELETE RESTRICT,
    warehouse_id  UUID          NOT NULL REFERENCES warehouses (id) ON DELETE RESTRICT,
    status        TEXT          NOT NULL DEFAULT 'DRAFT',
    expected_at   DATE,
    received_at   TIMESTAMPTZ,
    subtotal      NUMERIC(12,2) NOT NULL DEFAULT 0,
    tax_total     NUMERIC(12,2) NOT NULL DEFAULT 0,
    grand_total   NUMERIC(12,2) NOT NULL DEFAULT 0,
    currency      CHAR(3)       NOT NULL DEFAULT 'INR',
    notes         TEXT,

    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_by    UUID,
    deleted_at    TIMESTAMPTZ,
    version       BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT po_status_chk CHECK (status IN (
        'DRAFT', 'SUBMITTED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'CANCELLED'
    )),
    CONSTRAINT po_totals_chk CHECK (subtotal >= 0 AND tax_total >= 0 AND grand_total >= 0)
);

CREATE UNIQUE INDEX po_number_uq ON purchase_orders (po_number) WHERE deleted_at IS NULL;
CREATE INDEX po_supplier_idx ON purchase_orders (supplier_id) WHERE deleted_at IS NULL;
CREATE INDEX po_status_idx   ON purchase_orders (status)      WHERE deleted_at IS NULL;

CREATE TABLE purchase_order_items (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    po_id         UUID          NOT NULL REFERENCES purchase_orders (id)  ON DELETE CASCADE,
    variant_id    UUID          NOT NULL REFERENCES product_variants (id) ON DELETE RESTRICT,
    qty_ordered   INT           NOT NULL,
    qty_received  INT           NOT NULL DEFAULT 0,
    unit_cost     NUMERIC(12,2) NOT NULL,
    line_total    NUMERIC(12,2) NOT NULL,

    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_by    UUID,
    deleted_at    TIMESTAMPTZ,
    version       BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT po_item_qty_chk      CHECK (qty_ordered > 0 AND qty_received >= 0),
    -- Receiving more than ordered means a data-entry error or a supplier dispute.
    -- Either way it needs a human, not a silent accept.
    CONSTRAINT po_item_received_chk CHECK (qty_received <= qty_ordered),
    CONSTRAINT po_item_cost_chk     CHECK (unit_cost >= 0),
    CONSTRAINT po_item_variant_uq   UNIQUE (po_id, variant_id)
);

CREATE INDEX po_items_variant_idx ON purchase_order_items (variant_id);

-- -----------------------------------------------------------------------------
-- stock_adjustments  (reason-coded corrections requiring approval)
-- -----------------------------------------------------------------------------
CREATE TABLE stock_adjustments (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inventory_id  UUID        NOT NULL REFERENCES inventory (id) ON DELETE RESTRICT,
    batch_id      UUID REFERENCES inventory_batches (id) ON DELETE RESTRICT,
    reason        TEXT        NOT NULL,
    qty_delta     INT         NOT NULL,
    note          TEXT,
    approved_by   UUID REFERENCES users (id) ON DELETE SET NULL,
    approved_at   TIMESTAMPTZ,

    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_by    UUID,
    deleted_at    TIMESTAMPTZ,
    version       BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT adjustment_reason_chk CHECK (reason IN (
        'DAMAGE', 'EXPIRY', 'COUNT_CORRECTION', 'THEFT', 'SAMPLE', 'OTHER'
    )),
    CONSTRAINT adjustment_delta_chk CHECK (qty_delta <> 0),
    -- Shrinkage is where money quietly leaks. An applied adjustment must name who
    -- approved it; approved_at and approved_by move together or not at all.
    CONSTRAINT adjustment_approval_chk CHECK (
        (approved_at IS NULL AND approved_by IS NULL) OR
        (approved_at IS NOT NULL AND approved_by IS NOT NULL)
    )
);

CREATE INDEX stock_adjustments_inventory_idx ON stock_adjustments (inventory_id)
    WHERE deleted_at IS NULL;
CREATE INDEX stock_adjustments_pending_idx ON stock_adjustments (created_at)
    WHERE deleted_at IS NULL AND approved_at IS NULL;

-- -----------------------------------------------------------------------------
-- low_stock_alerts
-- -----------------------------------------------------------------------------
CREATE TABLE low_stock_alerts (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inventory_id  UUID        NOT NULL REFERENCES inventory (id) ON DELETE CASCADE,
    qty_at_trigger INT        NOT NULL,
    reorder_point  INT        NOT NULL,
    triggered_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at   TIMESTAMPTZ,
    notified_at   TIMESTAMPTZ,

    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_by    UUID,
    version       BIGINT      NOT NULL DEFAULT 0
);

-- Only one open alert per inventory row — otherwise a product hovering at the
-- reorder point generates an alert per stock movement and buries the real ones.
CREATE UNIQUE INDEX low_stock_one_open_per_inventory
    ON low_stock_alerts (inventory_id) WHERE resolved_at IS NULL;

-- -----------------------------------------------------------------------------
-- Ledger reconciliation view
-- Recomputes on-hand from stock_transactions and diffs it against the cached
-- inventory.qty_on_hand. A non-empty result means the cache drifted from the
-- ledger — an accuracy bug. This is the query behind the PRD §2 metric.
-- -----------------------------------------------------------------------------
CREATE VIEW inventory_ledger_drift AS
SELECT
    i.id AS inventory_id,
    i.warehouse_id,
    i.variant_id,
    i.qty_on_hand AS cached_qty,
    coalesce(sum(t.qty_delta) FILTER (
        WHERE t.type NOT IN ('RESERVATION', 'RELEASE')
    ), 0) AS ledger_qty,
    i.qty_on_hand - coalesce(sum(t.qty_delta) FILTER (
        WHERE t.type NOT IN ('RESERVATION', 'RELEASE')
    ), 0) AS drift
FROM inventory i
LEFT JOIN stock_transactions t ON t.inventory_id = i.id
WHERE i.deleted_at IS NULL
GROUP BY i.id, i.warehouse_id, i.variant_id, i.qty_on_hand
HAVING i.qty_on_hand <> coalesce(sum(t.qty_delta) FILTER (
    WHERE t.type NOT IN ('RESERVATION', 'RELEASE')
), 0);

-- -----------------------------------------------------------------------------
-- updated_at triggers
-- -----------------------------------------------------------------------------
CREATE TRIGGER warehouses_set_updated_at BEFORE UPDATE ON warehouses
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER suppliers_set_updated_at BEFORE UPDATE ON suppliers
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER inventory_set_updated_at BEFORE UPDATE ON inventory
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER inventory_batches_set_updated_at BEFORE UPDATE ON inventory_batches
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER purchase_orders_set_updated_at BEFORE UPDATE ON purchase_orders
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER purchase_order_items_set_updated_at BEFORE UPDATE ON purchase_order_items
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER stock_adjustments_set_updated_at BEFORE UPDATE ON stock_adjustments
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER low_stock_alerts_set_updated_at BEFORE UPDATE ON low_stock_alerts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
