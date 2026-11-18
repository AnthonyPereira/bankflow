#!/bin/sh
# Crée une base et un utilisateur dédié par service (BF-002-02 et BF-002-03).
# Exécuté une seule fois par l'image PostgreSQL, à la création du volume.
# Chaque utilisateur possède sa base et ne peut se connecter à aucune autre.

create_service_database() {
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
        -v db_name="$1" -v db_user="$2" -v db_password="$3" <<'SQL'
CREATE ROLE :"db_user" LOGIN PASSWORD :'db_password' NOSUPERUSER NOCREATEDB NOCREATEROLE;
CREATE DATABASE :"db_name" OWNER :"db_user";
REVOKE ALL ON DATABASE :"db_name" FROM PUBLIC;
SQL
}

create_service_database auth_db auth_user "$AUTH_DB_PASSWORD"
create_service_database customer_db customer_user "$CUSTOMER_DB_PASSWORD"
create_service_database banking_db banking_user "$BANKING_DB_PASSWORD"
create_service_database notification_db notification_user "$NOTIFICATION_DB_PASSWORD"
create_service_database analytics_db analytics_user "$ANALYTICS_DB_PASSWORD"

# Les bases système ne restent accessibles qu'à l'administrateur
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<'SQL'
REVOKE CONNECT ON DATABASE postgres FROM PUBLIC;
REVOKE CONNECT ON DATABASE template1 FROM PUBLIC;
SQL
