-- =============================================================================
-- V10 — Tax invoices.
--
-- The order already freezes the invoice data at checkout (line snapshots, the
-- CGST/SGST/IGST split, HSN codes, ship-to). This table adds only what an invoice
-- needs beyond that: a stable, gapless invoice number and an issue date, assigned
-- once. The PDF is rendered on demand from the order + this record — never stored
-- as the source of truth — and a copy is cached in object storage for the record.
-- =============================================================================

CREATE TABLE invoices (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id       UUID        NOT NULL UNIQUE REFERENCES orders (id) ON DELETE RESTRICT,
    invoice_number TEXT        NOT NULL UNIQUE,
    issued_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- A cached rendering in object storage (audit/record). The authoritative serve
    -- path regenerates from the order, so this can be regenerated if a template changes.
    pdf_key        TEXT,
    pdf_url        TEXT,

    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by     UUID,
    updated_by     UUID,
    deleted_at     TIMESTAMPTZ,
    version        BIGINT      NOT NULL DEFAULT 0
);

CREATE TRIGGER invoices_set_updated_at BEFORE UPDATE ON invoices
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
