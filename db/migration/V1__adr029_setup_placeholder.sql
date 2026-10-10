-- Flyway foundation (OI-T03): ADR-029 + Setup tables.
-- Wave A still uses GORM dbCreate:update for local/dev; this file documents
-- the production migration path and can be baselined when Flyway is switched on.
--
-- Tables expected (created by GORM until Flyway is primary):
--   legal_entity, site, bank_account, company_setting, user_preference, setup_state
--
-- Enable later via spring.flyway + dbCreate: none. Do not run blindly on an
-- already-populated golden DB without flyway baseline.

SELECT 1;
