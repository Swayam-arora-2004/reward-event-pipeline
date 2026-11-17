#!/bin/sh
# Creates a non-superuser role for the app and makes it the owner of the app database.
# Runs once, the first time the data volume is created. Credentials come from environment variables.
set -e
psql -v ON_ERROR_STOP=1 -U postgres -d "$POSTGRES_DB" \
  -v app_user="$APP_DB_USER" -v app_pw="$APP_DB_PASSWORD" -v db="$POSTGRES_DB" <<'SQL'
CREATE ROLE :"app_user" LOGIN PASSWORD :'app_pw';
ALTER DATABASE :"db" OWNER TO :"app_user";
SQL
