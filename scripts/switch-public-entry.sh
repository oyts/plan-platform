#!/bin/sh
# Run with sudo on the ECS after independent deployment and administrator authorization are ready.
set -eu
[ "$(id -u)" = 0 ] || { echo 'Run this script with sudo; do not grant unrestricted passwordless sudo.'; exit 1; }
config=/etc/nginx/conf.d/plan-console-public.conf
backup=/etc/nginx/conf.d/plan-console-public.conf.before-plan-platform
[ -f "$config" ] || { echo 'Expected host Nginx config not found'; exit 1; }
curl -fsS http://127.0.0.1:43140/actuator/health | grep -q '"status":"UP"'
curl -fsS http://127.0.0.1:43140/api/auth/config | grep -q '"registrationEnabled":false'
grep -q 'proxy_pass http://127.0.0.1:43127;' "$config" || { echo 'Unexpected current proxy; configuration preserved.'; exit 1; }
[ ! -e "$backup" ] || { echo 'Existing rollback config preserved; inspect before repeating.'; exit 1; }
cp -p "$config" "$backup"
sed -i 's@proxy_pass http://127.0.0.1:43127;@proxy_pass http://127.0.0.1:43140;@' "$config"
rollback() { cp -p "$backup" "$config"; nginx -t && systemctl reload nginx; echo 'Previous entry restored; new database retained.'; }
if ! nginx -t; then rollback; exit 1; fi
systemctl reload nginx || { rollback; exit 1; }
if ! curl -fsS https://plan.zijun701.cn/api/health | grep -q '"service":"plan-platform-api"'; then rollback; exit 1; fi
if ! curl -fsS https://plan.zijun701.cn/api/auth/config | grep -q '"todoEnabled":true'; then rollback; exit 1; fi
if ! curl -fsS https://plan.zijun701.cn/ | grep -q '<div id="app"'; then rollback; exit 1; fi
echo 'HTTPS entry now points to independent Spring Boot 0.1.4. Old containers and rollback config retained.'
echo 'Rollback: sudo sh scripts/rollback-public-entry.sh'
