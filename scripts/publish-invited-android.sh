#!/bin/sh
# Private files are mounted only into the authenticated API, never web/Nginx.
set -eu
umask 077
root=/home/oyts/plan-platform
stage=${1:?Supply verified staging directory}
code=${2:?Supply positive version code}
version=${3:?Supply x.y.z version}
case "$stage" in "$root"/android-private/staging/*) ;; *) echo 'Invalid staging path'; exit 1;; esac
test "$(realpath "$stage")" = "$stage"
case "$code" in ''|*[!0-9]*) exit 1;; esac
case "$version" in ''|*[!0-9.]*) exit 1;; esac
managed=$root/android-private
release=$managed/releases/$code
test ! -e "$release" || { echo 'Immutable Android release already exists'; exit 1; }
test -f "$stage/release.json" && test -f "$stage/current.json" && test -f "$stage/PlanCabin-$version-release.apk"
test -f "$root/current/scripts/production-compose.sh"
compose() { sh "$root/current/scripts/production-compose.sh" "$root/current" "$@"; }
rollback() {
  if [ -f "$stage/previous-current.json" ]; then cp -p "$stage/previous-current.json" "$managed/current.rollback.json"; mv -f "$managed/current.rollback.json" "$managed/current.json";
  elif [ -f "$managed/current.json" ]; then mv "$managed/current.json" "$stage/disabled-current.json"; fi
  if [ -f "$stage/previous-compose.yml" ]; then cp -p "$stage/previous-compose.yml" "$managed/compose.yml";
  elif [ -f "$managed/compose.yml" ]; then mv "$managed/compose.yml" "$stage/disabled-compose.yml"; fi
  compose up -d --no-deps backend || echo 'Backend rollback failed; inspect retained configuration.'
  echo 'Previous private update config restored; APK and database retained.'
}
if [ -f "$managed/current.json" ]; then cp -p "$managed/current.json" "$stage/previous-current.json"; fi
if [ -f "$managed/compose.yml" ]; then cp -p "$managed/compose.yml" "$stage/previous-compose.yml"; fi
install -d -m 711 "$managed" "$managed/releases"
install -d -m 755 "$release"
install -m 644 "$stage/release.json" "$release/release.json"
install -m 644 "$stage/PlanCabin-$version-release.apk" "$release/PlanCabin-$version-release.apk"
cat > "$stage/compose.next.yml" <<'YAML'
services:
  backend:
    environment:
      ANDROID_UPDATE_ENABLED: "true"
      ANDROID_UPDATE_DIRECTORY: /srv/plan-android-updates
      ANDROID_UPDATE_PACKAGE: cn.zijun701.plan.app
    volumes:
      - /home/oyts/plan-platform/android-private:/srv/plan-android-updates:ro
YAML
install -m 644 "$stage/current.json" "$managed/current.next.json"
mv -f "$managed/current.next.json" "$managed/current.json"
install -m 600 "$stage/compose.next.yml" "$managed/compose.yml"
trap 'rollback; exit 1' HUP INT TERM
if ! compose config --quiet || ! compose up -d --no-deps backend; then rollback; exit 1; fi
ready=false
for attempt in $(seq 1 30); do
  if curl --max-time 5 -fsS https://plan.zijun701.cn/api/health | grep -q '"service":"plan-platform-api"'; then ready=true; break; fi
  sleep 2
done
if [ "$ready" != true ]; then rollback; exit 1; fi
trap - HUP INT TERM
echo 'Private update staged. Authenticated manifest and APK hash checks are REQUIRED before acceptance.'
echo "Rollback: sh $stage/rollback.sh"
# Keep a narrow, reversible rollback command without credentials.
printf '#!/bin/sh\nset -eu\n' > "$stage/rollback.sh"
if [ -f "$stage/previous-current.json" ]; then printf 'cp -p "%s/previous-current.json" "%s/current.json"\n' "$stage" "$managed" >> "$stage/rollback.sh";
else printf 'mv "%s/current.json" "%s/disabled-current.json"\n' "$managed" "$stage" >> "$stage/rollback.sh"; fi
if [ -f "$stage/previous-compose.yml" ]; then printf 'cp -p "%s/previous-compose.yml" "%s/compose.yml"\n' "$stage" "$managed" >> "$stage/rollback.sh";
else printf 'mv "%s/compose.yml" "%s/disabled-compose.yml"\n' "$managed" "$stage" >> "$stage/rollback.sh"; fi
printf 'sh "%s/current/scripts/production-compose.sh" "%s/current" up -d --no-deps backend\n' "$root" "$root" >> "$stage/rollback.sh"
