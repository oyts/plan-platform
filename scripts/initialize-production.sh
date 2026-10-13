#!/bin/sh
set -eu
umask 077
cd "$(dirname "$0")/.."
[ ! -e .env ] || { echo 'Existing production config preserved; initialization refused.'; exit 1; }
email=${1:?Pass the actual administrator email as the first argument}
case "$email" in *[!a-zA-Z0-9.@_+-]*|'') echo 'Invalid email'; exit 1;; esac
token=$(openssl rand -hex 32)
hash=$(printf '%s' "$token" | sha256sum | cut -d ' ' -f 1)
expires=$(date -u -d '+7 days' '+%Y-%m-%dT%H:%M:%SZ')
version=$(sed -n 's/^[[:space:]]*"version": "\([0-9][0-9.]*\)",/\1/p' web/package.json)
case "$version" in ''|*[!0-9.]*) echo 'Cannot determine release version'; exit 1;; esac
{
  printf 'PLAN_VERSION=%s\nPLAN_PORT=43140\n' "$version"
  printf 'DB_PASSWORD=%s\nMYSQL_ROOT_PASSWORD=%s\nJWT_SECRET=%s\n' "$(openssl rand -hex 32)" "$(openssl rand -hex 32)" "$(openssl rand -hex 48)"
  printf 'BOOTSTRAP_ADMIN_EMAIL=%s\nBOOTSTRAP_ADMIN_TOKEN_HASH=%s\nBOOTSTRAP_ADMIN_EXPIRES_AT=%s\n' "$email" "$hash" "$expires"
} > .env
printf 'https://plan.zijun701.cn/register?email=%s&invite=%s\n' "$email" "$token" > admin-registration.url
chmod 600 .env admin-registration.url
echo 'Restricted configuration created. Administrator chooses their own password using admin-registration.url; registration remains closed.'
