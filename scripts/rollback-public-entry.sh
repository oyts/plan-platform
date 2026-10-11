#!/bin/sh
set -eu
[ "$(id -u)" = 0 ] || { echo 'Run with sudo'; exit 1; }
config=/etc/nginx/conf.d/plan-console-public.conf
backup=/etc/nginx/conf.d/plan-console-public.conf.before-plan-platform
[ -f "$backup" ] || { echo 'Rollback config missing'; exit 1; }
curl -fsS http://127.0.0.1:43127/api/health | grep -q 'plan-console-sync'
cp -p "$config" /etc/nginx/conf.d/plan-console-public.conf.before-rollback
cp -p "$backup" "$config"
nginx -t
systemctl reload nginx
echo 'Old Node entry restored. New MySQL data and containers were not deleted.'
