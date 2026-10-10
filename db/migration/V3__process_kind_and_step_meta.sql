-- Process catalogue kinds + step metadata (PROCESS-CATALOG-VISUAL-CONCEPT D1/D5)

ALTER TABLE business_process ADD COLUMN IF NOT EXISTS process_kind VARCHAR(32);
UPDATE business_process SET process_kind = 'HUMAN' WHERE process_kind IS NULL OR process_kind = '';
ALTER TABLE business_process ALTER COLUMN process_kind SET DEFAULT 'HUMAN';

CREATE INDEX IF NOT EXISTS cap_process_kind_idx ON business_process (process_kind);
CREATE INDEX IF NOT EXISTS cap_process_code_idx ON business_process (process_code);

ALTER TABLE process_step ADD COLUMN IF NOT EXISTS step_code VARCHAR(64);
ALTER TABLE process_step ADD COLUMN IF NOT EXISTS desk_hint VARCHAR(255);
ALTER TABLE process_step ADD COLUMN IF NOT EXISTS outcome_status VARCHAR(64);
ALTER TABLE process_step ADD COLUMN IF NOT EXISTS actor_hint VARCHAR(32);

CREATE INDEX IF NOT EXISTS cap_process_step_code_idx ON process_step (step_code);
