-- V13: back-in-stock alerts.
--
-- A shopper viewing an out-of-stock variant can ask to be told when it returns. When inventory for
-- that variant crosses from 0 to positive (a stock receipt or a positive adjustment), every open
-- alert is fulfilled: an in-app + email notification is raised and the alert is marked notified.
--
-- notified_at doubles as the "still waiting" flag — a fulfilled alert keeps its row (for history)
-- but no longer counts as active, so a later restock does not spam the same person again unless
-- they re-subscribe.

CREATE TABLE stock_alerts (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    variant_id  UUID NOT NULL REFERENCES product_variants (id) ON DELETE CASCADE,

    -- Set when the back-in-stock notification is delivered; NULL means "still waiting".
    notified_at TIMESTAMPTZ,

    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID,
    deleted_at  TIMESTAMPTZ,
    version     BIGINT NOT NULL DEFAULT 0
);

-- At most one open alert per user+variant; re-subscribing after a fulfilled one is allowed because
-- the old row has notified_at set and so is excluded here.
CREATE UNIQUE INDEX stock_alerts_active_uq
    ON stock_alerts (user_id, variant_id)
    WHERE notified_at IS NULL AND deleted_at IS NULL;

-- The restock hook looks up open alerts by variant.
CREATE INDEX stock_alerts_variant_idx
    ON stock_alerts (variant_id)
    WHERE notified_at IS NULL AND deleted_at IS NULL;

CREATE TRIGGER stock_alerts_set_updated_at BEFORE UPDATE ON stock_alerts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
