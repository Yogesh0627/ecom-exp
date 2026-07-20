-- =============================================================================
-- V6 — Engagement
-- PRD §4 (Reviews, Notifications), §6 (Banners, Settings).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- reviews
-- -----------------------------------------------------------------------------
CREATE TABLE reviews (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID        NOT NULL REFERENCES users (id)    ON DELETE CASCADE,
    product_id        UUID        NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    -- Links the review to the purchase that justifies it. Nullable only because
    -- an order line can be soft-deleted; verified_purchase is derived from it.
    order_item_id     UUID REFERENCES order_items (id) ON DELETE SET NULL,

    rating            SMALLINT    NOT NULL,
    title             TEXT,
    body              TEXT,
    status            TEXT        NOT NULL DEFAULT 'PENDING',
    -- Set by the server from a DELIVERED order line, never from client input.
    verified_purchase BOOLEAN     NOT NULL DEFAULT FALSE,
    helpful_count     INT         NOT NULL DEFAULT 0,
    moderated_by      UUID REFERENCES users (id) ON DELETE SET NULL,
    moderated_at      TIMESTAMPTZ,
    moderation_note   TEXT,

    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by        UUID,
    updated_by        UUID,
    deleted_at        TIMESTAMPTZ,
    version           BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT reviews_rating_chk CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT reviews_status_chk CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'FLAGGED')),
    CONSTRAINT reviews_helpful_chk CHECK (helpful_count >= 0),
    CONSTRAINT reviews_moderation_chk CHECK (
        (moderated_at IS NULL AND moderated_by IS NULL) OR
        (moderated_at IS NOT NULL AND moderated_by IS NOT NULL)
    )
);

-- One review per user per product. Without this, a single user can flood a
-- product's rating.
CREATE UNIQUE INDEX reviews_user_product_uq
    ON reviews (user_id, product_id) WHERE deleted_at IS NULL;
-- Serves the product page: approved reviews, newest first.
CREATE INDEX reviews_product_idx ON reviews (product_id, created_at DESC)
    WHERE deleted_at IS NULL AND status = 'APPROVED';
CREATE INDEX reviews_moderation_queue_idx ON reviews (created_at)
    WHERE deleted_at IS NULL AND status IN ('PENDING', 'FLAGGED');

CREATE TABLE review_images (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_id   UUID        NOT NULL REFERENCES reviews (id) ON DELETE CASCADE,
    url         TEXT        NOT NULL,
    position    INT         NOT NULL DEFAULT 0,

    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID,
    deleted_at  TIMESTAMPTZ,
    version     BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX review_images_review_idx ON review_images (review_id) WHERE deleted_at IS NULL;

-- Product rating rollup. A view, not a stored column: a cached average drifts every
-- time a review is edited or moderated, and a wrong star rating is a trust problem.
-- Promote to a materialised view if the product page ever needs it faster.
CREATE VIEW product_rating_summary AS
SELECT
    p.id AS product_id,
    count(r.id)                                   AS review_count,
    round(avg(r.rating)::numeric, 2)              AS avg_rating,
    count(r.id) FILTER (WHERE r.rating = 5)       AS five_star,
    count(r.id) FILTER (WHERE r.rating = 4)       AS four_star,
    count(r.id) FILTER (WHERE r.rating = 3)       AS three_star,
    count(r.id) FILTER (WHERE r.rating = 2)       AS two_star,
    count(r.id) FILTER (WHERE r.rating = 1)       AS one_star,
    count(r.id) FILTER (WHERE r.verified_purchase) AS verified_count
FROM products p
LEFT JOIN reviews r
    ON r.product_id = p.id AND r.status = 'APPROVED' AND r.deleted_at IS NULL
WHERE p.deleted_at IS NULL
GROUP BY p.id;

-- -----------------------------------------------------------------------------
-- notifications
-- -----------------------------------------------------------------------------
CREATE TABLE notifications (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type        TEXT        NOT NULL,
    channel     TEXT        NOT NULL DEFAULT 'IN_APP',
    title       TEXT        NOT NULL,
    body        TEXT,
    -- Deep-link target and any type-specific data (order_id, variant_id, ...).
    payload     JSONB,
    read_at     TIMESTAMPTZ,
    sent_at     TIMESTAMPTZ,
    failed_at   TIMESTAMPTZ,
    error       TEXT,

    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID,
    deleted_at  TIMESTAMPTZ,
    version     BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT notifications_type_chk CHECK (type IN (
        'ORDER_PLACED', 'ORDER_SHIPPED', 'ORDER_DELIVERED', 'ORDER_CANCELLED',
        'PAYMENT_FAILED', 'REFUND_PROCESSED', 'PANTRY_EXPIRY', 'BACK_IN_STOCK',
        'PRICE_DROP', 'MEAL_PLAN_READY', 'PROMO', 'SYSTEM'
    )),
    CONSTRAINT notifications_channel_chk CHECK (channel IN ('IN_APP', 'EMAIL', 'SMS', 'PUSH', 'WHATSAPP'))
);

-- Serves the notification bell: unread, newest first.
CREATE INDEX notifications_user_unread_idx ON notifications (user_id, created_at DESC)
    WHERE deleted_at IS NULL AND read_at IS NULL;
CREATE INDEX notifications_user_idx ON notifications (user_id, created_at DESC)
    WHERE deleted_at IS NULL;
-- Outbox: rows waiting to be delivered on a non-IN_APP channel.
CREATE INDEX notifications_pending_idx ON notifications (created_at)
    WHERE deleted_at IS NULL AND sent_at IS NULL AND channel <> 'IN_APP';

-- -----------------------------------------------------------------------------
-- banners  (PRD §6 — Banner Management)
-- -----------------------------------------------------------------------------
CREATE TABLE banners (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title         TEXT        NOT NULL,
    subtitle      TEXT,
    image_url     TEXT        NOT NULL,
    mobile_image_url TEXT,
    link_url      TEXT,
    placement     TEXT        NOT NULL DEFAULT 'HOME_HERO',
    position      INT         NOT NULL DEFAULT 0,
    is_active     BOOLEAN     NOT NULL DEFAULT TRUE,
    active_from   TIMESTAMPTZ,
    active_until  TIMESTAMPTZ,

    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_by    UUID,
    deleted_at    TIMESTAMPTZ,
    version       BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT banners_placement_chk CHECK (placement IN (
        'HOME_HERO', 'HOME_STRIP', 'CATEGORY_TOP', 'CART_UPSELL', 'CHECKOUT'
    )),
    CONSTRAINT banners_window_chk CHECK (
        active_from IS NULL OR active_until IS NULL OR active_until > active_from
    )
);

CREATE INDEX banners_active_idx ON banners (placement, position)
    WHERE deleted_at IS NULL AND is_active;

-- -----------------------------------------------------------------------------
-- settings  (PRD §6 — Settings)
-- Runtime configuration that ops changes without a deploy: delivery fee, free-
-- shipping threshold, support contacts. Values are JSONB so a setting can be a
-- scalar or a structure without a schema change.
-- -----------------------------------------------------------------------------
CREATE TABLE settings (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key         TEXT        NOT NULL,
    value       JSONB       NOT NULL,
    description TEXT,
    -- Public settings are safe to expose to the storefront; private ones are not.
    is_public   BOOLEAN     NOT NULL DEFAULT FALSE,

    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT settings_key_uq  UNIQUE (key),
    CONSTRAINT settings_key_chk CHECK (key ~ '^[a-z][a-z0-9_.]*$')
);

CREATE INDEX settings_public_idx ON settings (key) WHERE is_public;

-- -----------------------------------------------------------------------------
-- updated_at triggers
-- -----------------------------------------------------------------------------
CREATE TRIGGER reviews_set_updated_at BEFORE UPDATE ON reviews
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER review_images_set_updated_at BEFORE UPDATE ON review_images
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER notifications_set_updated_at BEFORE UPDATE ON notifications
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER banners_set_updated_at BEFORE UPDATE ON banners
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER settings_set_updated_at BEFORE UPDATE ON settings
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- -----------------------------------------------------------------------------
-- Seed: platform settings
-- -----------------------------------------------------------------------------
INSERT INTO settings (key, value, description, is_public) VALUES
    ('delivery.fee_inr',              '40',      'Flat delivery fee in INR', TRUE),
    ('delivery.free_above_inr',       '499',     'Cart value above which delivery is free', TRUE),
    ('delivery.min_order_inr',        '199',     'Minimum order value', TRUE),
    ('cart.abandon_after_hours',      '72',      'Mark an untouched cart abandoned after N hours', FALSE),
    ('cart.reservation_ttl_minutes',  '30',      'How long stock stays reserved for an unpaid cart', FALSE),
    ('order.cancel_window_minutes',   '60',      'Customer self-cancel window after placement', TRUE),
    ('review.auto_approve_verified',  'false',   'Auto-approve reviews from verified purchases', FALSE),
    ('ai.fridge_scan_retention_days', '30',      'Days before an uploaded fridge photo is purged (PII)', FALSE),
    ('ai.monthly_budget_inr',         '5000',    'Hard cap on Gemini spend per month', FALSE),
    ('support.email',                 '"support@ecoexpress.in"', 'Customer support email', TRUE),
    ('support.phone',                 '""',      'Customer support phone', TRUE)
ON CONFLICT (key) DO NOTHING;
