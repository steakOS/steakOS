-- A7 — ProcessStep hover tip fields (learning vehicle on SYSTEM charts)

ALTER TABLE process_step ADD COLUMN IF NOT EXISTS tip_what TEXT;
ALTER TABLE process_step ADD COLUMN IF NOT EXISTS tip_who VARCHAR(128);
ALTER TABLE process_step ADD COLUMN IF NOT EXISTS tip_where VARCHAR(255);
ALTER TABLE process_step ADD COLUMN IF NOT EXISTS tip_pitfall TEXT;
ALTER TABLE process_step ADD COLUMN IF NOT EXISTS stub BOOLEAN DEFAULT FALSE;
ALTER TABLE process_step ADD COLUMN IF NOT EXISTS help_key VARCHAR(128);
