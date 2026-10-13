#!/bin/sh
# Preserve all approved overlays without shell eval or printing .env values.
set -eu
directory=${1:?Verified release directory required}
shift
resolved=$(realpath "$directory")
root=$(dirname "$(dirname "$resolved")")
case "$root" in /home/*/plan-platform|/opt/plan-platform) ;; *) echo 'Expected a managed deployment root'; exit 1;; esac
test "$(dirname "$resolved")" = "$root/releases" || { echo 'Expected a managed release directory'; exit 1; }
test -f "$resolved/docker-compose.production.yml"
# Prepend in reverse order, retaining the caller's command as separate arguments.
for overlay in "$root/android-private/compose.yml" "$root/production-timezone.yml" "$root/client-public/compose.yml"; do
  if [ -f "$overlay" ]; then set -- -f "$overlay" "$@"; fi
done
docker compose --project-directory "$resolved" -p plan-platform -f "$resolved/docker-compose.production.yml" "$@"
