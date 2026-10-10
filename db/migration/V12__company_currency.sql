-- OI-CUR01 D1: per-company currency in-use join (system Currencies stays shared)
-- GORM dbCreate:update also creates this table in local dev.

CREATE TABLE IF NOT EXISTS company_currency (
    id              bigserial PRIMARY KEY,
    version         bigint NOT NULL DEFAULT 0,
    npub_owner      varchar(255) NOT NULL,
    deputy_npub     varchar(255),
    currency_code   varchar(3) NOT NULL,
    in_use          boolean NOT NULL DEFAULT true,
    date_created    timestamp,
    last_updated    timestamp,
    CONSTRAINT company_currency_npub_code_uidx UNIQUE (npub_owner, currency_code)
);

CREATE INDEX IF NOT EXISTS company_currency_npub_idx ON company_currency (npub_owner);
CREATE INDEX IF NOT EXISTS company_currency_code_idx ON company_currency (currency_code);
