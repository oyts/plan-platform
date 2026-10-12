#!/bin/sh
# Run with sudo after independent deployment checks. Register the administrator
# through the verified HTTPS entry afterwards, not the HTTP SSH preview.
set -eu
[ "$(id -u)" = 0 ] || { echo 'Run this script with sudo; do not grant unrestricted passwordless sudo.'; exit 1; }
config=/etc/nginx/conf.d/plan-console-public.conf
backup=/etc/nginx/conf.d/plan-console-public.conf.before-plan-platform
[ -f "$config" ] || { echo 'Expected host Nginx config not found'; exit 1; }
curl -fsS http://127.0.0.1:43140/actuator/health | grep -q '"status":"UP"'
curl -fsS http://127.0.0.1:43140/api/auth/config | grep -q '"registrationEnabled":false'
grep -q 'proxy_pass http://127.0.0.1:43127;' "$config" || { echo 'Unexpected current proxy; configuration preserved.'; exit 1; }
[ ! -L "$backup" ] || { echo 'Symlink rollback config refused; no changes made.'; exit 1; }
if [ -e "$backup" ]; then
  cmp -s "$backup" "$config" || { echo 'Existing rollback config differs; preserved without changes.'; exit 1; }
  echo 'Reusing the verified identical rollback config from the previous attempt.'
else
  cp -p "$config" "$backup"
fi
sed -i 's@proxy_pass http://127.0.0.1:43127;@proxy_pass http://127.0.0.1:43140;@' "$config"
rollback() { cp -p "$backup" "$config"; nginx -t && systemctl reload nginx; echo 'Previous entry restored; new database retained.'; }
trap 'rollback; exit 1' HUP INT TERM
if ! nginx -t; then rollback; exit 1; fi
systemctl reload nginx || { rollback; exit 1; }

# Nginx reload returns before replacement workers accept connections. Do not
# mistake an immediate response from an old worker for a failed deployment.
last_failure='No HTTPS response yet'
check_https() {
  identity=$(curl --connect-timeout 2 --max-time 5 -fsS "$@" https://plan.zijun701.cn/api/health) || { last_failure='HTTPS health request failed'; return 1; }
  printf '%s' "$identity" | grep -q '"service":"plan-platform-api"' || { last_failure='HTTPS health still points to another service'; return 1; }
  capabilities=$(curl --connect-timeout 2 --max-time 5 -fsS "$@" https://plan.zijun701.cn/api/auth/config) || { last_failure='HTTPS auth config request failed'; return 1; }
  printf '%s' "$capabilities" | grep -q '"todoEnabled":true' || { last_failure='HTTPS auth config lacks TODO capability'; return 1; }
  printf '%s' "$capabilities" | grep -q '"registrationEnabled":false' || { last_failure='Public registration is unexpectedly enabled'; return 1; }
  homepage=$(curl --connect-timeout 2 --max-time 5 -fsS "$@" https://plan.zijun701.cn/) || { last_failure='HTTPS homepage request failed'; return 1; }
  printf '%s' "$homepage" | grep -q '<div id="app"' || { last_failure='HTTPS homepage is not the new application'; return 1; }
}

ready=false
for attempt in $(seq 1 15); do
  # Keep normal certificate and hostname verification; only route to the local
  # Nginx listener to exclude public DNS/NAT from the reload readiness check.
  if check_https --resolve plan.zijun701.cn:443:127.0.0.1; then ready=true; break; fi
  echo "Waiting for Nginx HTTPS entry ($attempt/15): $last_failure"
  sleep 1
done
if [ "$ready" != true ]; then echo "Cutover failed: $last_failure"; rollback; exit 1; fi

# Also verify the real public domain, not only the loopback HTTPS listener.
ready=false
for attempt in $(seq 1 5); do
  if check_https; then ready=true; break; fi
  echo "Waiting for public HTTPS entry ($attempt/5): $last_failure"
  sleep 1
done
if [ "$ready" != true ]; then echo "Public cutover failed: $last_failure"; rollback; exit 1; fi
trap - HUP INT TERM
echo "HTTPS entry now points to Spring Boot: $identity"
echo 'Old containers and rollback config retained. Use the original HTTPS administrator registration link.'
echo 'Rollback: sudo sh scripts/rollback-public-entry.sh'
