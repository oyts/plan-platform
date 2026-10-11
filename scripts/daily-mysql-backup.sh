#!/bin/sh
# User-owned cron entry; no sudo and no deletion of historical backups.
set -eu
umask 077
# This helper is installed at the deployment root, beside the current symlink.
root=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
exec 9>"$root/.mysql-backup.lock"
flock -n 9 || { echo 'Another MySQL backup is running; this run skipped.'; exit 0; }
release=$(readlink -f "$root/current")
case "$release" in "$root"/release-*|"$root"/releases/*) ;; *) echo 'Unexpected current release'; exit 1;; esac
cd "$release"
sh scripts/mysql-backup.sh
backup=$(find ./backups -maxdepth 1 -type f -name 'plan_platform-mysql-*.sql.gz' | sort | tail -n 1)
[ -n "$backup" ] || { echo 'Backup not found'; exit 1; }
sh scripts/mysql-oss-upload.sh "$backup"
