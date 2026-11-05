#!/usr/bin/env bash
# 在生产服务器上部署自建 MySQL 8，导入备份，并把 campus.env 切换到本机数据库。
# 用法（root）：
#   bash setup-server-mysql.sh /root/production-db-20260826-150935.sql.gz
# 可选环境变量：
#   USE_EXISTING=1 MYSQL_ROOT_PASSWORD=xxx   已用宝塔等方式装好 MySQL 时，跳过 Docker
#   DB_NAME=SuperCampus  DB_USER=campus  DB_PASSWORD=<不填则随机生成>
#   BACKEND_SERVICE=campus-platform.service
set -Eeuo pipefail

DUMP="${1:?用法: setup-server-mysql.sh <备份 .sql.gz 或 .sql>}"
APP_HOME="${APP_HOME:-/opt/campus-platform}"
ENV_FILE="${ENV_FILE:-$APP_HOME/backend/campus.env}"
DB_NAME="${DB_NAME:-SuperCampus}"
DB_USER="${DB_USER:-campus}"
DB_PASSWORD="${DB_PASSWORD:-$(openssl rand -base64 24 | tr -dc 'A-Za-z0-9' | head -c 24)}"
BACKEND_SERVICE="${BACKEND_SERVICE:-campus-platform.service}"
CONTAINER="${CONTAINER:-campus-mysql}"
DATA_DIR="${DATA_DIR:-$APP_HOME/mysql}"
SECRET_FILE="$APP_HOME/backend/mysql-root.secret"

[ -r "$DUMP" ] || { echo "备份文件不存在: $DUMP" >&2; exit 1; }
[ -w "$ENV_FILE" ] || { echo "找不到或无法写入 $ENV_FILE" >&2; exit 1; }
umask 077

if [ "${USE_EXISTING:-0}" = "1" ]; then
  : "${MYSQL_ROOT_PASSWORD:?USE_EXISTING=1 时需要提供 MYSQL_ROOT_PASSWORD}"
  MYSQL_BIN="$(command -v mysql || command -v mariadb)"
  run_sql() { MYSQL_PWD="$MYSQL_ROOT_PASSWORD" "$MYSQL_BIN" -h127.0.0.1 -uroot --default-character-set=utf8mb4 "$@"; }
  USER_HOSTS=("localhost" "127.0.0.1")
else
  command -v docker >/dev/null || {
    echo "未安装 Docker。可选：1) 宝塔面板 软件商店 安装 MySQL 8.0 后用 USE_EXISTING=1 重跑；2) 先安装 Docker。" >&2
    exit 1
  }
  if ss -ltn 2>/dev/null | grep -q ':3306 '; then
    echo "3306 已被占用，可能已有 MySQL。请改用 USE_EXISTING=1 MYSQL_ROOT_PASSWORD=..." >&2
    exit 1
  fi
  if [ -f "$SECRET_FILE" ]; then
    MYSQL_ROOT_PASSWORD="$(cat "$SECRET_FILE")"
  else
    MYSQL_ROOT_PASSWORD="$(openssl rand -base64 32 | tr -dc 'A-Za-z0-9' | head -c 28)"
    printf '%s' "$MYSQL_ROOT_PASSWORD" > "$SECRET_FILE"
  fi
  mkdir -p "$DATA_DIR/data" "$DATA_DIR/conf"
  # 小内存服务器：关 performance_schema，缓冲池压到 256M
  cat > "$DATA_DIR/conf/campus.cnf" <<'CNF'
[mysqld]
character-set-server=utf8mb4
collation-server=utf8mb4_unicode_ci
default-time-zone='+08:00'
lower_case_table_names=1
performance_schema=OFF
innodb_buffer_pool_size=256M
max_connections=200
max_allowed_packet=64M
skip-name-resolve
CNF
  if ! docker ps -a --format '{{.Names}}' | grep -qx "$CONTAINER"; then
    echo "启动 MySQL 8.0 容器（仅监听 127.0.0.1:3306）..."
    docker run -d --name "$CONTAINER" --restart unless-stopped \
      -p 127.0.0.1:3306:3306 \
      -e MYSQL_ROOT_PASSWORD="$MYSQL_ROOT_PASSWORD" -e TZ=Asia/Shanghai \
      -v "$DATA_DIR/data:/var/lib/mysql" \
      -v "$DATA_DIR/conf:/etc/mysql/conf.d:ro" \
      mysql:8.0
  fi
  run_sql() { docker exec -i -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" "$CONTAINER" mysql -uroot --default-character-set=utf8mb4 "$@"; }
  USER_HOSTS=("%")
  echo -n "等待 MySQL 就绪"
  for _ in $(seq 1 60); do
    run_sql -e 'SELECT 1' >/dev/null 2>&1 && break; echo -n .; sleep 2
  done; echo
fi

run_sql -e 'SELECT VERSION()' >/dev/null || { echo "连不上 MySQL" >&2; exit 1; }

existing="$(run_sql -N -e "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='$DB_NAME'")"
if [ "$existing" != "0" ]; then
  echo "库 $DB_NAME 已有 $existing 张表，为避免覆盖，停止。确需重导请先手动处理。" >&2
  exit 1
fi

echo "创建数据库和应用账号 $DB_USER ..."
{
  echo "CREATE DATABASE IF NOT EXISTS \`$DB_NAME\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
  for h in "${USER_HOSTS[@]}"; do
    echo "CREATE USER IF NOT EXISTS '$DB_USER'@'$h' IDENTIFIED BY '$DB_PASSWORD';"
    echo "ALTER USER '$DB_USER'@'$h' IDENTIFIED BY '$DB_PASSWORD';"
    echo "GRANT ALL PRIVILEGES ON \`$DB_NAME\`.* TO '$DB_USER'@'$h';"
  done
  echo "FLUSH PRIVILEGES;"
} | run_sql

echo "导入备份（去掉 RDS 的 DEFINER，兼容 MySQL 5.7 排序规则）..."
if [[ "$DUMP" == *.gz ]]; then gzip -dc "$DUMP"; else cat "$DUMP"; fi \
  | sed -E -e 's/DEFINER=`[^`]+`@`[^`]+`//g' -e 's/utf8mb4_0900_(ai|as)_c[is]/utf8mb4_general_ci/g' \
  | run_sql "$DB_NAME"
tables="$(run_sql -N -e "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='$DB_NAME'")"
echo "导入完成：$tables 张表（8/26 备份应为 74）"

echo "备份并改写 $ENV_FILE ..."
cp -p "$ENV_FILE" "$ENV_FILE.bak.$(date +%Y%m%d%H%M%S)"
URL="jdbc:mysql://127.0.0.1:3306/$DB_NAME?allowMultiQueries=true&useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&autoReconnect=true&rewriteBatchedStatements=true"
tmp="$(mktemp)"
grep -vE '^(CAMPUS_DB_URL|CAMPUS_DB_USERNAME|CAMPUS_DB_PASSWORD|CAMPUS_DB_SLAVE_URL|CAMPUS_DB_SLAVE_USERNAME|CAMPUS_DB_SLAVE_PASSWORD)=' "$ENV_FILE" > "$tmp" || true
{
  echo "CAMPUS_DB_URL=$URL"
  echo "CAMPUS_DB_USERNAME=$DB_USER"
  echo "CAMPUS_DB_PASSWORD=$DB_PASSWORD"
} >> "$tmp"
cat "$tmp" > "$ENV_FILE"; rm -f "$tmp"

echo "重启 $BACKEND_SERVICE ..."
systemctl restart "$BACKEND_SERVICE"
sleep 20
systemctl is-active "$BACKEND_SERVICE" && \
  curl -s -o /dev/null -w 'admin-api HTTP %{http_code}\n' http://127.0.0.1:48080/admin-api/system/auth/get-permission-info || true
curl -s http://127.0.0.1:48080/app-api/campus/esp32/assistant/health || true
echo
echo "完成。root 密码保存在 $SECRET_FILE（Docker 模式）。"
echo "下一步：在 GitHub Actions 重跑 Campus Deploy，补上 8/26 之后的升级 SQL。"
