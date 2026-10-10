-- PDR-026 PR1: company kind/code on legal_entity + user memberships
ALTER TABLE legal_entity ADD COLUMN IF NOT EXISTS code varchar(64);
ALTER TABLE legal_entity ADD COLUMN IF NOT EXISTS kind varchar(16) NOT NULL DEFAULT 'LIVE';

CREATE UNIQUE INDEX IF NOT EXISTS legal_entity_code_uidx ON legal_entity (code) WHERE code IS NOT NULL;
CREATE INDEX IF NOT EXISTS legal_entity_kind_idx ON legal_entity (kind);

CREATE TABLE IF NOT EXISTS user_company_membership (
    id              bigserial PRIMARY KEY,
    version         bigint NOT NULL DEFAULT 0,
    user_id         bigint NOT NULL REFERENCES "user"(id),
    legal_entity_id bigint NOT NULL REFERENCES legal_entity(id),
    is_default      boolean NOT NULL DEFAULT false,
    date_created    timestamp,
    last_updated    timestamp,
    CONSTRAINT ucm_user_company_uidx UNIQUE (user_id, legal_entity_id)
);

CREATE INDEX IF NOT EXISTS ucm_user_idx ON user_company_membership (user_id);
CREATE INDEX IF NOT EXISTS ucm_company_idx ON user_company_membership (legal_entity_id);

UPDATE legal_entity SET kind = 'LIVE' WHERE kind IS NULL OR kind = '';
UPDATE legal_entity SET code = 'primary' WHERE code IS NULL AND kind = 'LIVE';
