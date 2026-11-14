-- 0.2.17 security columns. Idempotent so a database that GORM already updated can migrate.

ALTER TABLE "user" ADD COLUMN IF NOT EXISTS last_totp_step bigint;

CREATE TABLE IF NOT EXISTS webhook_delivery (
    id bigserial PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    delivery_id varchar(200) NOT NULL UNIQUE
);

-- User secret keys are no longer stored. Export runs before this migration.
ALTER TABLE "user" DROP COLUMN IF EXISTS nsec;
