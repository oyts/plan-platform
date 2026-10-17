#!/bin/sh
# V19 rehearsal on a restored snapshot only. Retain backup, DB and stopped apps.
set -eu
umask 077
root=${1:?Managed production root required}
image=${2:?Verified V19 candidate image required}
case "$root" in /home/*/plan-platform|/opt/plan-platform) ;; *) exit 1;; esac
test "$(realpath "$root")" = "$root"
test "$image" = plan-platform-api:0.1.14
current=$(readlink -f "$root/current")
test "$(dirname "$current")" = "$root/releases"
test -f "$current/.env"
run="$(date -u +%Y%m%d%H%M%S)_$(openssl rand -hex 4)"
directory="$root/release-drills/daily_$run"
test ! -e "$directory"
mkdir -p "$directory"
chmod 700 "$directory"
set -a; . "$current/.env"; set +a
container=$(cd "$current"; docker compose -p plan-platform -f docker-compose.production.yml ps -q mysql)
old_image=$(docker inspect --format '{{.Config.Image}}' plan-platform-backend-1)
case "$old_image" in plan-platform-api:0.1.13*) ;; *) echo 'Expected currently verified 0.1.13 image'; exit 1;; esac
sql() { docker exec -i -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" "$container" mysql --default-character-set=utf8mb4 -uroot -N; }
test "$(printf "SELECT MAX(CAST(version AS UNSIGNED)) FROM plan_platform.flyway_schema_history WHERE success=1;" | sql)" = 18
(cd "$current"; sh scripts/mysql-backup.sh) > "$directory/backup.txt"
backup=$(sed -n 's/^Verified MySQL backup: //p' "$directory/backup.txt")
test -n "$backup"
case "$backup" in ./*) backup="$current/${backup#./}";; /*) ;; *) exit 1;; esac
(cd "$current"; sh scripts/mysql-restore-drill.sh "$backup") > "$directory/restore.txt"
database=$(sed -n 's/^Restore validated in isolated database \(plan_restore_[0-9_]*\);.*/\1/p' "$directory/restore.txt")
case "$database" in plan_restore_[0-9]*_[0-9]*) ;; *) exit 1;; esac
case "$database" in *[!a-z0-9_]*) exit 1;; esac
user="drill_$(openssl rand -hex 8)"
password=$(openssl rand -hex 32)
printf "CREATE USER '%s'@'%%' IDENTIFIED BY '%s'; GRANT ALL ON %s.* TO '%s'@'%%';\n" "$user" "$password" "$database" "$user" | sql
tables=$(printf "SELECT table_name FROM information_schema.tables WHERE table_schema='%s' ORDER BY table_name;" "$database" | sql)
todo_columns=$(printf "SELECT column_name FROM information_schema.columns WHERE table_schema='%s' AND table_name='todos' ORDER BY ordinal_position;" "$database" | sql | paste -sd, -)
case "$todo_columns" in ''|*[!a-z0-9_,]*) exit 1;; esac
projection() { printf 'SELECT JSON_ARRAY(%s) FROM %s.todos ORDER BY 1;\n' "$todo_columns" "$database" | sql | sha256sum | cut -d ' ' -f 1; }
checksums() {
  for table in $tables; do
    case "$table" in *[!a-zA-Z0-9_]*) exit 1;; esac
    case "$table" in flyway_schema_history|todos) continue;; esac
    printf 'CHECKSUM TABLE %s.%s EXTENDED;\n' "$database" "$table" | sql
  done
}
projection > "$directory/todos-before.sha256"
checksums > "$directory/business-before.txt"
{
  printf 'SPRING_PROFILES_ACTIVE=mysql\nDB_USERNAME=%s\nDB_PASSWORD=%s\nJWT_SECRET=%s\n' "$user" "$password" "$(openssl rand -hex 48)"
  printf 'DB_URL=jdbc:mysql://mysql:3306/%s?serverTimezone=Asia/Shanghai&characterEncoding=utf8\n' "$database"
  printf 'JAVA_TOOL_OPTIONS=-Xms64m -Xmx256m -XX:MaxMetaspaceSize=128m -Duser.timezone=Asia/Shanghai\n'
  printf 'SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=2\nAI_ENABLED=false\nAI_API_KEY_FILE=\nREGISTRATION_ENABLED=false\nINVITATION_ENABLED=false\nPASSWORD_RESET_ENABLED=false\nEMAIL_VERIFICATION_ENABLED=false\nBOOTSTRAP_ADMIN_EMAIL=\nBOOTSTRAP_ADMIN_TOKEN_HASH=\nBOOTSTRAP_ADMIN_EXPIRES_AT=\n'
} > "$directory/app.env"
unset password
active=
cleanup() { if [ -n "$active" ]; then docker stop "$active" >/dev/null 2>&1 || true; fi; }
trap cleanup EXIT HUP INT TERM
boot() {
  active="$1"
  docker run -d --name "$active" --network plan-platform_default --memory 480m --cpus 0.75 --env-file "$directory/app.env" --label "plan.daily.drill=$run" "$2" >/dev/null
  healthy=false
  for attempt in $(seq 1 75); do
    if docker exec "$active" sh -c 'wget -q -O /dev/null http://127.0.0.1:8080/actuator/health/readiness' 2>/dev/null; then healthy=true; break; fi
    sleep 2
  done
  docker stop "$active" >/dev/null
  active=
  [ "$healthy" = true ] || { echo 'Isolated app readiness failed; production unchanged, drill retained'; exit 1; }
}
boot "plan-daily-$run" "$image"
test "$(printf 'SELECT MAX(CAST(version AS UNSIGNED)) FROM %s.flyway_schema_history WHERE success=1;' "$database" | sql)" = 19
test "$(printf "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='%s' AND table_name IN ('reminder_preferences','weekly_reviews');" "$database" | sql)" = 2
test "$(printf 'SELECT COUNT(*) FROM %s.todos WHERE completed_at IS NOT NULL;' "$database" | sql)" = 0
projection > "$directory/todos-after.sha256"
checksums > "$directory/business-after.txt"
cmp "$directory/todos-before.sha256" "$directory/todos-after.sha256"
cmp "$directory/business-before.txt" "$directory/business-after.txt"
boot "plan-daily-$run-rollback" "$old_image"
projection > "$directory/todos-rollback.sha256"
checksums > "$directory/business-rollback.txt"
cmp "$directory/todos-before.sha256" "$directory/todos-rollback.sha256"
cmp "$directory/business-before.txt" "$directory/business-rollback.txt"
test "$(printf "SELECT MAX(CAST(version AS UNSIGNED)) FROM plan_platform.flyway_schema_history WHERE success=1;" | sql)" = 18
printf 'PASS V19 snapshot migration and actual previous-image rollback; all original Todo columns and other business checksums unchanged.\nBackup: %s\nRestored DB: %s\nEvidence: %s\nPrevious image: %s\nNo production migration, model call, email or public port.\n' "$backup" "$database" "$directory" "$old_image" | tee "$directory/result.txt"
