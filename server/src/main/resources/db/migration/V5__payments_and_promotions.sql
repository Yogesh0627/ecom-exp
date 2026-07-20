-- =============================================================================
-- V5 — Payments & Promotions
-- Gateway: Razorpay (India, UPI-first).
--
-- Two rules govern this module:
--   1. Nothing here soft-deletes. Payment history is a legal record; corrections
--      are compensating rows (a refund), never a delete.
--   2. Gateway ids are UNIQUE. Razorpay retries webhooks, and a retried
--      payment.captured must not credit an order twice. The constraint makes
--      double-processing impossible at the database rather than hoping the
--      application remembers to check.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- payments  (no soft delete, no version — append/transition only)
-- -----------------------------------------------------------------------------
CREATE TABLE payments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id            UUID          NOT NULL REFERENCES orders (id) ON DELETE RESTRICT,
    gateway             TEXT          NOT NULL DEFAULT 'RAZORPAY',

    -- Razorpay's order handle (order_xxx), created before the customer pays.
    gateway_order_id    TEXT,
    -- Razorpay's payment handle (pay_xxx), set once a payment attempt exists.
    -- UNIQUE: this is the idempotency key for webhook processing.
    gateway_payment_id  TEXT,
    gateway_signature   TEXT,

    amount              NUMERIC(12,2) NOT NULL,
    currency            CHAR(3)       NOT NULL DEFAULT 'INR',
    status              TEXT          NOT NULL DEFAULT 'CREATED',
    method              TEXT,
    -- Amount refunded so far. A refund can never exceed this.
    amount_refunded     NUMERIC(12,2) NOT NULL DEFAULT 0,

    failure_code        TEXT,
    failure_reason      TEXT,
    captured_at         TIMESTAMPTZ,

    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by          UUID,
    updated_by          UUID,

    CONSTRAINT payments_status_chk CHECK (status IN (
        'CREATED', 'AUTHORIZED', 'CAPTURED', 'FAILED', 'REFUNDED', 'PARTIALLY_REFUNDED'
    )),
    CONSTRAINT payments_method_chk CHECK (method IS NULL OR method IN (
        'UPI', 'CARD', 'NETBANKING', 'WALLET', 'EMI', 'COD'
    )),
    CONSTRAINT payments_amount_chk   CHECK (amount > 0),
    CONSTRAINT payments_refund_chk   CHECK (amount_refunded >= 0 AND amount_refunded <= amount),
    -- A captured payment must record when. Anything else is an inconsistent ledger.
    CONSTRAINT payments_captured_chk CHECK (
        (status IN ('CAPTURED', 'REFUNDED', 'PARTIALLY_REFUNDED')) = (captured_at IS NOT NULL)
    )
);

-- The idempotency guarantee. A retried webhook hits this and fails instead of
-- crediting the order a second time.
CREATE UNIQUE INDEX payments_gateway_payment_uq
    ON payments (gateway_payment_id) WHERE gateway_payment_id IS NOT NULL;
CREATE UNIQUE INDEX payments_gateway_order_uq
    ON payments (gateway_order_id) WHERE gateway_order_id IS NOT NULL;
CREATE INDEX payments_order_idx  ON payments (order_id);
CREATE INDEX payments_status_idx ON payments (status);

-- -----------------------------------------------------------------------------
-- payment_refunds  (no soft delete)
-- -----------------------------------------------------------------------------
CREATE TABLE payment_refunds (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id         UUID          NOT NULL REFERENCES payments (id) ON DELETE RESTRICT,
    gateway_refund_id  TEXT,
    amount             NUMERIC(12,2) NOT NULL,
    currency           CHAR(3)       NOT NULL DEFAULT 'INR',
    reason             TEXT          NOT NULL,
    status             TEXT          NOT NULL DEFAULT 'PENDING',
    notes              TEXT,
    initiated_by       UUID REFERENCES users (id) ON DELETE SET NULL,
    processed_at       TIMESTAMPTZ,

    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,

    CONSTRAINT refunds_status_chk CHECK (status IN ('PENDING', 'PROCESSED', 'FAILED')),
    CONSTRAINT refunds_reason_chk CHECK (reason IN (
        'CUSTOMER_REQUEST', 'OUT_OF_STOCK', 'DAMAGED', 'LATE_DELIVERY',
        'ORDER_CANCELLED', 'QUALITY_ISSUE', 'OTHER'
    )),
    CONSTRAINT refunds_amount_chk CHECK (amount > 0)
);

CREATE UNIQUE INDEX refunds_gateway_refund_uq
    ON payment_refunds (gateway_refund_id) WHERE gateway_refund_id IS NOT NULL;
CREATE INDEX refunds_payment_idx ON payment_refunds (payment_id);

-- -----------------------------------------------------------------------------
-- payment_webhook_events
-- Raw gateway callbacks, stored before processing. Two jobs:
--   - idempotency: gateway_event_id UNIQUE means a replayed event is rejected
--   - forensics: when a payment dispute lands, the raw payload is the evidence
-- -----------------------------------------------------------------------------
CREATE TABLE payment_webhook_events (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    gateway           TEXT        NOT NULL DEFAULT 'RAZORPAY',
    gateway_event_id  TEXT        NOT NULL,
    event_type        TEXT        NOT NULL,
    payload           JSONB       NOT NULL,
    signature         TEXT,
    signature_valid   BOOLEAN,
    processed_at      TIMESTAMPTZ,
    processing_error  TEXT,
    received_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT webhook_event_uq UNIQUE (gateway, gateway_event_id)
);

CREATE INDEX webhook_unprocessed_idx ON payment_webhook_events (received_at)
    WHERE processed_at IS NULL;
CREATE INDEX webhook_type_idx ON payment_webhook_events (event_type, received_at DESC);

CREATE TRIGGER payment_webhook_events_append_only
    BEFORE DELETE ON payment_webhook_events
    FOR EACH ROW EXECUTE FUNCTION reject_mutation();

-- -----------------------------------------------------------------------------
-- coupons
-- -----------------------------------------------------------------------------
CREATE TABLE coupons (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code               TEXT          NOT NULL,
    description        TEXT,
    type               TEXT          NOT NULL,
    value              NUMERIC(12,2) NOT NULL,
    -- Caps a PERCENT coupon in absolute terms: "20% off, up to Rs.200".
    max_discount       NUMERIC(12,2),
    min_cart_value     NUMERIC(12,2) NOT NULL DEFAULT 0,
    valid_from         TIMESTAMPTZ   NOT NULL,
    valid_until        TIMESTAMPTZ   NOT NULL,
    max_uses           INT,
    max_uses_per_user  INT           NOT NULL DEFAULT 1,
    -- Cached counter for fast checks; coupon_redemptions is the truth.
    times_used         INT           NOT NULL DEFAULT 0,
    is_active          BOOLEAN       NOT NULL DEFAULT TRUE,
    first_order_only   BOOLEAN       NOT NULL DEFAULT FALSE,

    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    deleted_at         TIMESTAMPTZ,
    version            BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT coupons_type_chk     CHECK (type IN ('PERCENT', 'FLAT', 'FREE_SHIPPING')),
    CONSTRAINT coupons_value_chk    CHECK (value >= 0),
    -- A "150% off" coupon pays the customer to shop. Guard the type invariant here.
    CONSTRAINT coupons_percent_chk  CHECK (type <> 'PERCENT' OR value <= 100),
    CONSTRAINT coupons_validity_chk CHECK (valid_until > valid_from),
    CONSTRAINT coupons_uses_chk     CHECK (max_uses IS NULL OR max_uses > 0),
    CONSTRAINT coupons_per_user_chk CHECK (max_uses_per_user > 0),
    CONSTRAINT coupons_min_cart_chk CHECK (min_cart_value >= 0)
);

-- Codes are case-insensitive in practice ("SAVE20" == "save20").
CREATE UNIQUE INDEX coupons_code_uq ON coupons (upper(code)) WHERE deleted_at IS NULL;
CREATE INDEX coupons_active_idx ON coupons (valid_from, valid_until)
    WHERE is_active AND deleted_at IS NULL;

-- -----------------------------------------------------------------------------
-- coupon_conditions
-- Scope rules as data ("category = Dairy", "brand = X"), so ops can build a
-- campaign without a deploy.
-- -----------------------------------------------------------------------------
CREATE TABLE coupon_conditions (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    coupon_id      UUID        NOT NULL REFERENCES coupons (id) ON DELETE CASCADE,
    condition_type TEXT        NOT NULL,
    operator       TEXT        NOT NULL DEFAULT 'IN',
    operand        JSONB       NOT NULL,

    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by     UUID,
    updated_by     UUID,
    version        BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT coupon_cond_type_chk CHECK (condition_type IN (
        'CATEGORY', 'BRAND', 'PRODUCT', 'VARIANT', 'USER_SEGMENT', 'PINCODE'
    )),
    CONSTRAINT coupon_cond_op_chk CHECK (operator IN ('IN', 'NOT_IN'))
);

CREATE INDEX coupon_conditions_coupon_idx ON coupon_conditions (coupon_id);

-- -----------------------------------------------------------------------------
-- coupon_redemptions  (no soft delete — this is the ledger for coupon usage)
-- -----------------------------------------------------------------------------
CREATE TABLE coupon_redemptions (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    coupon_id        UUID          NOT NULL REFERENCES coupons (id) ON DELETE RESTRICT,
    user_id          UUID          NOT NULL REFERENCES users (id)   ON DELETE RESTRICT,
    order_id         UUID          NOT NULL REFERENCES orders (id)  ON DELETE RESTRICT,
    discount_applied NUMERIC(12,2) NOT NULL,
    redeemed_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),

    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by       UUID,

    CONSTRAINT redemption_amount_chk CHECK (discount_applied >= 0),
    -- One coupon can only be applied once per order.
    CONSTRAINT redemption_coupon_order_uq UNIQUE (coupon_id, order_id)
);

CREATE INDEX redemptions_coupon_idx ON coupon_redemptions (coupon_id);
-- Enforces max_uses_per_user: count rows for (coupon_id, user_id) before allowing another.
CREATE INDEX redemptions_coupon_user_idx ON coupon_redemptions (coupon_id, user_id);
CREATE INDEX redemptions_order_idx ON coupon_redemptions (order_id);

CREATE TRIGGER coupon_redemptions_append_only
    BEFORE UPDATE OR DELETE ON coupon_redemptions
    FOR EACH ROW EXECUTE FUNCTION reject_mutation();

-- -----------------------------------------------------------------------------
-- Payment reconciliation view
-- An order's captured payments minus refunds should equal what we actually hold.
-- A row here where net_captured <> grand_total on a PAID order is a money bug.
-- -----------------------------------------------------------------------------
CREATE VIEW order_payment_position AS
SELECT
    o.id AS order_id,
    o.order_number,
    o.status AS order_status,
    o.grand_total,
    coalesce(sum(p.amount) FILTER (WHERE p.status IN ('CAPTURED', 'PARTIALLY_REFUNDED')), 0) AS captured,
    coalesce(sum(p.amount_refunded), 0) AS refunded,
    coalesce(sum(p.amount) FILTER (WHERE p.status IN ('CAPTURED', 'PARTIALLY_REFUNDED')), 0)
        - coalesce(sum(p.amount_refunded), 0) AS net_captured
FROM orders o
LEFT JOIN payments p ON p.order_id = o.id
WHERE o.deleted_at IS NULL
GROUP BY o.id, o.order_number, o.status, o.grand_total;

-- -----------------------------------------------------------------------------
-- updated_at triggers
-- -----------------------------------------------------------------------------
CREATE TRIGGER payments_set_updated_at BEFORE UPDATE ON payments
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER payment_refunds_set_updated_at BEFORE UPDATE ON payment_refunds
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER coupons_set_updated_at BEFORE UPDATE ON coupons
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER coupon_conditions_set_updated_at BEFORE UPDATE ON coupon_conditions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
