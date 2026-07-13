#!/usr/bin/env bash
set -euo pipefail

DOMAIN="huanwoshidai.com.cn"
EXPECTED_IP="121.43.83.21"
APP_HOME="${APP_HOME:-/opt/campus-platform}"
SOURCE_DIR="${1:-.}"
ACME_ROOT="/var/www/letsencrypt"

if [ -d /www/server/panel/vhost/nginx ]; then
  NGINX_SITE_DIR=/www/server/panel/vhost/nginx
else
  NGINX_SITE_DIR=/etc/nginx/conf.d
fi

TARGET="$NGINX_SITE_DIR/$DOMAIN.conf"
HTTP_SOURCE="$SOURCE_DIR/$DOMAIN.http.conf"
SSL_SOURCE="$SOURCE_DIR/$DOMAIN.ssl.conf"
BACKUP=""

reload_nginx() {
  nginx -t
  if ! systemctl reload nginx; then
    nginx -s reload
  fi
}

rollback() {
  if [ -n "$BACKUP" ] && [ -f "$BACKUP" ]; then
    cp "$BACKUP" "$TARGET"
  else
    rm -f "$TARGET"
  fi
  nginx -t && (systemctl reload nginx || nginx -s reload) || true
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

curl --fail --silent --show-error --max-time 10 \
  -H "Host: $DOMAIN" http://127.0.0.1/ >/dev/null
curl --fail --silent --show-error --max-time 10 \
  -H "Host: $DOMAIN" http://127.0.0.1/admin-api/system/auth/get-permission-info >/dev/null

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

cp "$SSL_SOURCE" "$TARGET"
reload_nginx

curl --fail --silent --show-error --max-time 15 \
  --resolve "$DOMAIN:443:127.0.0.1" "https://$DOMAIN/" >/dev/null
curl --fail --silent --show-error --max-time 15 \
  --resolve "$DOMAIN:443:127.0.0.1" "https://$DOMAIN/admin-api/system/auth/get-permission-info" >/dev/null

trap - ERR
echo "Nginx domain configuration completed with HTTPS: https://$DOMAIN"
