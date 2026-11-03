-- OI-F30 / PDR-027 PR7: ACCRUAL document journal ids (invoice.issue / payable.create)
-- GORM dbCreate:update also adds these columns in local dev.

ALTER TABLE invoice
    ADD COLUMN IF NOT EXISTS issue_journal_entry_id bigint;

ALTER TABLE payable
    ADD COLUMN IF NOT EXISTS create_journal_entry_id bigint;
