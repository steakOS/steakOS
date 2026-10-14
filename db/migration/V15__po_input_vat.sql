-- OI-F23 / L14: PO input VAT (net/tax/gross) for AP honesty
-- GORM dbCreate:update also adds these columns in local dev.

ALTER TABLE purchase_order
    ADD COLUMN IF NOT EXISTS net_amount_eur numeric(19, 8),
    ADD COLUMN IF NOT EXISTS tax_amount_eur numeric(19, 8);

ALTER TABLE purchase_order_line
    ADD COLUMN IF NOT EXISTS tax_code varchar(32),
    ADD COLUMN IF NOT EXISTS tax_rate_percent numeric(19, 4),
    ADD COLUMN IF NOT EXISTS tax_amount_eur numeric(19, 8);
