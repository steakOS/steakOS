-- CREATE-CONTENT-RAIL D1: drop Attachable.url product data (companion LINK replaces UX).
-- Column may remain for trait compile compatibility; values cleared.

-- Clear url on common host tables that implement Attachable (safe if column missing)
DO $$
DECLARE
  r RECORD;
BEGIN
  FOR r IN
    SELECT table_name
    FROM information_schema.columns
    WHERE table_schema = 'public' AND column_name = 'url'
  LOOP
    EXECUTE format('UPDATE %I SET url = NULL WHERE url IS NOT NULL', r.table_name);
  END LOOP;
END $$;
