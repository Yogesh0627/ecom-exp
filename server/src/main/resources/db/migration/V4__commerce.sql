-- =============================================================================
-- V4 — Commerce
-- PRD §10 (User Journey): Cart -> Checkout -> Order -> Tracking.
--
-- Design rule for this module: an order is a historical record, not a live view of
-- the catalog. Prices, product names, SKUs and the delivery address are SNAPSHOT
-- onto the order at placement. If a product is renamed, repriced or deleted next
-- week, a six-month-old invoice must still render exactly as the customer received
-- it. Joining live catalog rows to old orders silently rewrites history.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- addresses
-- -----------------------------------------------------------------------------
CREATE TABLE addresses (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID REFERENCES users (id) ON DELETE CASCADE,
    label         TEXT,
    recipient_name TEXT       NOT NULL,
    phone         TEXT        NOT NULL,
    line1         TEXT        NOT NULL,
    line2         TEXT,
    landmark      TEXT,
    city          TEXT        NOT NULL,
    state         TEXT        NOT NULL,
    pincode       TEXT        NOT NULL,
    country       CHAR(2)     NOT NULL DEFAULT 'IN',
    type          TEXT        NOT NULL DEFAULT 'HOME',
    is_default    BOOLEAN     NOT NULL DEFAULT FALSE,
    latitude      NUMERIC(9,6),
    longitude     NUMERIC(9,6),

    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_by    UUID,
    deleted_at    TIMESTAMPTZ,
    version       BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT addresses_type_chk    CHECK (type IN ('HOME', 'WORK', 'WAREHOUSE', 'OTHER')),
    CONSTRAINT addresses_pincode_chk CHECK (pincode ~ '^[1-9][0-9]{5}$'),
    CONSTRAINT addresses_phone_chk   CHECK (phone ~ '^(\+91)?[6-9][0-9]{9}$'),
    -- user_id is nullable so warehouse addresses can exist without an owner, but a
    -- customer-facing address type must belong to someone.
    CONSTRAINT addresses_owner_chk   CHECK (type = 'WAREHOUSE' OR user_id IS NOT NULL)
);

CREATE INDEX addresses_user_idx ON addresses (user_id) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX addresses_one_default_per_user
    ON addresses (user_id) WHERE is_default AND deleted_at IS NULL AND user_id IS NOT NULL;

-- Deferred from V3: warehouses need an address, addresses did not exist yet.
ALTER TABLE warehouses ADD COLUMN address_id UUID REFERENCES addresses (id) ON DELETE SET NULL;

-- -----------------------------------------------------------------------------
-- carts
-- -----------------------------------------------------------------------------
CREATE TABLE carts (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID REFERENCES users (id) ON DELETE CASCADE,
    -- Anonymous carts: a browser token before login. Merged into the user cart on
    -- sign-in, which is why user_id is nullable.
    session_key TEXT,
    status      TEXT        NOT NULL DEFAULT 'ACTIVE',
    expires_at  TIMESTAMPTZ,

    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID,
    deleted_at  TIMESTAMPTZ,
    version     BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT carts_status_chk CHECK (status IN ('ACTIVE', 'CONVERTED', 'ABANDONED', 'EXPIRED')),
    CONSTRAINT carts_owner_chk  CHECK (user_id IS NOT NULL OR session_key IS NOT NULL)
);

-- One ACTIVE cart per user. Two active carts means items silently vanish depending
-- on which one the request picks up.
CREATE UNIQUE INDEX carts_one_active_per_user
    ON carts (user_id) WHERE status = 'ACTIVE' AND deleted_at IS NULL AND user_id IS NOT NULL;
CREATE UNIQUE INDEX carts_one_active_per_session
    ON carts (session_key) WHERE status = 'ACTIVE' AND deleted_at IS NULL AND session_key IS NOT NULL;
CREATE INDEX carts_abandoned_idx ON carts (updated_at) WHERE status = 'ACTIVE' AND deleted_at IS NULL;

CREATE TABLE cart_items (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cart_id              UUID          NOT NULL REFERENCES carts (id)            ON DELETE CASCADE,
    variant_id           UUID          NOT NULL REFERENCES product_variants (id) ON DELETE RESTRICT,
    qty                  INT           NOT NULL,
    -- Price when added. Compared against the live price at checkout so the customer
    -- is TOLD about drift rather than surprised by it.
    unit_price_snapshot  NUMERIC(12,2) NOT NULL,

    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by           UUID,
    updated_by           UUID,
    deleted_at           TIMESTAMPTZ,
    version              BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT cart_items_qty_chk   CHECK (qty > 0),
    CONSTRAINT cart_items_price_chk CHECK (unit_price_snapshot >= 0),
    -- Adding the same variant twice increments qty; it never creates a second row.
    CONSTRAINT cart_items_variant_uq UNIQUE (cart_id, variant_id)
);

CREATE INDEX cart_items_variant_idx ON cart_items (variant_id) WHERE deleted_at IS NULL;

-- -----------------------------------------------------------------------------
-- wishlists
-- -----------------------------------------------------------------------------
CREATE TABLE wishlists (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name        TEXT        NOT NULL DEFAULT 'My Wishlist',
    is_default  BOOLEAN     NOT NULL DEFAULT TRUE,

    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID,
    deleted_at  TIMESTAMPTZ,
    version     BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX wishlists_user_idx ON wishlists (user_id) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX wishlists_one_default_per_user
    ON wishlists (user_id) WHERE is_default AND deleted_at IS NULL;

CREATE TABLE wishlist_items (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wishlist_id UUID        NOT NULL REFERENCES wishlists (id)        ON DELETE CASCADE,
    variant_id  UUID        NOT NULL REFERENCES product_variants (id) ON DELETE CASCADE,
    note        TEXT,

    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID,
    deleted_at  TIMESTAMPTZ,
    version     BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT wishlist_items_variant_uq UNIQUE (wishlist_id, variant_id)
);

-- -----------------------------------------------------------------------------
-- orders
--
-- GST: India requires CGST/SGST for intra-state supply and IGST for inter-state,
-- itemised on the invoice. A single tax_total column cannot produce a compliant
-- invoice. Splitting costs nothing now and is a painful backfill once real orders
-- exist, so the columns are here from the start.
-- -----------------------------------------------------------------------------
CREATE TABLE orders (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_number       TEXT          NOT NULL,
    user_id            UUID          NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    status             TEXT          NOT NULL DEFAULT 'PENDING_PAYMENT',

    subtotal           NUMERIC(12,2) NOT NULL DEFAULT 0,
    discount_total     NUMERIC(12,2) NOT NULL DEFAULT 0,
    cgst_total         NUMERIC(12,2) NOT NULL DEFAULT 0,
    sgst_total         NUMERIC(12,2) NOT NULL DEFAULT 0,
    igst_total         NUMERIC(12,2) NOT NULL DEFAULT 0,
    tax_total          NUMERIC(12,2) NOT NULL DEFAULT 0,
    shipping_fee       NUMERIC(12,2) NOT NULL DEFAULT 0,
    grand_total        NUMERIC(12,2) NOT NULL DEFAULT 0,
    currency           CHAR(3)       NOT NULL DEFAULT 'INR',

    -- Delivery address SNAPSHOT. Not an FK to addresses: if the customer edits or
    -- deletes that address later, this order must still show where it actually went.
    ship_recipient_name TEXT         NOT NULL,
    ship_phone          TEXT         NOT NULL,
    ship_line1          TEXT         NOT NULL,
    ship_line2          TEXT,
    ship_landmark       TEXT,
    ship_city           TEXT         NOT NULL,
    ship_state          TEXT         NOT NULL,
    ship_pincode        TEXT         NOT NULL,
    ship_country        CHAR(2)      NOT NULL DEFAULT 'IN',

    warehouse_id       UUID REFERENCES warehouses (id) ON DELETE SET NULL,
    placed_at          TIMESTAMPTZ,
    cancelled_at       TIMESTAMPTZ,
    cancellation_reason TEXT,
    customer_note      TEXT,

    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    deleted_at         TIMESTAMPTZ,
    version            BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT orders_status_chk CHECK (status IN (
        'PENDING_PAYMENT', 'PAID', 'CONFIRMED', 'PACKED', 'SHIPPED',
        'OUT_FOR_DELIVERY', 'DELIVERED', 'CANCELLED', 'RETURNED', 'REFUNDED'
    )),
    CONSTRAINT orders_amounts_nonneg CHECK (
        subtotal >= 0 AND discount_total >= 0 AND tax_total >= 0 AND
        shipping_fee >= 0 AND grand_total >= 0 AND
        cgst_total >= 0 AND sgst_total >= 0 AND igst_total >= 0
    ),
    -- The invoice must add up. Catching this at the DB means a rounding bug in the
    -- pricing engine cannot quietly ship a wrong total to a customer.
    CONSTRAINT orders_total_balances CHECK (
        grand_total = subtotal - discount_total + tax_total + shipping_fee
    ),
    -- GST is either intra-state (CGST+SGST) or inter-state (IGST). Never both.
    CONSTRAINT orders_gst_split_chk CHECK (
        (igst_total = 0) OR (cgst_total = 0 AND sgst_total = 0)
    ),
    CONSTRAINT orders_tax_sums_chk CHECK (tax_total = cgst_total + sgst_total + igst_total),
    CONSTRAINT orders_discount_chk CHECK (discount_total <= subtotal)
);

CREATE UNIQUE INDEX orders_number_uq ON orders (order_number);
CREATE INDEX orders_user_idx    ON orders (user_id, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX orders_status_idx  ON orders (status)                   WHERE deleted_at IS NULL;
CREATE INDEX orders_placed_idx  ON orders (placed_at DESC)           WHERE deleted_at IS NULL;

CREATE TABLE order_items (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id               UUID          NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    -- RESTRICT, not CASCADE: a variant must never be hard-deleted out from under an
    -- order line. Catalog removal is a soft delete.
    variant_id             UUID          NOT NULL REFERENCES product_variants (id) ON DELETE RESTRICT,

    -- Catalog SNAPSHOT at placement — see the module note at the top.
    product_name_snapshot  TEXT          NOT NULL,
    variant_name_snapshot  TEXT          NOT NULL,
    sku_snapshot           TEXT          NOT NULL,
    image_url_snapshot     TEXT,

    qty                    INT           NOT NULL,
    unit_price             NUMERIC(12,2) NOT NULL,
    discount_amount        NUMERIC(12,2) NOT NULL DEFAULT 0,
    tax_rate_pct           NUMERIC(5,2)  NOT NULL DEFAULT 0,
    tax_amount             NUMERIC(12,2) NOT NULL DEFAULT 0,
    line_total             NUMERIC(12,2) NOT NULL,
    -- HSN code is mandatory on a GST invoice.
    hsn_code               TEXT,

    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by             UUID,
    updated_by             UUID,
    deleted_at             TIMESTAMPTZ,
    version                BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT order_items_qty_chk    CHECK (qty > 0),
    CONSTRAINT order_items_price_chk  CHECK (unit_price >= 0 AND discount_amount >= 0),
    CONSTRAINT order_items_line_chk   CHECK (line_total = (unit_price * qty) - discount_amount + tax_amount),
    CONSTRAINT order_items_variant_uq UNIQUE (order_id, variant_id)
);

CREATE INDEX order_items_order_idx   ON order_items (order_id)   WHERE deleted_at IS NULL;
CREATE INDEX order_items_variant_idx ON order_items (variant_id) WHERE deleted_at IS NULL;

-- -----------------------------------------------------------------------------
-- order_status_history  (APPEND-ONLY)
-- "When did this order ship?" must be answerable months later, including for
-- disputes. History is never edited.
-- -----------------------------------------------------------------------------
CREATE TABLE order_status_history (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id    UUID        NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    from_status TEXT,
    to_status   TEXT        NOT NULL,
    note        TEXT,
    changed_by  UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX order_status_history_order_idx ON order_status_history (order_id, created_at);

CREATE TRIGGER order_status_history_append_only
    BEFORE UPDATE OR DELETE ON order_status_history
    FOR EACH ROW EXECUTE FUNCTION reject_mutation();

-- -----------------------------------------------------------------------------
-- shipments
-- -----------------------------------------------------------------------------
CREATE TABLE shipments (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id      UUID        NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    carrier       TEXT,
    tracking_no   TEXT,
    tracking_url  TEXT,
    status        TEXT        NOT NULL DEFAULT 'PENDING',
    shipped_at    TIMESTAMPTZ,
    delivered_at  TIMESTAMPTZ,
    delivery_note TEXT,

    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_by    UUID,
    deleted_at    TIMESTAMPTZ,
    version       BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT shipments_status_chk CHECK (status IN (
        'PENDING', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED', 'FAILED', 'RETURNED'
    )),
    CONSTRAINT shipments_delivery_order_chk CHECK (
        delivered_at IS NULL OR shipped_at IS NULL OR delivered_at >= shipped_at
    )
);

CREATE INDEX shipments_order_idx    ON shipments (order_id) WHERE deleted_at IS NULL;
CREATE INDEX shipments_tracking_idx ON shipments (tracking_no) WHERE tracking_no IS NOT NULL;

-- Partial shipments: an order can ship in pieces, so quantities are per line.
CREATE TABLE shipment_items (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shipment_id   UUID        NOT NULL REFERENCES shipments (id)   ON DELETE CASCADE,
    order_item_id UUID        NOT NULL REFERENCES order_items (id) ON DELETE RESTRICT,
    qty           INT         NOT NULL,

    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_by    UUID,
    version       BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT shipment_items_qty_chk CHECK (qty > 0),
    CONSTRAINT shipment_items_uq      UNIQUE (shipment_id, order_item_id)
);

CREATE INDEX shipment_items_order_item_idx ON shipment_items (order_item_id);

-- -----------------------------------------------------------------------------
-- updated_at triggers
-- -----------------------------------------------------------------------------
CREATE TRIGGER addresses_set_updated_at BEFORE UPDATE ON addresses
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER carts_set_updated_at BEFORE UPDATE ON carts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER cart_items_set_updated_at BEFORE UPDATE ON cart_items
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER wishlists_set_updated_at BEFORE UPDATE ON wishlists
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER wishlist_items_set_updated_at BEFORE UPDATE ON wishlist_items
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER orders_set_updated_at BEFORE UPDATE ON orders
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER order_items_set_updated_at BEFORE UPDATE ON order_items
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER shipments_set_updated_at BEFORE UPDATE ON shipments
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER shipment_items_set_updated_at BEFORE UPDATE ON shipment_items
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
