#!/usr/bin/env bash

set -Eeuo pipefail

readonly backup_root="/opt/campus-platform/backups/git-export"
readonly env_file="/opt/campus-platform/backend/campus.env"
readonly output_path="${1:?Usage: export-production-database.sh OUTPUT_PATH}"

case "$output_path" in
  "$backup_root"/production-db-*.sql.gz) ;;
  *)
    echo "Output must stay inside $backup_root and use production-db-*.sql.gz" >&2
    exit 1
    ;;
esac

read_env_value() {
  local key="$1"
  grep -m1 "^${key}=" "$env_file" | cut -d= -f2- | tr -d '\r'
}

CAMPUS_DB_URL="$(read_env_value CAMPUS_DB_URL)"
CAMPUS_DB_USERNAME="$(read_env_value CAMPUS_DB_USERNAME)"
CAMPUS_DB_PASSWORD="$(read_env_value CAMPUS_DB_PASSWORD)"

case "$CAMPUS_DB_URL" in
  jdbc:mysql://*) ;;
  *) echo "Only jdbc:mysql URLs are supported." >&2; exit 1 ;;
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

dump_bin="$(command -v mysqldump || command -v mariadb-dump || true)"
if [ -z "$dump_bin" ]; then
  echo "mysqldump or mariadb-dump is required." >&2
  exit 1
fi

mkdir -p "$backup_root"
umask 077
temporary_path="${output_path}.tmp.$$"
export MYSQL_PWD="$CAMPUS_DB_PASSWORD"
trap 'rm -f "$temporary_path"; unset MYSQL_PWD CAMPUS_DB_PASSWORD' EXIT

"$dump_bin" \
  --host="$db_host" \
  --port="$db_port" \
  --user="$CAMPUS_DB_USERNAME" \
  --default-character-set=utf8mb4 \
  --single-transaction \
  --quick \
  --skip-lock-tables \
  --no-tablespaces \
  --hex-blob \
  --set-gtid-purged=OFF \
  --triggers \
  "$db_name" | gzip -9 > "$temporary_path"

test -s "$temporary_path"
mv "$temporary_path" "$output_path"
unset MYSQL_PWD CAMPUS_DB_PASSWORD
trap - EXIT

printf 'path=%s\n' "$output_path"
printf 'bytes=%s\n' "$(stat -c %s "$output_path")"
printf 'sha256=%s\n' "$(sha256sum "$output_path" | awk '{print $1}')"
