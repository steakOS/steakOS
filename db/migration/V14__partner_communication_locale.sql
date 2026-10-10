-- OI-I18N02: partner communicationLocale + documentLocale snapshot on issue
ALTER TABLE customer ADD COLUMN IF NOT EXISTS communication_locale VARCHAR(16);
ALTER TABLE vendor ADD COLUMN IF NOT EXISTS communication_locale VARCHAR(16);

ALTER TABLE customer_quote ADD COLUMN IF NOT EXISTS document_locale VARCHAR(16);
ALTER TABLE sales_order ADD COLUMN IF NOT EXISTS document_locale VARCHAR(16);
ALTER TABLE purchase_order ADD COLUMN IF NOT EXISTS document_locale VARCHAR(16);
ALTER TABLE invoice ADD COLUMN IF NOT EXISTS document_locale VARCHAR(16);
