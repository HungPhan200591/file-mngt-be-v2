#!/bin/sh
set -eu

create_database() {
  local database_name="$1"
  local user_name="$2"
  local password="$3"

  psql --username "$POSTGRES_USER" --dbname postgres \
    --set=database_name="$database_name" \
    --set=user_name="$user_name" \
    --set=password="$password" \
    --set=ON_ERROR_STOP=1 <<'SQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'user_name', :'password')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = :'user_name')
\gexec

SELECT format('CREATE DATABASE %I OWNER %I', :'database_name', :'user_name')
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = :'database_name')
\gexec
SQL
}

create_database "$CATALOG_DB" "$CATALOG_DB_USER" "$CATALOG_DB_PASSWORD"
create_database "$SCAN_DB" "$SCAN_DB_USER" "$SCAN_DB_PASSWORD"
create_database "$QUERY_DB" "$QUERY_DB_USER" "$QUERY_DB_PASSWORD"
