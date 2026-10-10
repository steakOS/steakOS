-- Task unification (OI-WK01): extend task as single work item; drop activity.
-- Wave A may still use GORM dbCreate:update; this is the production path (ADR-019 interim).

-- New Task work-item columns (idempotent-friendly: run once on baseline)
ALTER TABLE task ADD COLUMN IF NOT EXISTS assignee_human_npub VARCHAR(128);
ALTER TABLE task ADD COLUMN IF NOT EXISTS assignee_agent_id BIGINT;
ALTER TABLE task ADD COLUMN IF NOT EXISTS start_at TIMESTAMP;
ALTER TABLE task ADD COLUMN IF NOT EXISTS end_at TIMESTAMP;
ALTER TABLE task ADD COLUMN IF NOT EXISTS deadline_at TIMESTAMP;
ALTER TABLE task ADD COLUMN IF NOT EXISTS urgency INTEGER;
ALTER TABLE task ADD COLUMN IF NOT EXISTS importance INTEGER;

-- Defaults for existing rows
UPDATE task SET urgency = 2 WHERE urgency IS NULL;
UPDATE task SET importance = 2 WHERE importance IS NULL;
UPDATE task SET status = 'OPEN' WHERE status IS NULL OR status = '';

ALTER TABLE task ALTER COLUMN urgency SET DEFAULT 2;
ALTER TABLE task ALTER COLUMN importance SET DEFAULT 2;

CREATE INDEX IF NOT EXISTS task_deadline_idx ON task (deadline_at);
CREATE INDEX IF NOT EXISTS task_assignee_human_idx ON task (assignee_human_npub);
CREATE INDEX IF NOT EXISTS task_assignee_agent_idx ON task (assignee_agent_id);

-- Migrate Activity rows into Task when present (name/description/npub only)
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = 'public' AND table_name = 'activity'
  ) THEN
    INSERT INTO task (version, name, description, npub_owner, status, urgency, importance)
    SELECT
      COALESCE(a.version, 0),
      a.name,
      a.description,
      a.npub_owner,
      COALESCE(NULLIF(a.status, ''), 'OPEN'),
      2,
      2
    FROM activity a
    WHERE NOT EXISTS (
      SELECT 1 FROM task t
      WHERE t.name = a.name
        AND COALESCE(t.npub_owner, '') = COALESCE(a.npub_owner, '')
    );

    DROP TABLE IF EXISTS activity CASCADE;
  END IF;
END $$;
