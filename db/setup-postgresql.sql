-- steakOS PostgreSQL setup
-- Run as superuser, e.g.:
--   sudo -u postgres psql -v ON_ERROR_STOP=1 -f db/setup-postgresql.sql
--   then grant schema on each DB (see comments at bottom)

DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'steakos') THEN
    CREATE ROLE steakos LOGIN PASSWORD 'steakos';
  ELSE
    ALTER ROLE steakos WITH LOGIN PASSWORD 'steakos';
  END IF;
END
$$;

SELECT 'CREATE DATABASE steakos_dev OWNER steakos'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'steakos_dev')\gexec

SELECT 'CREATE DATABASE steakos_test OWNER steakos'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'steakos_test')\gexec

SELECT 'CREATE DATABASE steakos_prod OWNER steakos'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'steakos_prod')\gexec

GRANT ALL PRIVILEGES ON DATABASE steakos_dev TO steakos;
GRANT ALL PRIVILEGES ON DATABASE steakos_test TO steakos;
GRANT ALL PRIVILEGES ON DATABASE steakos_prod TO steakos;

-- Per-database schema grants (run after create):
--   sudo -u postgres psql -d steakos_dev  -c "GRANT ALL ON SCHEMA public TO steakos; ALTER SCHEMA public OWNER TO steakos;"
--   sudo -u postgres psql -d steakos_test -c "GRANT ALL ON SCHEMA public TO steakos; ALTER SCHEMA public OWNER TO steakos;"
--   sudo -u postgres psql -d steakos_prod -c "GRANT ALL ON SCHEMA public TO steakos; ALTER SCHEMA public OWNER TO steakos;"
