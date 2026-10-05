#!/bin/sh
set -eu

: "${POSTGRES_USER:?}"
: "${POSTGRES_DB:?}"
: "${DB_MIGRATION_PASSWORD:?}"
: "${DB_PASSWORD:?}"

if [ "$POSTGRES_USER" = "whatsappka_migrate" ] || [ "$POSTGRES_USER" = "whatsappka_app" ]; then
  echo "POSTGRES_USER не должен совпадать с ролью миграций или приложения" >&2
  exit 1
fi

psql -v ON_ERROR_STOP=1 \
  --username "$POSTGRES_USER" \
  --dbname "$POSTGRES_DB" \
  -v migrate_password="$DB_MIGRATION_PASSWORD" \
  -v app_password="$DB_PASSWORD" \
  -v dbname="$POSTGRES_DB" <<'SQL'
CREATE EXTENSION IF NOT EXISTS pg_trgm;

SELECT format('CREATE ROLE whatsappka_migrate LOGIN PASSWORD %L', :'migrate_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'whatsappka_migrate')
\gexec

SELECT format('CREATE ROLE whatsappka_app LOGIN PASSWORD %L', :'app_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'whatsappka_app')
\gexec

SELECT format('GRANT CONNECT, CREATE ON DATABASE %I TO whatsappka_migrate', :'dbname')
\gexec

SELECT format('GRANT CONNECT ON DATABASE %I TO whatsappka_app', :'dbname')
\gexec

GRANT USAGE, CREATE ON SCHEMA public TO whatsappka_migrate;
GRANT USAGE ON SCHEMA public TO whatsappka_app;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;

ALTER ROLE whatsappka_migrate SET search_path = public;
ALTER ROLE whatsappka_app SET search_path = public;

ALTER DEFAULT PRIVILEGES FOR ROLE whatsappka_migrate IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO whatsappka_app;
ALTER DEFAULT PRIVILEGES FOR ROLE whatsappka_migrate IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO whatsappka_app;
SQL
