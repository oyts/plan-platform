#!/bin/sh
# Narrow, reversible web-container overlay. No database/host Nginx changes.
set -eu
root=/home/oyts/plan-platform
managed=$root/client-public
action=${1:-}
run=${2:-}
version=__RELEASE_VERSION__
case "$run" in ''|*[!a-zA-Z0-9_-]*) echo 'Expected a safe release run ID'; exit 1;; esac
release=$managed/releases/$run
cd "$root/current"
test "$(docker inspect plan-platform-web-1 --format '{{index .Config.Labels "com.docker.compose.project"}}')" = plan-platform
test -f "$release/nginx.conf"
test -f "$release/public/downloads/manifest.json"
test ! -e "$managed/current" || test -L "$managed/current"
compose() { sh "$root/current/scripts/production-compose.sh" "$root/current" "$@"; }
rollback() {
  if [ -f "$release/previous-current" ]; then
    previous=$(cat "$release/previous-current")
    case "$previous" in releases/*) ;; *) echo 'Unsafe rollback pointer'; return 1;; esac
    ln -s "$previous" "$managed/current.rollback-$run"
    mv -Tf "$managed/current.rollback-$run" "$managed/current"
  fi
  if [ -f "$release/previous-compose.yml" ]; then
    cp -p "$release/previous-compose.yml" "$managed/compose.yml"
    cp -p "$release/previous-nginx.conf" "$managed/nginx.conf"
    compose -f "$managed/compose.yml" up -d --no-deps web
  else
    if [ -f "$managed/compose.yml" ]; then mv "$managed/compose.yml" "$release/disabled-compose.yml"; fi
    if [ -f "$managed/nginx.conf" ]; then mv "$managed/nginx.conf" "$release/disabled-nginx.conf"; fi
    compose up -d --no-deps web
  fi
  echo 'Previous web entry restored; uploaded version retained; database unchanged.'
}
if [ "$action" = rollback ]; then rollback; exit; fi
[ "$action" = activate ] || { echo 'Use activate or rollback'; exit 1; }
test ! -f "$release/activation-started"
# Validate and retain previous immutable downloads before touching the live pointer.
if [ -L "$managed/current" ]; then
  previous=$(readlink "$managed/current")
  case "$previous" in releases/*) ;; *) echo 'Unsafe previous pointer'; exit 1;; esac
  test "$(realpath "$managed/$previous")" = "$managed/$previous" || { echo 'Previous release escapes managed directory'; exit 1; }
  sh "$release/retain-windows-releases.sh" "$managed/$previous/public" "$release/public"
fi
# Nginx workers need traversal through the mount, not access to deployment metadata.
chmod 711 "$managed" "$managed/releases" "$release"
find "$release/public" -type d -exec chmod 755 {} \;
find "$release/public" -type f -exec chmod 644 {} \;
chmod 600 "$release/nginx.conf" "$release/compose.yml" "$release/publish.sh" "$release/retain-windows-releases.sh" "$release/upload.sha256" "$release/upload.tar.gz"
curl --max-time 10 -fsS https://plan.zijun701.cn/api/health | grep -q '"service":"plan-platform-api"'
curl --max-time 10 -fsS https://plan.zijun701.cn/api/auth/config | grep -q '"registrationEnabled":false'
# Preserve the actual running config and previous manifest/pointer before activation.
docker cp plan-platform-web-1:/etc/nginx/conf.d/default.conf "$release/running-nginx.before.conf"
docker cp plan-platform-web-1:/usr/share/nginx/html/downloads/manifest.json "$release/image-downloads.before.json"
curl --max-time 10 -fsS https://plan.zijun701.cn/downloads/manifest.json > "$release/public-downloads.before.json"
if [ -L "$managed/current" ]; then readlink "$managed/current" > "$release/previous-current"; fi
if [ -f "$managed/compose.yml" ]; then
  cp -p "$managed/compose.yml" "$release/previous-compose.yml"
  cp -p "$managed/nginx.conf" "$release/previous-nginx.conf"
fi
touch "$release/activation-started"
chmod 600 "$release"/*.before.conf "$release"/*.before.json
cp -p "$release/nginx.conf" "$managed/nginx.conf"
cp -p "$release/compose.yml" "$managed/compose.yml"
ln -s "releases/$run" "$managed/current.new-$run"
mv -Tf "$managed/current.new-$run" "$managed/current"
if ! compose -f "$managed/compose.yml" config --quiet; then rollback; exit 1; fi
trap 'rollback; exit 1' HUP INT TERM
if ! compose -f "$managed/compose.yml" up -d --no-deps web; then rollback; exit 1; fi
if ! docker exec plan-platform-web-1 nginx -t; then rollback; exit 1; fi
ready=false
for attempt in 1 2 3 4 5 6 7 8 9 10; do
  if curl --connect-timeout 2 --max-time 5 -fsS https://plan.zijun701.cn/client-updates/windows/latest.json | grep -Fq "\"version\": \"$version\""; then ready=true; break; fi
  sleep 1
done
if [ "$ready" != true ]; then rollback; exit 1; fi
if ! curl --max-time 10 -fsS https://plan.zijun701.cn/api/auth/config | grep -q '"registrationEnabled":false'; then rollback; exit 1; fi
trap - HUP INT TERM
touch "$release/activation-completed"
echo 'Windows static source enabled. External hash, signature, browser and native checks still required.'
