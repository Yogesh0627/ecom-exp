-- =============================================================================
-- V9 — Organic certification documents (PRD §5, differentiator).
--
-- A product can carry one or more certificates proving its organic/quality claims
-- (NPOP / India Organic, Jaivik Bharat, USDA/EU Organic, FSSAI, lab reports). Each
-- points at an uploaded document in object storage and can be marked verified by
-- staff — so "100% organic" is backed by a viewable certificate, not just a flag.
-- =============================================================================

CREATE TABLE product_certifications (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id         UUID        NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    cert_type          TEXT        NOT NULL,
    issuing_body       TEXT,
    certificate_number TEXT,
    document_url       TEXT        NOT NULL,
    valid_from         DATE,
    valid_until        DATE,
    -- Staff-verified: the document was checked, not just uploaded. Drives the
    -- "Verified" badge on the storefront.
    verified           BOOLEAN     NOT NULL DEFAULT FALSE,
    verified_at        TIMESTAMPTZ,
    verified_by        UUID        REFERENCES users (id) ON DELETE SET NULL,
    notes              TEXT,

    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    deleted_at         TIMESTAMPTZ,
    version            BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT product_certifications_type_chk CHECK (cert_type IN (
        'NPOP_INDIA_ORGANIC', 'JAIVIK_BHARAT', 'USDA_ORGANIC', 'EU_ORGANIC',
        'FSSAI', 'LAB_REPORT', 'OTHER')),
    CONSTRAINT product_certifications_validity_chk
        CHECK (valid_until IS NULL OR valid_from IS NULL OR valid_until >= valid_from)
);

CREATE INDEX product_certifications_product_idx
    ON product_certifications (product_id) WHERE deleted_at IS NULL;

CREATE TRIGGER product_certifications_set_updated_at BEFORE UPDATE ON product_certifications
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
