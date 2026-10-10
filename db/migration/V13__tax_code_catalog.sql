-- OI-F23 D1–D2: system TaxCode catalog + company in-use join
-- GORM dbCreate:update also creates these tables in local dev.

CREATE TABLE IF NOT EXISTS tax_code (
    id                bigserial PRIMARY KEY,
    version           bigint NOT NULL DEFAULT 0,
    code              varchar(32) NOT NULL,
    jurisdiction      varchar(16) NOT NULL,
    name              varchar(255) NOT NULL,
    description       text,
    rate_percent      numeric(19, 4) NOT NULL DEFAULT 0,
    tax_kind          varchar(32) NOT NULL,
    reverse_charge    boolean NOT NULL DEFAULT false,
    intra_community   boolean NOT NULL DEFAULT false,
    enabled           boolean DEFAULT true,
    CONSTRAINT tax_code_code_uidx UNIQUE (code)
);

CREATE INDEX IF NOT EXISTS tax_code_code_idx ON tax_code (code);
CREATE INDEX IF NOT EXISTS tax_code_jur_idx ON tax_code (jurisdiction);

CREATE TABLE IF NOT EXISTS company_tax_code (
    id              bigserial PRIMARY KEY,
    version         bigint NOT NULL DEFAULT 0,
    npub_owner      varchar(255) NOT NULL,
    deputy_npub     varchar(255),
    tax_code        varchar(32) NOT NULL,
    in_use          boolean NOT NULL DEFAULT true,
    date_created    timestamp,
    last_updated    timestamp,
    CONSTRAINT company_tax_code_npub_code_uidx UNIQUE (npub_owner, tax_code)
);

CREATE INDEX IF NOT EXISTS company_tax_code_npub_idx ON company_tax_code (npub_owner);
CREATE INDEX IF NOT EXISTS company_tax_code_code_idx ON company_tax_code (tax_code);
