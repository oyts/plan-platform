#!/bin/sh
# Configuration-only correction; keep the original image, volumes and secrets.
set -eu
root=/home/oyts/plan-platform
cd "$root/current"
source=$root/client-public/releases/windows-014-yJuIcE/production-timezone.yml
overlay=$root/production-timezone.yml
test -f "$source"
test ! -e "$overlay"
sh scripts/mysql-backup.sh
compose() { docker compose -p plan-platform -f docker-compose.production.yml -f "$root/client-public/compose.yml" "$@"; }
cp -p "$source" "$overlay"
chmod 600 "$overlay"
rollback() {
  compose up -d --no-deps backend
  docker exec plan-platform-web-1 nginx -s reload
  mv "$overlay" "$source.disabled"
  echo 'Previous backend timezone restored; database retained.'
}
if ! compose -f "$overlay" config --quiet; then rollback; exit 1; fi
if ! compose -f "$overlay" up -d --no-deps backend; then rollback; exit 1; fi
ready=false
for attempt in $(seq 1 40); do
  status=$(docker inspect plan-platform-backend-1 --format '{{.State.Health.Status}}')
  if [ "$status" = healthy ]; then ready=true; break; fi
  sleep 1
done
if [ "$ready" != true ]; then rollback; exit 1; fi
docker exec plan-platform-web-1 nginx -s reload
if ! curl --max-time 10 -fsS https://plan.zijun701.cn/api/auth/config | grep -q '"registrationEnabled":false'; then rollback; exit 1; fi
docker exec plan-platform-backend-1 date '+backend %Y-%m-%d %H:%M %Z'
echo 'Production runtime timezone is Asia/Shanghai; no migration or image rebuild.'
