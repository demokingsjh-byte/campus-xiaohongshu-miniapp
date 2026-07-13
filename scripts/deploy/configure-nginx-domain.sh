#!/usr/bin/env bash
set -euo pipefail

DOMAIN="huanwoshidai.com.cn"
EXPECTED_IP="121.43.83.21"
APP_HOME="${APP_HOME:-/opt/campus-platform}"
SOURCE_DIR="${1:-.}"
ACME_ROOT="/var/www/letsencrypt"
NGINX_BIN="${NGINX_BIN:-$(command -v nginx)}"

if [ -z "$NGINX_BIN" ]; then
  echo "::error title=Nginx unavailable::nginx executable was not found on the server." >&2
  exit 1
fi

NGINX_CONFIG="$($NGINX_BIN -T 2>&1)"
if grep -Eq 'configuration file /www/server/panel/vhost/nginx/|include[[:space:]]+/www/server/panel/vhost/nginx/\*\.conf' <<<"$NGINX_CONFIG"; then
  NGINX_SITE_DIR=/www/server/panel/vhost/nginx
elif grep -Eq 'configuration file /etc/nginx/conf\.d/|include[[:space:]]+/etc/nginx/conf\.d/\*\.conf' <<<"$NGINX_CONFIG"; then
  NGINX_SITE_DIR=/etc/nginx/conf.d
else
  echo "::error title=Nginx site directory unknown::The active nginx configuration does not include a supported virtual-host directory." >&2
  exit 1
fi

TARGET="$NGINX_SITE_DIR/$DOMAIN.conf"
HTTP_SOURCE="$SOURCE_DIR/$DOMAIN.http.conf"
SSL_SOURCE="$SOURCE_DIR/$DOMAIN.ssl.conf"
BACKUP=""

reload_nginx() {
  "$NGINX_BIN" -t
  if ! systemctl is-active --quiet nginx 2>/dev/null || ! systemctl reload nginx; then
    "$NGINX_BIN" -s reload
  fi
}

wait_for_http() {
  local label="$1"
  shift

  for _ in {1..10}; do
    if curl --fail --silent --show-error --max-time 10 "$@" >/dev/null; then
      return 0
    fi
    sleep 1
  done

  echo "::error title=Nginx health check failed::$label did not become healthy after the nginx reload." >&2
  return 1
}

rollback() {
  if [ -n "$BACKUP" ] && [ -f "$BACKUP" ]; then
    cp "$BACKUP" "$TARGET"
  else
    rm -f "$TARGET"
  fi
  "$NGINX_BIN" -t && (systemctl reload nginx || "$NGINX_BIN" -s reload) || true
}

install_certbot() {
  if command -v certbot >/dev/null 2>&1; then
    return 0
  fi

  if command -v apt-get >/dev/null 2>&1; then
    apt-get update
    DEBIAN_FRONTEND=noninteractive apt-get install -y certbot
  elif command -v dnf >/dev/null 2>&1; then
    dnf install -y certbot || {
      dnf install -y epel-release
      dnf install -y certbot
    }
  elif command -v yum >/dev/null 2>&1; then
    yum install -y certbot || {
      yum install -y epel-release
      yum install -y certbot
    }
  fi

  command -v certbot >/dev/null 2>&1
}

if [ ! -f "$HTTP_SOURCE" ] || [ ! -f "$SSL_SOURCE" ]; then
  echo "Nginx source files are missing in $SOURCE_DIR" >&2
  exit 1
fi

if [ ! -f "$APP_HOME/admin/dist/index.html" ]; then
  echo "Admin UI entry not found: $APP_HOME/admin/dist/index.html" >&2
  exit 1
fi

mkdir -p "$NGINX_SITE_DIR" "$ACME_ROOT"

if [ -f "$TARGET" ]; then
  BACKUP="$TARGET.bak.$(date +%Y%m%d%H%M%S)"
  cp "$TARGET" "$BACKUP"
  echo "Existing Nginx site backed up to $BACKUP"
fi

trap rollback ERR

cp "$HTTP_SOURCE" "$TARGET"
reload_nginx

wait_for_http "HTTP admin UI" -H "Host: $DOMAIN" http://127.0.0.1/
wait_for_http "HTTP admin API" -H "Host: $DOMAIN" \
  http://127.0.0.1/admin-api/system/auth/get-permission-info

echo "HTTP virtual host is healthy. Preparing HTTPS certificate."

if ! install_certbot; then
  echo "Unable to install certbot; HTTP configuration remains active." >&2
  trap - ERR
  exit 2
fi

certbot certonly \
  --webroot \
  --webroot-path "$ACME_ROOT" \
  --domains "$DOMAIN" \
  --domains "www.$DOMAIN" \
  --agree-tos \
  --non-interactive \
  --register-unsafely-without-email \
  --keep-until-expiring

RENEW_HOOK_DIR=/etc/letsencrypt/renewal-hooks/deploy
RENEW_HOOK="$RENEW_HOOK_DIR/reload-campus-nginx.sh"
mkdir -p "$RENEW_HOOK_DIR"
printf '%s\n' \
  '#!/usr/bin/env bash' \
  'set -e' \
  "if ! systemctl reload nginx 2>/dev/null; then '$NGINX_BIN' -s reload; fi" \
  > "$RENEW_HOOK"
chmod 0755 "$RENEW_HOOK"

cp "$SSL_SOURCE" "$TARGET"
reload_nginx

wait_for_http "HTTPS admin UI" --resolve "$DOMAIN:443:127.0.0.1" \
  "https://$DOMAIN/"
wait_for_http "HTTPS admin API" --resolve "$DOMAIN:443:127.0.0.1" \
  "https://$DOMAIN/admin-api/system/auth/get-permission-info"

trap - ERR
echo "Nginx domain configuration completed with HTTPS: https://$DOMAIN"
