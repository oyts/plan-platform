#!/bin/sh
# User-owned cron entry; no sudo and no deletion of historical backups.
set -eu
# cron does not load the interactive user's PATH; ossutil is root-installed here.
PATH=/usr/local/bin:/usr/bin:/bin
export PATH
umask 077
# This helper is installed at the deployment root, beside the current symlink.
root=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
exec 9>"$root/.mysql-backup.lock"
flock -n 9 || { echo 'Another MySQL backup is running; this run skipped.'; exit 0; }
release=$(readlink -f "$root/current")
case "$release" in "$root"/release-*|"$root"/releases/*) ;; *) echo 'Unexpected current release'; exit 1;; esac
cd "$release"
record_result() {
  if [ -f "$root/production-operations.yml" ]; then
    python3 scripts/operations-status.py record-result --code "$1" --failed "$2" --output "$root/operations/failures.json" --group-readable
  fi
}
if sh scripts/mysql-backup.sh; then record_result BACKUP_FAILED no; else record_result BACKUP_FAILED yes; exit 1; fi
# Respect the same retained catalog configured for mysql-backup.sh.
set -a; . ./.env; set +a
backup_dir=${PLAN_MYSQL_BACKUP_DIR:-./backups}
backup=$(find "$backup_dir" -maxdepth 1 -type f -name 'plan_platform-mysql-*.sql.gz' | sort | tail -n 1)
[ -n "$backup" ] || { echo 'Backup not found'; exit 1; }
if sh scripts/mysql-oss-upload.sh "$backup"; then record_result OSS_FAILED no; else record_result OSS_FAILED yes; exit 1; fi
if [ -f "$root/production-operations.yml" ] && [ -f "$root/collect-operations.sh" ]; then
  sh "$root/collect-operations.sh" --verify-oss
fi
