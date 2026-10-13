#!/bin/sh
# Temporary exact-file alias on the EXISTING web mount; no database or host TLS changes.
set -eu
root=/home/oyts/plan-platform/client-public
stage=$root/legacy-android/__RUN__
action=${1:-}
test "$(realpath "$stage")" = "$stage"
test "$(realpath "$root")" = "$root"
test "$(docker inspect plan-platform-web-1 --format '{{index .Config.Labels "com.docker.compose.project"}}')" = plan-platform
test -f "$stage/bridge-nginx.conf"
rollback() {
  test -f "$stage/nginx.actual-before.conf"
  if [ "$(sha256sum "$root/nginx.conf" | cut -d ' ' -f 1)" != '__ACTIVE_HASH__' ]; then
    echo 'Current config changed; not overwriting it. Original bridge endpoints remain time-limited if retained.'
    return 1
  fi
  cp -p "$stage/nginx.actual-before.conf" "$root/nginx.conf"
  if ! docker exec plan-platform-web-1 nginx -t; then
    cp -p "$stage/bridge-nginx.conf" "$root/nginx.conf"
    echo 'Close validation failed; original expiring bridge config retained.'
    return 1
  fi
  docker exec plan-platform-web-1 nginx -s reload
  touch "$stage/closed"
  echo 'Legacy bridge closed. Windows, authenticated Android and database untouched.'
}
if [ "$action" = close ]; then rollback; exit; fi
[ "$action" = activate ] || { echo 'Use activate or close'; exit 1; }
test ! -f "$stage/activation-started"
test "$(date -u +%s)" -lt '__EXPIRES__'
test "$(sha256sum "$root/nginx.conf" | cut -d ' ' -f 1)" = '__BASE_HASH__'
test "$(sha256sum "$stage/bridge-nginx.conf" | cut -d ' ' -f 1)" = '__ACTIVE_HASH__'
test "$(sha256sum "$stage/__APK_FILE__" | cut -d ' ' -f 1)" = '__APK_HASH__'
docker exec plan-platform-web-1 nginx -V 2>&1 | grep -q -- --with-http_secure_link_module
curl --max-time 10 -fsS https://plan.zijun701.cn/api/auth/config | grep -q '"registrationEnabled":false'
curl --max-time 10 -fsS https://plan.zijun701.cn/api/health | grep -q '"service":"plan-platform-api"'
cp -p "$root/nginx.conf" "$stage/nginx.actual-before.conf"
chmod 711 "$root/legacy-android" "$stage"
chmod 644 "$stage/latest.json" "$stage/__APK_FILE__"
chmod 600 "$stage/bridge-plan.json" "$stage/bridge-nginx.conf" "$stage/nginx.actual-before.conf" "$stage/nginx.expected-before.conf" "$stage/publish.sh" "$stage/upload.sha256" "$stage/upload.tar.gz"
touch "$stage/activation-started"
cp -p "$stage/bridge-nginx.conf" "$root/nginx.conf"
if ! docker exec plan-platform-web-1 nginx -t; then rollback; exit 1; fi
if ! docker exec plan-platform-web-1 nginx -s reload; then rollback; exit 1; fi
touch "$stage/activation-completed"
echo 'Temporary unauthenticated legacy bridge activated. BOTH exact endpoints expire in Nginx; external verification required.'
