#!/bin/sh
# Apply the new image to a restored production snapshot, never the live database.
set -eu
umask 077
cd "$(dirname "$0")/.."
directory=$(pwd -P)
case "$directory" in /home/*/plan-platform/ai-candidates/*|/opt/plan-platform/ai-candidates/*) ;; *) exit 1;; esac
root=$(dirname "$(dirname "$directory")")
set -a; . ./.env; . ./candidate.env; set +a
case "$AI_DB_USER" in ai_[a-f0-9]*) ;; *) exit 1;; esac
case "$PLAN_VERSION" in *[!0-9.]*) exit 1;; esac
current=$(readlink -f "$root/current")
test "$(dirname "$current")" = "$root/releases"
(unset PLAN_MYSQL_DATABASE PLAN_MYSQL_BACKUP_DIR PLAN_COMPOSE_PROJECT; cd "$current"; sh scripts/mysql-backup.sh)
backup=$(find "$current/backups" -maxdepth 1 -type f -name 'plan_platform-mysql-*.sql.gz' | sort | tail -n 1)
test -n "$backup"
(unset PLAN_MYSQL_DATABASE PLAN_MYSQL_BACKUP_DIR PLAN_COMPOSE_PROJECT; cd "$current"; sh scripts/mysql-restore-drill.sh "$backup") > "$directory/production-restore-drill.txt"
database=$(sed -n 's/^Restore validated in isolated database \(plan_restore_[0-9_]*\);.*/\1/p' production-restore-drill.txt)
case "$database" in plan_restore_[0-9]*_[0-9]*) ;; *) echo 'Verified isolated restore required'; exit 1;; esac
case "$database" in *[!a-z0-9_]*) exit 1;; esac
sql() { docker exec -i -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" "$AI_MYSQL_CONTAINER" mysql --default-character-set=utf8mb4 -uroot -N; }
printf "GRANT ALL PRIVILEGES ON %s.* TO '%s'@'%%';\n" "$database" "$AI_DB_USER" | sql
tables=$(printf "SELECT table_name FROM information_schema.tables WHERE table_schema='%s' ORDER BY table_name;\n" "$database" | sql)
# V18 adds nullable metadata to the quota ledger. CHECKSUM TABLE includes the
# schema, so compare its original columns separately instead of ignoring data.
quota_columns=$(printf "SELECT column_name FROM information_schema.columns WHERE table_schema='%s' AND table_name='ai_quota_requests' ORDER BY ordinal_position;\n" "$database" | sql | paste -sd, -)
case "$quota_columns" in ''|*[!a-z0-9_,]*) exit 1;; esac
quota_projection() { printf 'SELECT JSON_ARRAY(%s) FROM %s.ai_quota_requests ORDER BY 1;\n' "$quota_columns" "$database" | sql | sha256sum | cut -d ' ' -f 1; }
quota_projection > production-quota-before.sha256
for table in $tables; do
  case "$table" in *[!a-zA-Z0-9_]*) exit 1;; esac
  [ "$table" = flyway_schema_history ] && continue
  [ "$table" = ai_quota_requests ] && continue
  printf 'CHECKSUM TABLE %s.%s EXTENDED;\n' "$database" "$table" | sql
done > production-business-before.txt
name="plan-ai-migration-$(date -u +%Y%m%d%H%M%S)"
printf '%s\n' "$name" > migration-container.txt
# The restored real data is never used as model context: no key mount, AI disabled,
# no public port, no bootstrap/promotion, no acceptance login or outgoing AI job.
docker run -d --name "$name" --network plan-platform_default --memory 480m --cpus 0.75 \
  --label "plan.ai.migration=$directory" --env-file "$directory/app.env" \
  -e "DB_URL=jdbc:mysql://mysql:3306/$database?serverTimezone=Asia/Shanghai&characterEncoding=utf8" \
  -e AI_ENABLED=false -e AI_API_KEY_FILE= -e BOOTSTRAP_ADMIN_EMAIL= \
  -e BOOTSTRAP_ADMIN_TOKEN_HASH= -e BOOTSTRAP_ADMIN_EXPIRES_AT= \
  "plan-platform-api:$PLAN_VERSION" >/dev/null
healthy=false
for attempt in $(seq 1 60); do
  if docker exec "$name" sh -c 'wget -q -O /dev/null http://127.0.0.1:8080/actuator/health/readiness' 2>/dev/null; then healthy=true; break; fi
  sleep 2
done
docker stop "$name" >/dev/null
[ "$healthy" = true ] || { echo 'Migration drill failed; stopped container and restore DB retained'; exit 1; }
test "$(printf 'SELECT MAX(CAST(version AS UNSIGNED)) FROM %s.flyway_schema_history WHERE success=1;\n' "$database" | sql)" = 18
quota_projection > production-quota-after.sha256
cmp production-quota-before.sha256 production-quota-after.sha256
for table in $tables; do
  [ "$table" = flyway_schema_history ] && continue
  [ "$table" = ai_quota_requests ] && continue
  printf 'CHECKSUM TABLE %s.%s EXTENDED;\n' "$database" "$table" | sql
done > production-business-after.txt
cmp production-business-before.txt production-business-after.txt
old_version=$(python3 -c 'import json,sys;print(json.load(open(sys.argv[1]))["version"])' "$current/web/package.json")
case "$old_version" in ''|*[!0-9.]*) exit 1;; esac
rollback_name="$name-rollback"
docker run -d --name "$rollback_name" --network plan-platform_default --memory 480m --cpus 0.75 \
  --label "plan.ai.migration=$directory" --env-file "$directory/app.env" \
  -e "DB_URL=jdbc:mysql://mysql:3306/$database?serverTimezone=Asia/Shanghai&characterEncoding=utf8" \
  -e AI_ENABLED=false -e AI_API_KEY_FILE= -e BOOTSTRAP_ADMIN_EMAIL= \
  -e BOOTSTRAP_ADMIN_TOKEN_HASH= -e BOOTSTRAP_ADMIN_EXPIRES_AT= \
  "plan-platform-api:$old_version" >/dev/null
healthy=false
for attempt in $(seq 1 60); do
  if docker exec "$rollback_name" sh -c 'wget -q -O /dev/null http://127.0.0.1:8080/actuator/health/readiness' 2>/dev/null; then healthy=true; break; fi
  sleep 2
done
docker stop "$rollback_name" >/dev/null
[ "$healthy" = true ] || { echo 'Old image rollback drill failed; production unchanged'; exit 1; }
for table in $tables; do
  [ "$table" = flyway_schema_history ] && continue
  [ "$table" = ai_quota_requests ] && continue
  printf 'CHECKSUM TABLE %s.%s EXTENDED;\n' "$database" "$table" | sql
done > production-business-rollback.txt
cmp production-business-before.txt production-business-rollback.txt
quota_projection > production-quota-rollback.sha256
cmp production-quota-before.sha256 production-quota-rollback.sha256
printf 'PASS: production snapshot V18 migration AND previous-image rollback; unchanged tables and all original quota-ledger columns match. No key, public port, AI request or production write. Restore DB and stopped containers retained.\n'
