#!/bin/sh
# Administrator-operated mail activation. Never accepts an authorization code.
set -eu
umask 077
[ "$(id -u)" = 0 ] || { echo 'Run with sudo in the server terminal.'; exit 1; }
root=/home/oyts/plan-platform
current=$(readlink -f "$root/current")
test "$(dirname "$current")" = "$root/releases"
target=$root/production-mail.yml
mode=${1:-}
case "$mode" in
  test) source=$root/production-mail.candidate.yml ;;
  enable-reset)
    [ "${2:-}" = --received-test-email ] || { echo 'First receive the administrator test email, then pass --received-test-email.'; exit 1; }
    source=$target ;;
  *) echo 'Usage: activate-mail.sh test | enable-reset --received-test-email'; exit 1 ;;
esac
test -f "$source" && test ! -L "$source"
test -f /etc/plan-platform-mail/spring.mail.password
test "$(stat -c %u:%g:%a /etc/plan-platform-mail/spring.mail.password)" = 100:101:600
grep -q 'production-mail.yml' "$current/scripts/production-compose.sh" || { echo 'Upgrade the release before enabling mail.'; exit 1; }
backup=$root/mail-activation-$(date -u +%Y%m%dT%H%M%SZ)-$$
mkdir -m 700 "$backup"
if [ -f "$target" ]; then cp -p "$target" "$backup/previous.yml"; fi
if [ "$mode" = test ]; then cp "$source" "$backup/next.yml";
else sed 's/PASSWORD_RESET_ENABLED: "false"/PASSWORD_RESET_ENABLED: "true"/' "$source" > "$backup/next.yml"; fi
chown oyts:oyts "$backup/next.yml"
chmod 600 "$backup/next.yml"
restore() {
  if [ -f "$backup/previous.yml" ]; then cp -p "$backup/previous.yml" "$target";
  else mv "$target" "$backup/failed.yml"; fi
  sh "$current/scripts/production-compose.sh" "$current" up -d --no-deps backend >/dev/null 2>&1 || true
  echo 'Mail activation failed; previous mail configuration restored. Data and private files retained.'
}
cp -p "$backup/next.yml" "$target"
if ! sh "$current/scripts/production-compose.sh" "$current" config --quiet; then restore; exit 1; fi
if ! sh "$current/scripts/production-compose.sh" "$current" up -d --no-deps backend >/dev/null; then restore; exit 1; fi
healthy=false
for attempt in $(seq 1 45); do
  if curl -fsS --max-time 3 http://127.0.0.1:43140/api/health >/dev/null 2>&1; then healthy=true; break; fi
  sleep 2
done
if [ "$healthy" != true ]; then restore; exit 1; fi
echo 'Mail configuration activated; credentials not printed. Public registration remains closed.'
if [ "$mode" = test ]; then echo 'Password recovery is still disabled. Sign in as administrator and send a test email to your own account; verify its actual arrival first.';
else echo 'Password recovery enabled. Verify a real recovery request in your own mailbox. No verification mail was sent by this script.'; fi
