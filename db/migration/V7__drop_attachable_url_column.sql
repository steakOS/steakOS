-- Drop legacy Attachable.url columns. Links live on Object Companion (object_content.url).
-- Safe to re-run: IF EXISTS. Does NOT touch object_content.

DO $$
DECLARE
  r RECORD;
BEGIN
  FOR r IN
    SELECT table_name
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND column_name = 'url'
      AND table_name <> 'object_content'
  LOOP
    EXECUTE format('ALTER TABLE %I DROP COLUMN IF EXISTS url', r.table_name);
  END LOOP;
END $$;
