-- =============================================================================
-- V2 — Catalog
-- PRD §7 (Product Model), §8 (Nutrition Model).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- categories  (self-referencing tree, depth <= 3)
-- -----------------------------------------------------------------------------
CREATE TABLE categories (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id   UUID REFERENCES categories (id) ON DELETE RESTRICT,
    name        TEXT        NOT NULL,
    slug        TEXT        NOT NULL,
    description TEXT,
    image_url   TEXT,
    position    INT         NOT NULL DEFAULT 0,
    is_active   BOOLEAN     NOT NULL DEFAULT TRUE,

    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID,
    deleted_at  TIMESTAMPTZ,
    version     BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT categories_slug_chk      CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    -- A category cannot be its own parent. Deeper cycles are prevented in code;
    -- this catches the trivial and most common case at the DB.
    CONSTRAINT categories_no_self_parent CHECK (parent_id IS NULL OR parent_id <> id)
);

CREATE UNIQUE INDEX categories_slug_uq ON categories (slug) WHERE deleted_at IS NULL;
CREATE INDEX categories_parent_idx ON categories (parent_id) WHERE deleted_at IS NULL;

-- -----------------------------------------------------------------------------
-- brands
-- -----------------------------------------------------------------------------
CREATE TABLE brands (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        TEXT        NOT NULL,
    slug        TEXT        NOT NULL,
    logo_url    TEXT,
    description TEXT,

    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID,
    deleted_at  TIMESTAMPTZ,
    version     BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT brands_slug_chk CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);

CREATE UNIQUE INDEX brands_slug_uq ON brands (slug) WHERE deleted_at IS NULL;

-- -----------------------------------------------------------------------------
-- products
-- -----------------------------------------------------------------------------
CREATE TABLE products (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name          TEXT        NOT NULL,
    slug          TEXT        NOT NULL,
    description   TEXT,
    brand_id      UUID REFERENCES brands (id)     ON DELETE SET NULL,
    category_id   UUID        NOT NULL REFERENCES categories (id) ON DELETE RESTRICT,
    origin        TEXT,
    is_organic    BOOLEAN     NOT NULL DEFAULT TRUE,
    status        TEXT        NOT NULL DEFAULT 'DRAFT',
    published_at  TIMESTAMPTZ,

    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_by    UUID,
    deleted_at    TIMESTAMPTZ,
    version       BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT products_slug_chk   CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT products_status_chk CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED', 'OUT_OF_STOCK'))
);

CREATE UNIQUE INDEX products_slug_uq ON products (slug) WHERE deleted_at IS NULL;
CREATE INDEX products_category_idx ON products (category_id) WHERE deleted_at IS NULL;
CREATE INDEX products_brand_idx    ON products (brand_id)    WHERE deleted_at IS NULL;
CREATE INDEX products_status_idx   ON products (status)      WHERE deleted_at IS NULL;

-- Full-text search over name + description (PRD §10 "Search").
-- GIN over a generated tsvector: the index stays correct without application help.
ALTER TABLE products ADD COLUMN search_vector tsvector
    GENERATED ALWAYS AS (
        setweight(to_tsvector('english', coalesce(name, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(description, '')), 'B')
    ) STORED;
CREATE INDEX products_search_idx ON products USING GIN (search_vector);

-- -----------------------------------------------------------------------------
-- product_variants
-- Price/SKU/barcode live here, not on products: "Organic Turmeric" is a product,
-- the 200g and 500g packs are variants.
-- -----------------------------------------------------------------------------
CREATE TABLE product_variants (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id    UUID          NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    sku           TEXT          NOT NULL,
    barcode       TEXT,
    name          TEXT          NOT NULL,
    weight_grams  NUMERIC(10,2) NOT NULL,
    -- mrp = printed maximum retail price; price = what we actually charge.
    mrp           NUMERIC(12,2) NOT NULL,
    price         NUMERIC(12,2) NOT NULL,
    currency      CHAR(3)       NOT NULL DEFAULT 'INR',
    is_default    BOOLEAN       NOT NULL DEFAULT FALSE,
    is_active     BOOLEAN       NOT NULL DEFAULT TRUE,

    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_by    UUID,
    deleted_at    TIMESTAMPTZ,
    version       BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT variants_price_positive CHECK (price >= 0 AND mrp >= 0),
    -- We may not charge above the printed MRP. This is a legal requirement in India,
    -- not a business preference.
    CONSTRAINT variants_price_lte_mrp  CHECK (price <= mrp),
    CONSTRAINT variants_weight_chk     CHECK (weight_grams > 0)
);

CREATE UNIQUE INDEX variants_sku_uq     ON product_variants (sku) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX variants_barcode_uq ON product_variants (barcode)
    WHERE deleted_at IS NULL AND barcode IS NOT NULL;
CREATE INDEX variants_product_idx ON product_variants (product_id) WHERE deleted_at IS NULL;

-- Exactly one default variant per product — the one shown on a listing card.
CREATE UNIQUE INDEX variants_one_default_per_product
    ON product_variants (product_id)
    WHERE is_default AND deleted_at IS NULL;

-- discount_pct is derived, never stored: a stored copy drifts the moment price or mrp
-- changes and then the storefront lies about the discount.
CREATE VIEW product_variant_pricing AS
SELECT
    v.id,
    v.product_id,
    v.sku,
    v.price,
    v.mrp,
    CASE WHEN v.mrp > 0 THEN round(((v.mrp - v.price) / v.mrp) * 100, 2) ELSE 0 END AS discount_pct
FROM product_variants v
WHERE v.deleted_at IS NULL;

-- -----------------------------------------------------------------------------
-- product_images
-- -----------------------------------------------------------------------------
CREATE TABLE product_images (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    variant_id  UUID        NOT NULL REFERENCES product_variants (id) ON DELETE CASCADE,
    url         TEXT        NOT NULL,
    alt         TEXT,
    position    INT         NOT NULL DEFAULT 0,
    is_primary  BOOLEAN     NOT NULL DEFAULT FALSE,

    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID,
    deleted_at  TIMESTAMPTZ,
    version     BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX product_images_variant_idx ON product_images (variant_id) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX product_images_one_primary
    ON product_images (variant_id)
    WHERE is_primary AND deleted_at IS NULL;

-- -----------------------------------------------------------------------------
-- nutrition_facts  (PRD §8 — the 12 nutrients, per 100g)
-- Every value is NULLABLE on purpose: NULL means "not measured", 0 means
-- "measured, contains none". A health score must not treat unknown iron as zero iron.
-- -----------------------------------------------------------------------------
CREATE TABLE nutrition_facts (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    variant_id        UUID          NOT NULL REFERENCES product_variants (id) ON DELETE CASCADE,
    basis_grams       NUMERIC(8,2)  NOT NULL DEFAULT 100,

    calories_kcal     NUMERIC(9,3),
    protein_g         NUMERIC(9,3),
    fat_g             NUMERIC(9,3),
    carbohydrates_g   NUMERIC(9,3),
    fiber_g           NUMERIC(9,3),
    sugar_g           NUMERIC(9,3),
    iron_mg           NUMERIC(9,3),
    vitamin_a_mcg     NUMERIC(9,3),
    vitamin_c_mg      NUMERIC(9,3),
    vitamin_d_mcg     NUMERIC(9,3),
    potassium_mg      NUMERIC(9,3),
    sodium_mg         NUMERIC(9,3),

    -- Provenance matters: IFCT vs USDA vs a label someone typed in have very
    -- different reliability, and we will be asked to justify a health score.
    source            TEXT          NOT NULL DEFAULT 'MANUAL',
    source_ref        TEXT,
    verified_at       TIMESTAMPTZ,

    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by        UUID,
    updated_by        UUID,
    deleted_at        TIMESTAMPTZ,
    version           BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT nutrition_source_chk CHECK (source IN ('IFCT', 'USDA', 'LABEL', 'MANUAL', 'AI_ESTIMATED')),
    CONSTRAINT nutrition_basis_chk  CHECK (basis_grams > 0),
    CONSTRAINT nutrition_nonneg_chk CHECK (
        coalesce(calories_kcal, 0)   >= 0 AND coalesce(protein_g, 0)     >= 0 AND
        coalesce(fat_g, 0)           >= 0 AND coalesce(carbohydrates_g,0)>= 0 AND
        coalesce(fiber_g, 0)         >= 0 AND coalesce(sugar_g, 0)       >= 0 AND
        coalesce(iron_mg, 0)         >= 0 AND coalesce(vitamin_a_mcg, 0) >= 0 AND
        coalesce(vitamin_c_mg, 0)    >= 0 AND coalesce(vitamin_d_mcg, 0) >= 0 AND
        coalesce(potassium_mg, 0)    >= 0 AND coalesce(sodium_mg, 0)     >= 0
    )
);

-- One nutrition row per variant.
CREATE UNIQUE INDEX nutrition_variant_uq ON nutrition_facts (variant_id) WHERE deleted_at IS NULL;

-- -----------------------------------------------------------------------------
-- tags
-- -----------------------------------------------------------------------------
CREATE TABLE tags (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        TEXT        NOT NULL,
    slug        TEXT        NOT NULL,

    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID,
    deleted_at  TIMESTAMPTZ,
    version     BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT tags_slug_chk CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);

CREATE UNIQUE INDEX tags_slug_uq ON tags (slug) WHERE deleted_at IS NULL;

CREATE TABLE product_tag_map (
    product_id UUID NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    tag_id     UUID NOT NULL REFERENCES tags (id)     ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (product_id, tag_id)
);

CREATE INDEX product_tag_map_tag_idx ON product_tag_map (tag_id);

-- -----------------------------------------------------------------------------
-- organic_certifications
-- NPOP / PGS-India / Jaivik Bharat. Selling "organic" in India without a valid
-- certificate is a regulatory problem, so expiry is tracked as data, not a note.
-- -----------------------------------------------------------------------------
CREATE TABLE organic_certifications (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id      UUID        NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    certifier       TEXT        NOT NULL,
    scheme          TEXT        NOT NULL DEFAULT 'NPOP',
    certificate_no  TEXT        NOT NULL,
    valid_from      DATE        NOT NULL,
    valid_until     DATE        NOT NULL,
    document_url    TEXT,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_by      UUID,
    deleted_at      TIMESTAMPTZ,
    version         BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT organic_scheme_chk CHECK (scheme IN ('NPOP', 'PGS_INDIA', 'JAIVIK_BHARAT', 'OTHER')),
    CONSTRAINT organic_validity_chk CHECK (valid_until > valid_from)
);

CREATE INDEX organic_cert_product_idx ON organic_certifications (product_id) WHERE deleted_at IS NULL;
-- Drives the "certificate expiring soon" admin alert.
CREATE INDEX organic_cert_expiry_idx  ON organic_certifications (valid_until) WHERE deleted_at IS NULL;

-- -----------------------------------------------------------------------------
-- updated_at triggers
-- -----------------------------------------------------------------------------
CREATE TRIGGER categories_set_updated_at BEFORE UPDATE ON categories
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER brands_set_updated_at BEFORE UPDATE ON brands
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER products_set_updated_at BEFORE UPDATE ON products
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER product_variants_set_updated_at BEFORE UPDATE ON product_variants
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER product_images_set_updated_at BEFORE UPDATE ON product_images
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER nutrition_facts_set_updated_at BEFORE UPDATE ON nutrition_facts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER tags_set_updated_at BEFORE UPDATE ON tags
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER organic_certifications_set_updated_at BEFORE UPDATE ON organic_certifications
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- -----------------------------------------------------------------------------
-- Seed: top-level categories (PRD §1 — the product areas we launch with)
-- -----------------------------------------------------------------------------
INSERT INTO categories (name, slug, position) VALUES
    ('Fruits',      'fruits',      1),
    ('Vegetables',  'vegetables',  2),
    ('Grains',      'grains',      3),
    ('Flours',      'flours',      4),
    ('Nuts & Seeds','nuts-seeds',  5),
    ('Dairy',       'dairy',       6),
    ('Healthy Foods','healthy-foods', 7)
ON CONFLICT DO NOTHING;
