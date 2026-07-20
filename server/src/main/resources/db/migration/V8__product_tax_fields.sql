-- =============================================================================
-- V8 — GST fields on products
--
-- V4 gave order_items a tax_rate_pct and hsn_code, but nothing in the catalog
-- supplied them: the rate has to come from the product being sold. Without this,
-- every order would compute zero tax and the invoices would be wrong.
--
-- Additive only. V1-V7 are already applied and the database holds real rows
-- (the bootstrap admin, the category seed), so this is a new migration rather
-- than an edit to an existing one.
-- =============================================================================

ALTER TABLE products
    -- Harmonised System of Nomenclature code. Mandatory on a GST invoice.
    -- Nullable: exempt fresh produce still needs a code on the invoice, but we
    -- do not have them all at launch and a wrong code is worse than a blank one.
    ADD COLUMN hsn_code TEXT,

    -- GST rate for this product, as a percentage.
    --
    -- DEFAULT 0 is deliberate and correct for this catalog: fresh, unbranded and
    -- unpackaged fruit, vegetables and cereals are NIL-rated under GST, which is
    -- most of what EcoExpress sells. Branded/packaged items attract 5% or more and
    -- must be set explicitly per product.
    --
    -- Stored per product, not per variant: the rate follows the goods, not the
    -- pack size.
    ADD COLUMN gst_rate_pct NUMERIC(5,2) NOT NULL DEFAULT 0;

-- Only the rates that actually exist under Indian GST. A typo like 1.8 instead of
-- 18 would silently under-charge tax on every sale and surface at audit.
ALTER TABLE products
    ADD CONSTRAINT products_gst_rate_chk
    CHECK (gst_rate_pct IN (0, 0.25, 3, 5, 12, 18, 28));

COMMENT ON COLUMN products.gst_rate_pct IS
    'GST %. 0 for nil-rated fresh produce; 5/12/18/28 for packaged goods. Drives order_items.tax_rate_pct at checkout.';
COMMENT ON COLUMN products.hsn_code IS
    'HSN code for the GST invoice.';
