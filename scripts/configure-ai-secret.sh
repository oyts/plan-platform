#!/bin/sh
# Run interactively on the ECS host. Never pass the key as an argument or environment value.
set -eu
umask 077
[ "$#" -eq 0 ] || { echo 'Do not pass keys or arguments; use hidden interactive input only.' >&2; exit 1; }
[ "$(id -u)" -eq 0 ] || { echo 'Run with sudo on the ECS host.' >&2; exit 1; }
[ -t 0 ] || { echo 'An interactive terminal is required for hidden input.' >&2; exit 1; }
command -v docker >/dev/null
command -v flock >/dev/null
project=${PLAN_COMPOSE_PROJECT:-plan-platform}
container=$(docker ps --filter "label=com.docker.compose.project=$project" --filter 'label=com.docker.compose.service=backend' --format '{{.ID}}')
case "$container" in ''|*[!a-f0-9]*) echo 'Exactly one running backend container is required; no changes made.' >&2; exit 1;; esac
backend_uid=$(docker exec "$container" id -u)
backend_gid=$(docker exec "$container" id -g)
case "$backend_uid:$backend_gid" in *[!0-9:]*) echo 'Invalid backend identity.' >&2; exit 1;; esac
[ "$backend_uid" -gt 0 ] || { echo 'Backend must run as a non-root user.' >&2; exit 1; }
directory=/etc/plan-platform-ai
target=$directory/deepseek-api-key
[ ! -L "$directory" ] || { echo 'Refusing a symlink directory.' >&2; exit 1; }
install -d -m 700 -o root -g root "$directory"
exec 9>"$directory/setup.lock"
flock -n 9 || { echo 'Another secret setup is running.' >&2; exit 1; }
if [ -e "$target" ] || [ -L "$target" ]; then echo 'A key file already exists. It was NOT read or overwritten. Arrange an explicit rotation separately.' >&2; exit 1; fi
temporary=$(mktemp "$directory/.key-XXXXXXXX")
tty_state=$(stty -g)
cleanup() { stty "$tty_state" 2>/dev/null || :; unset ai_key; rm -f -- "$temporary"; }
trap cleanup EXIT
trap 'exit 130' HUP INT TERM
printf 'Paste DeepSeek official API key (hidden), then Enter: ' >&2
stty -echo
IFS= read -r ai_key || exit 1
stty "$tty_state"
printf '\n' >&2
case "$ai_key" in ''|*[!A-Za-z0-9_-]*) echo 'Invalid key format; nothing saved.' >&2; exit 1;; esac
[ "${#ai_key}" -ge 16 ] && [ "${#ai_key}" -le 4096 ] || { echo 'Invalid key length; nothing saved.' >&2; exit 1; }
printf '%s\n' "$ai_key" >"$temporary"
unset ai_key
install -m 600 -o "$backend_uid" -g "$backend_gid" "$temporary" "$target"
echo 'Restricted key file saved. Its value was not printed.'
echo 'AI_SECRET_HOST_FILE=/etc/plan-platform-ai/deepseek-api-key'
echo 'AI is NOT enabled, no service was restarted, and no API request was made.'
echo 'Wait for the isolated release verification before mounting/enabling this key.'
