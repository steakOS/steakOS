-- steakOS PostgreSQL setup
-- Run as superuser. The installer passes the role password as a psql variable.
--   psql -v ON_ERROR_STOP=1 -v dbpass='<secret>' -d postgres -f db/setup-postgresql.sql
-- Do not put a password in this file.

\if :{?dbpass}
\else
\echo 'steakOS: psql variable dbpass is required. Refusing to create the role without it.'
\quit 1
\endif

SELECT format('CREATE ROLE steakos LOGIN PASSWORD %L', :'dbpass')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'steakos')\gexec

SELECT format('ALTER ROLE steakos WITH LOGIN PASSWORD %L', :'dbpass')\gexec

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
