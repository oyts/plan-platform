#!/bin/sh
set -eu
umask 077
cd "$(dirname "$0")/.."
backup=${1:?Pass a backup created by mysql-backup.sh}
gzip -t "$backup"
(cd "$(dirname "$backup")"; sha256sum -c "$(basename "$backup").sha256")
set -a; . ./.env; set +a
container=$(docker compose -p "${PLAN_COMPOSE_PROJECT:-plan-platform}" -f docker-compose.production.yml ps -q mysql)
source_database=${PLAN_MYSQL_DATABASE:-plan_platform}
case "$source_database" in plan_platform|plan_acceptance_[0-9]*) ;; *) echo 'Unexpected source database'; exit 1;; esac
case "$source_database" in *[!a-z0-9_]*) exit 1;; esac
database="plan_restore_$(date -u +%Y%m%d_%H%M%S)"
docker exec -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" "$container" mysql -uroot -e "CREATE DATABASE $database CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
temporary=$(mktemp)
trap 'rm -f "$temporary"' EXIT
gzip -dc "$backup" > "$temporary"
if grep -iE '^[[:space:]]*(USE[[:space:]]|CREATE[[:space:]]+DATABASE|DROP[[:space:]]+DATABASE)' "$temporary"; then
  echo 'Database-switching SQL refused; only single-database dumps from mysql-backup.sh are supported.'; exit 1
fi
docker exec -i -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" "$container" mysql -uroot "$database" < "$temporary"
tables=$(docker exec -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" "$container" mysql -uroot -N -e "SELECT table_name FROM information_schema.tables WHERE table_schema='$source_database' ORDER BY table_name;")
for table in $tables; do
  case "$table" in *[!a-zA-Z0-9_]*) exit 1;; esac
  original=$(docker exec -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" "$container" mysql -uroot -N -e "CHECKSUM TABLE $source_database.$table EXTENDED;" | awk '{print $2}')
  restored=$(docker exec -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" "$container" mysql -uroot -N -e "CHECKSUM TABLE $database.$table EXTENDED;" | awk '{print $2}')
  [ "$original" != NULL ] && [ "$original" = "$restored" ] || { echo "Restore checksum mismatch: $table; drill database retained"; exit 1; }
done
echo "Restore validated in isolated database $database; live database unchanged. Drill database retained for inspection."
if [ -n "${PLAN_OPERATIONS_DIRECTORY:-}" ]; then
  # Evidence is recorded only after every table comparison above succeeded.
  python3 scripts/operations-status.py record-drill --backup "$(readlink -f "$backup")" --output "$PLAN_OPERATIONS_DIRECTORY/drill.json" --group-readable
fi
