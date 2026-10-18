#!/bin/sh
# Activate only the exact prebuilt 0.1.15 archive after its snapshot rehearsal.
set -eu
umask 077
directory=${1:?Managed verified release directory required}
drill=${2:?Successful private snapshot rehearsal directory required}
directory=$(realpath "$directory")
root=$(dirname "$(dirname "$directory")")
case "$directory" in /home/*/plan-platform/releases/0.1.15-*) ;; *) exit 1;; esac
test "$(dirname "$directory")" = "$root/releases"
drill=$(realpath "$drill")
test "$(dirname "$drill")" = "$root/release-drills"
test ! -e "$directory/activation-started"
test ! -e "$root/production-operations.yml" || { echo 'Operations overlay requires separate review'; exit 1; }
python3 - "$drill/report.json" <<'PY'
import json,sys
r=json.load(open(sys.argv[1]))
assert r['status']=='PASS' and r['migration']=='V19 -> V23'
assert r['previousImage']=='plan-platform-api:0.1.14'
PY
previous=$(readlink -f "$root/current")
test "$(dirname "$previous")" = "$root/releases"
test "$(docker inspect --format '{{.Config.Image}}' plan-platform-backend-1)" = plan-platform-api:0.1.14
test "$(sha256sum "$directory/release.tar.gz" | cut -d ' ' -f 1)" = 792a5d8a412d6e650833592043d87de9f31aa9a9e146e7b390bc374fa310b1b6
test "$(sha256sum "$directory/backend/api.jar" | cut -d ' ' -f 1)" = 6a68b763d7b05c48aaaae379eb7ba9eb313840ab28e79b4b3333039823690013
test ! -e "$directory/.env"
cp -p "$previous/.env" "$directory/.env"
chmod 600 "$directory/.env"
sed -i 's/^PLAN_VERSION=.*/PLAN_VERSION=0.1.15/' "$directory/.env"
grep -qx PLAN_VERSION=0.1.15 "$directory/.env"
printf '%s\n' "$previous" > "$directory/previous-release"
compose_from() { managed=$1; shift; sh "$directory/scripts/production-compose.sh" "$managed" "$@"; }
check_idle() {
  set -a; . "$previous/.env"; set +a
  active=$(docker exec -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" plan-platform-mysql-1 mysql -uroot -N plan_platform -e "SELECT COUNT(*) FROM ai_jobs WHERE state IN ('QUEUED','RUNNING');")
  test "$active" = 0 || { echo 'Active AI jobs: postpone upgrade'; exit 1; }
  # Compose must resolve each release's version from its own private env file.
  unset PLAN_VERSION
}
check_idle
(cd "$previous"; sh scripts/mysql-backup.sh) > "$directory/activation-backup.txt"
check_idle
date -u +%FT%TZ > "$directory/activation-started"
rollback_on_error() {
  code=$?
  trap - EXIT HUP INT TERM
  if [ "$code" -ne 0 ]; then
    echo 'Activation failed. Checking safe application rollback; database and backups retained.'
    if python3 "$directory/scripts/check-governance-rollback.py" --target-supports-generation no; then
      compose_from "$previous" up -d --no-deps backend web || echo 'Rollback needs inspection; do not remove retained data.'
    else
      echo 'Generation guard refused old binary; keep new data and inspect the retained release.'
    fi
  fi
  exit "$code"
}
trap rollback_on_error EXIT
trap 'exit 1' HUP INT TERM
compose_from "$directory" up -d --no-deps backend web
healthy=false
for attempt in $(seq 1 75); do
  if curl --max-time 5 -fsS http://127.0.0.1:43140/actuator/health 2>/dev/null | grep -q '"status":"UP"'; then healthy=true; break; fi
  sleep 2
done
test "$healthy" = true
curl --max-time 15 -fsS https://plan.zijun701.cn/api/health | python3 -c 'import json,sys;r=json.load(sys.stdin);assert r["version"]=="0.1.15" and r["ok"] and r["service"]=="plan-platform-api"'
curl --max-time 15 -fsS https://plan.zijun701.cn/api/auth/config | python3 -c 'import json,sys;r=json.load(sys.stdin);assert r["serverVersion"]=="0.1.15" and not r["registrationEnabled"] and r["invitationEnabled"] and r["passwordResetEnabled"]'
python3 - <<'PY'
import json,subprocess
r=json.loads(subprocess.check_output(['docker','inspect','plan-platform-backend-1']))[0]
e=dict(v.split('=',1) for v in r['Config']['Env'] if '=' in v)
assert e.get('AI_ENABLED')=='true' and e.get('AI_API_KEY_FILE')=='/run/secrets/deepseek-api-key'
assert e.get('OPERATIONS_ENABLED','false')=='false' and e.get('MEMBER_GOVERNANCE_ENABLED','false')=='false'
assert e.get('REFRESH_COOKIE_SECURE')=='true'
mounts=r['Mounts']
assert any(m['Destination']=='/run/secrets/deepseek-api-key' and not m['RW'] for m in mounts)
print('AI read-only key mount, secure cookie and disabled governance/operations retained; no model request.')
PY
test -L "$root/current"
test ! -e "$root/current.new"
ln -s "$directory" "$root/current.new"
mv -T "$root/current.new" "$root/current"
cp -p "$directory/scripts/daily-mysql-backup.sh" "$root/daily-mysql-backup.sh"
chmod 700 "$root/daily-mysql-backup.sh"
date -u +%FT%TZ > "$directory/activation-completed"
trap - EXIT HUP INT TERM
echo 'Web/API 0.1.15 healthy. Native sources unchanged pending installer acceptance.'
echo "Previous managed release retained: $previous"
