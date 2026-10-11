#!/bin/sh
set -eu
umask 077
cd "$(dirname "$0")/.."
set -a; . ./.env; set +a
case "${PLAN_COMPOSE_PROJECT:-plan-platform}" in *[!a-zA-Z0-9_-]*|'') exit 1;; esac
backup_dir=${PLAN_MYSQL_BACKUP_DIR:-./backups}
mkdir -p "$backup_dir"
container=$(docker compose -p "${PLAN_COMPOSE_PROJECT:-plan-platform}" -f docker-compose.production.yml ps -q mysql)
[ -n "$container" ] || { echo 'MySQL container not found'; exit 1; }
database=${PLAN_MYSQL_DATABASE:-plan_platform}
case "$database" in plan_platform|plan_acceptance_[0-9]*) ;; *) echo 'Unexpected database'; exit 1;; esac
case "$database" in *[!a-z0-9_]*) exit 1;; esac
target="$backup_dir/$database-mysql-$(date -u +%Y%m%d-%H%M%S).sql.gz"
temporary="$target.sql.tmp"
trap 'rm -f "$temporary"' EXIT
# Write only a new file; neither backup nor restore ever overwrites a live database.
docker exec -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" "$container" mysqldump -uroot --single-transaction --no-tablespaces --set-gtid-purged=OFF --routines --triggers "$database" > "$temporary"
[ -s "$temporary" ] || { echo 'Empty dump refused'; exit 1; }
gzip -c "$temporary" > "$target"
gzip -t "$target"
(cd "$backup_dir"; sha256sum "$(basename "$target")" > "$(basename "$target").sha256")
echo "Verified MySQL backup: $target"
