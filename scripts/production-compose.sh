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
for overlay in "$root/production-telemetry.yml" "$root/production-web.yml" "$root/production-operations.yml" "$root/production-mail.yml" "$root/production-ai.yml" "$root/android-private/compose.yml" "$root/production-timezone.yml" "$root/client-public/compose.yml"; do
  # Website overlay version guard
  if [ -f "$overlay" ] && [ "$overlay" = "$root/production-web.yml" ]; then
    base_version=$(sed -n 's/^# plan-base-version: //p' "$overlay")
    case "$base_version" in ''|*[!0-9.]*) echo 'Invalid website base version'; exit 1;; esac
    grep -Fqx "PLAN_VERSION=$base_version" "$resolved/.env" || continue
  fi
  # End website overlay version guard
  if [ -f "$overlay" ]; then set -- -f "$overlay" "$@"; fi
done
docker compose --project-directory "$resolved" -p plan-platform -f "$resolved/docker-compose.production.yml" "$@"
