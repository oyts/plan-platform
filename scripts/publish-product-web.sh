#!/bin/sh
# A web-only content release. Never rebuild/restart the API or database.
set -eu
umask 077
mode=${1:?Supply activate or rollback}
stage=${2:?Supply a verified web staging directory}
root=/home/oyts/plan-platform
case "$stage" in "$root"/product-web/releases/web-*) ;; *) echo 'Unexpected website release directory'; exit 1;; esac
test "$(realpath "$stage")" = "$stage"
id=$(basename "$stage")
case "$id" in *[!a-z0-9-]*) exit 1;; esac
current=$(readlink -f "$root/current")
test "$(dirname "$current")" = "$root/releases"
compose() { sh "$current/scripts/production-compose.sh" "$current" "$@"; }
restore() {
  test "$(cat "$stage/previous-release")" = "$current" || { echo 'Business release changed; stop and review before rollback.'; return 1; }
  cp -p "$stage/previous-compose-helper.sh" "$current/scripts/production-compose.sh"
  if [ -f "$stage/previous-web.yml" ]; then cp -p "$stage/previous-web.yml" "$root/production-web.yml";
  elif [ -f "$root/production-web.yml" ]; then mv "$root/production-web.yml" "$stage/withdrawn-web-$(date -u +%Y%m%d%H%M%S).yml"; fi
  compose config --quiet && compose up -d --no-deps web
  echo 'Previous website restored; API, database, downloads and native update sources retained.'
}
case "$mode" in
  rollback) restore; exit $? ;;
  activate) ;;
  *) echo 'Use activate or rollback'; exit 1 ;;
esac
test -f "$stage/dist/index.html" && test -f "$stage/dist/sw.js"
test -f "$stage/Dockerfile" && test -f "$stage/nginx.conf"
test -f "$stage/production-compose.sh"
# Backport only the new web overlay to the installed helper, retaining every
# previously approved mail, AI, static-download and timezone overlay.
if ! cmp -s "$stage/production-compose.sh" "$current/scripts/production-compose.sh"; then
  sed '/^  # Website overlay version guard$/,/^  # End website overlay version guard$/d;s|"$root/production-web.yml" ||' "$stage/production-compose.sh" > "$stage/helper-without-web.sh"
  sed '/^  # Website overlay version guard$/,/^  # End website overlay version guard$/d;s|"$root/production-web.yml" ||' "$current/scripts/production-compose.sh" > "$stage/installed-helper-without-web.sh"
  cmp -s "$stage/helper-without-web.sh" "$stage/installed-helper-without-web.sh" || { echo 'Existing Compose helper differs; no production change.'; exit 1; }
fi
base_version=$(sed -n 's/^PLAN_VERSION=//p' "$current/.env")
case "$base_version" in ''|*[!0-9.]*) echo 'Unexpected business release version'; exit 1;; esac
test ! -e "$stage/activation-started"
test ! -e "$stage/previous-release"
if docker image inspect "plan-platform-web:$id" >/dev/null 2>&1; then echo 'Website image already exists; preserve it and stage a new release.'; exit 1; fi
api_before=$(docker inspect --format='{{.Id}}' plan-platform-backend-1)
mysql_before=$(docker inspect --format='{{.Id}}' plan-platform-mysql-1)
downloads_before=$(curl -fsS --max-time 10 https://plan.zijun701.cn/downloads/manifest.json | sha256sum | cut -d ' ' -f 1)
windows_before=$(curl -fsS --max-time 10 https://plan.zijun701.cn/client-updates/windows/latest.json | sha256sum | cut -d ' ' -f 1)
# Keep older immutable asset URLs usable by already-open tabs and cached shells.
docker cp plan-platform-web-1:/usr/share/nginx/html/assets "$stage/previous-assets"
for old in "$stage"/previous-assets/*; do
  test -f "$old" || continue
  name=$(basename "$old")
  if [ -e "$stage/dist/assets/$name" ]; then cmp -s "$old" "$stage/dist/assets/$name" || { echo 'Hashed asset collision refused'; exit 1; };
  else cp -p "$old" "$stage/dist/assets/$name"; fi
done
docker build --pull=false -t "plan-platform-web:$id" "$stage"
sh "$current/scripts/mysql-backup.sh"
# No interruption of a known paid request, even though only the web proxy moves.
(
  cd "$current"
  . ./.env
  pending=$(docker exec -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" plan-platform-mysql-1 mysql -uroot -N plan_platform -e "SELECT COUNT(*) FROM ai_jobs WHERE state IN ('QUEUED','RUNNING');")
  test "$pending" = 0 || { echo 'Active AI request; website activation deferred.'; exit 1; }
)
cp -p "$current/scripts/production-compose.sh" "$stage/previous-compose-helper.sh"
if [ -f "$root/production-web.yml" ]; then cp -p "$root/production-web.yml" "$stage/previous-web.yml"; fi
printf '%s\n' "$current" > "$stage/previous-release"
printf '%s\n' "$api_before" > "$stage/api-container-before"
printf '%s\n' "$mysql_before" > "$stage/mysql-container-before"
touch "$stage/activation-started"
failed() { trap - EXIT HUP INT TERM; restore || echo 'Website rollback needs review; all images and data retained.'; exit 1; }
trap failed EXIT
trap 'exit 1' HUP INT TERM
printf '# plan-base-version: %s\nservices:\n  web:\n    image: plan-platform-web:%s\n' "$base_version" "$id" > "$root/production-web.yml"
chmod 600 "$root/production-web.yml"
cp -p "$stage/production-compose.sh" "$current/scripts/production-compose.sh"
compose config --quiet
compose up -d --no-deps web
ready=false
for attempt in $(seq 1 20); do
  if curl -fsS --max-time 5 https://plan.zijun701.cn/welcome | grep -q 'product-page-revision'; then ready=true; break; fi
  sleep 1
done
test "$ready" = true
curl -fsS --max-time 10 https://plan.zijun701.cn/api/health | grep -q '"service":"plan-platform-api"'
test "$(docker inspect --format='{{.Id}}' plan-platform-backend-1)" = "$api_before"
test "$(docker inspect --format='{{.Id}}' plan-platform-mysql-1)" = "$mysql_before"
test "$(curl -fsS --max-time 10 https://plan.zijun701.cn/downloads/manifest.json | sha256sum | cut -d ' ' -f 1)" = "$downloads_before"
test "$(curl -fsS --max-time 10 https://plan.zijun701.cn/client-updates/windows/latest.json | sha256sum | cut -d ' ' -f 1)" = "$windows_before"
trap - EXIT HUP INT TERM
touch "$stage/activated"
echo 'Website activated; API and MySQL container identities unchanged. Browser acceptance still required.'
echo "Rollback: sh $stage/publish-product-web.sh rollback $stage"
