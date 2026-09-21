#!/usr/bin/env bash
set -euo pipefail

CURRENT_STEP="initializing application update"
on_error() {
  local exit_code=$?
  echo "::error title=Campus application update failed::$CURRENT_STEP failed at line ${BASH_LINENO[0]} (exit $exit_code)." >&2
  exit "$exit_code"
}
trap on_error ERR

APP_NAME="${APP_NAME:-campus-platform}"
APP_HOME="${APP_HOME:-/opt/campus-platform}"
BACKEND_JAR="${BACKEND_JAR:-campus-backend.jar}"
ADMIN_DIST_TGZ="${ADMIN_DIST_TGZ:-campus-admin-ui-dist.tar.gz}"
BACKEND_SERVICE="${BACKEND_SERVICE:-campus-platform.service}"
NGINX_RELOAD="${NGINX_RELOAD:-true}"
HEALTH_CHECK_URL="${HEALTH_CHECK_URL:-http://127.0.0.1:48080/admin-api/system/auth/get-permission-info}"
SUDO="${SUDO:-sudo}"

if [ "$(id -u)" = "0" ]; then
  SUDO=""
fi

mkdir -p "$APP_HOME/releases" "$APP_HOME/backend" "$APP_HOME/admin"

release_id="$(date +%Y%m%d%H%M%S)"
release_dir="$APP_HOME/releases/$release_id"
mkdir -p "$release_dir"

echo "[$APP_NAME] preparing release $release_id"
cp "$BACKEND_JAR" "$release_dir/$BACKEND_JAR"
cp "$ADMIN_DIST_TGZ" "$release_dir/$ADMIN_DIST_TGZ"

rollback_dir="$release_dir/rollback"
mkdir -p "$rollback_dir"
if [ -f "$APP_HOME/backend/app.jar" ]; then
  cp "$APP_HOME/backend/app.jar" "$rollback_dir/app.jar"
fi
if [ -d "$APP_HOME/admin/dist" ]; then
  tar -czf "$rollback_dir/admin-dist.tar.gz" -C "$APP_HOME/admin" dist
fi

rollback() {
  echo "[$APP_NAME] health check failed; restoring previous application files" >&2
  if [ -f "$rollback_dir/app.jar" ]; then
    cp "$rollback_dir/app.jar" "$APP_HOME/backend/app.jar"
  fi
  if [ -f "$rollback_dir/admin-dist.tar.gz" ]; then
    rm -rf "$APP_HOME/admin/dist"
    tar -xzf "$rollback_dir/admin-dist.tar.gz" -C "$APP_HOME/admin"
  fi
  $SUDO systemctl restart "$BACKEND_SERVICE"
}

echo "[$APP_NAME] updating backend jar"
CURRENT_STEP="updating backend jar"
cp "$release_dir/$BACKEND_JAR" "$APP_HOME/backend/app.jar"

echo "[$APP_NAME] updating admin UI"
CURRENT_STEP="updating admin UI"
rm -rf "$release_dir/admin-dist"
mkdir -p "$release_dir/admin-dist"
tar -xzf "$release_dir/$ADMIN_DIST_TGZ" -C "$release_dir/admin-dist"
rm -rf "$APP_HOME/admin/dist"
cp -R "$release_dir/admin-dist/dist" "$APP_HOME/admin/dist"

echo "[$APP_NAME] restarting backend service: $BACKEND_SERVICE"
CURRENT_STEP="restarting backend service"
$SUDO systemctl restart "$BACKEND_SERVICE"

healthy=false
for _ in $(seq 1 60); do
  if curl --fail --silent --show-error --max-time 5 "$HEALTH_CHECK_URL" >/dev/null; then
    healthy=true
    break
  fi
  sleep 2
done

if [ "$healthy" != "true" ]; then
  CURRENT_STEP="rolling back after a failed backend health check"
  rollback
  exit 1
fi

# Prune old releases so /opt/campus-platform/releases never fills the disk again.
# Each deploy writes ~350MB (jar + admin tarball + rollback copies); without pruning
# this directory grew to 24.9GB and took the whole server down on 2026-09-20.
# Keep a few entries because one deploy may create both <ts>/ and <ts>-database/
# sibling directories; 6 entries covers roughly the last 3 deploys.
KEEP_RELEASES="${KEEP_RELEASES:-6}"
if [ -d "$APP_HOME/releases" ]; then
  CURRENT_STEP="pruning old releases"
  cd "$APP_HOME/releases"
  old_entries="$(ls -1t | tail -n +$((KEEP_RELEASES + 1)))"
  if [ -n "$old_entries" ]; then
    echo "[$APP_NAME] pruning old releases (keeping newest $KEEP_RELEASES entries)"
    echo "$old_entries" | while IFS= read -r old_entry; do
      [ -n "$old_entry" ] || continue
      echo "[$APP_NAME] removing $old_entry"
      rm -rf -- "./$old_entry"
    done
  fi
  cd - >/dev/null
fi

$SUDO systemctl status "$BACKEND_SERVICE" --no-pager -l

if [ "$NGINX_RELOAD" = "true" ]; then
  echo "[$APP_NAME] reloading nginx"
  CURRENT_STEP="reloading nginx"
  $SUDO nginx -t
  if ! $SUDO systemctl reload nginx; then
    $SUDO nginx -s reload
  fi
fi

echo "[$APP_NAME] release $release_id completed"
