#!/usr/bin/env bash
set -euo pipefail

CURRENT_STEP="initializing database migration"
on_error() {
  local exit_code=$?
  echo "::error title=Campus database migration failed::$CURRENT_STEP failed at line ${BASH_LINENO[0]} (exit $exit_code)." >&2
  exit "$exit_code"
}
trap on_error ERR

APP_HOME="${APP_HOME:-/opt/campus-platform}"
ENV_FILE="${ENV_FILE:-$APP_HOME/backend/campus.env}"
MIGRATION_DIR="${MIGRATION_DIR:-$(pwd)/migrations}"
BACKUP_DIR="${BACKUP_DIR:-$APP_HOME/releases/$(date +%Y%m%d%H%M%S)-database}"

if [ ! -r "$ENV_FILE" ]; then
  echo "Database environment file is missing or unreadable: $ENV_FILE" >&2
  exit 1
fi

while IFS= read -r line || [ -n "$line" ]; do
  line="${line%$'\r'}"
  case "$line" in
    ''|'#'*) continue ;;
  esac
  key="${line%%=*}"
  value="${line#*=}"
  if [ "$key" = "$line" ]; then
    continue
  fi
  case "$value" in
    \"*\") value="${value#\"}"; value="${value%\"}" ;;
    \'*\') value="${value#\'}"; value="${value%\'}" ;;
  esac
  case "$key" in
    CAMPUS_DB_URL) CAMPUS_DB_URL="$value" ;;
    CAMPUS_DB_USERNAME) CAMPUS_DB_USERNAME="$value" ;;
    CAMPUS_DB_PASSWORD) CAMPUS_DB_PASSWORD="$value" ;;
  esac
done < "$ENV_FILE"

: "${CAMPUS_DB_URL:?CAMPUS_DB_URL is required in $ENV_FILE}"
: "${CAMPUS_DB_USERNAME:?CAMPUS_DB_USERNAME is required in $ENV_FILE}"
: "${CAMPUS_DB_PASSWORD:?CAMPUS_DB_PASSWORD is required in $ENV_FILE}"

case "$CAMPUS_DB_URL" in
  jdbc:mysql://*) ;;
  *) echo "Only jdbc:mysql URLs are supported by the deployment migrator." >&2; exit 1 ;;
esac

db_url="${CAMPUS_DB_URL#jdbc:mysql://}"
db_authority="${db_url%%/*}"
db_path="${db_url#*/}"
db_name="${db_path%%\?*}"
db_host="${db_authority%%:*}"
db_port="${db_authority##*:}"
if [ "$db_host" = "$db_port" ]; then
  db_port=3306
fi

mysql_bin="$(command -v mysql || command -v mariadb || true)"
dump_bin="$(command -v mysqldump || command -v mariadb-dump || true)"
if [ -z "$mysql_bin" ] || [ -z "$dump_bin" ]; then
  echo "MySQL client tools are required on the deployment server." >&2
  exit 1
fi

mysql_args=(
  --host="$db_host"
  --port="$db_port"
  --user="$CAMPUS_DB_USERNAME"
  --default-character-set=utf8mb4
  --connect-timeout=10
  "$db_name"
)

export MYSQL_PWD="$CAMPUS_DB_PASSWORD"
trap 'unset MYSQL_PWD' EXIT

echo "Checking database connection..."
CURRENT_STEP="checking database connection"
"$mysql_bin" "${mysql_args[@]}" --batch --skip-column-names -e "SELECT 1" >/dev/null

mkdir -p "$BACKUP_DIR"
backup_tables=()
for table in system_menu system_dict_data system_tenant system_social_client campus_region campus_school_catalog campus_tenant_profile campus_miniapp_user campus_post campus_post_interaction; do
  exists="$("$mysql_bin" "${mysql_args[@]}" --batch --skip-column-names \
    -e "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '$table'")"
  if [ "$exists" = "1" ]; then
    backup_tables+=("$table")
  fi
done

if [ "${#backup_tables[@]}" -eq 0 ]; then
  echo "No campus database tables were found; refusing to migrate." >&2
  exit 1
fi

echo "Backing up affected tables to $BACKUP_DIR..."
CURRENT_STEP="backing up affected database tables"
"$dump_bin" \
  --host="$db_host" \
  --port="$db_port" \
  --user="$CAMPUS_DB_USERNAME" \
  --default-character-set=utf8mb4 \
  --single-transaction \
  --quick \
  --skip-lock-tables \
  "$db_name" "${backup_tables[@]}" | gzip -9 > "$BACKUP_DIR/affected-tables.sql.gz"

migrations=(
  campus-student-user-upgrade.sql
  campus-community-upgrade.sql
  campus-menu-encoding-repair.sql
  campus-menu.sql
  campus-school-data-upgrade.sql
  campus-wechat-miniapp-config.sql
)

for migration in "${migrations[@]}"; do
  migration_path="$MIGRATION_DIR/$migration"
  if [ ! -r "$migration_path" ]; then
    echo "Migration file is missing: $migration_path" >&2
    exit 1
  fi
  echo "Applying $migration..."
  CURRENT_STEP="applying $migration"
  if ! migration_output=$("$mysql_bin" "${mysql_args[@]}" < "$migration_path" 2>&1); then
    migration_output="${migration_output//$'\r'/ }"
    migration_output="${migration_output//$'\n'/ }"
    migration_output="${migration_output//'%'/'%25'}"
    echo "::error title=MySQL migration error::$migration: $migration_output" >&2
    exit 1
  fi
done

echo "Database migration completed. Backup: $BACKUP_DIR/affected-tables.sql.gz"
