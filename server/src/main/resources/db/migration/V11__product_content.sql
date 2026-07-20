-- V11: AI-assisted rich product content (advantages, health benefits, why-choose, etc.)
--
-- One row per product. Content is AI-DRAFTED and human-APPROVED: a draft is never shown to
-- shoppers, only a PUBLISHED row is. This keeps the AI a drafting assistant for staff, not an
-- autonomous publisher — the guard against a plausible-but-wrong health claim reaching a customer.
--
-- Health/nutrition claims are legally sensitive in India (FSSAI/ASCI). The generator is prompted
-- to avoid disease-cure/prevention wording; the "disease prevention" ask is stored under the
-- compliance-safe name nutrient_support ("nutrients that support…"), and the UI shows a
-- "general nutritional information, not medical advice" disclaimer.

CREATE TABLE product_content (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id      UUID NOT NULL REFERENCES products (id) ON DELETE CASCADE,

    -- Prose sections (markdown-ish; the storefront renders bullet lines). All nullable: a draft may
    -- fill only some, and an admin may clear a section they don't want shown.
    overview         TEXT,   -- a richer description than products.description
    advantages       TEXT,   -- why this product / what's good about it
    health_benefits  TEXT,   -- nutrition-backed benefits, cautiously framed
    nutrient_support TEXT,   -- "nutrients that support…" (the compliance-safe framing)
    why_choose       TEXT,   -- why choose organic / this over alternatives
    storage_tips     TEXT,   -- keeping it fresh

    status          TEXT NOT NULL DEFAULT 'DRAFT',
    generated_by_ai BOOLEAN NOT NULL DEFAULT FALSE,
    ai_model        TEXT,
    generated_at    TIMESTAMPTZ,
    published_at    TIMESTAMPTZ,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_by      UUID,
    deleted_at      TIMESTAMPTZ,
    version         BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT product_content_status_chk CHECK (status IN ('DRAFT', 'PUBLISHED'))
);

-- One live content row per product.
CREATE UNIQUE INDEX product_content_product_uq
    ON product_content (product_id) WHERE deleted_at IS NULL;

CREATE TRIGGER product_content_set_updated_at BEFORE UPDATE ON product_content
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Two new AI features so per-feature cost on the AI-spend dashboard stays accurate:
--   PRODUCT_CONTENT   — generating the rich content above
--   RECIPE_COMPLETION — "turn my cart into a meal" suggestions
ALTER TABLE ai_request_logs DROP CONSTRAINT ai_log_feature_chk;
ALTER TABLE ai_request_logs ADD CONSTRAINT ai_log_feature_chk CHECK (feature IN (
    'FRIDGE_SCAN', 'MEAL_PLAN', 'RECIPE_GEN', 'NUTRITION_ESTIMATE',
    'RECOMMENDATION', 'SEARCH_ENHANCE', 'PRODUCT_CONTENT', 'RECIPE_COMPLETION'
));
