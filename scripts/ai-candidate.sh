#!/bin/sh
# Isolated ECS acceptance only. Never changes current, Nginx or the production DB.
set -eu
umask 077
action=${1:-}
[ "$#" -eq 1 ] || { echo 'Usage: sh scripts/ai-candidate.sh prepare|enable|disable|stop|status'; exit 1; }
cd "$(dirname "$0")/.."
directory=$(pwd -P)
root=$(dirname "$(dirname "$directory")")
case "$root" in /home/*/plan-platform|/opt/plan-platform) ;; *) echo 'Managed deployment root required'; exit 1;; esac
[ "$(dirname "$directory")" = "$root/ai-candidates" ] || { echo 'Use an isolated ai-candidates directory'; exit 1; }
command -v python3 >/dev/null
command -v openssl >/dev/null
command -v flock >/dev/null
exec 9>"$directory/.candidate.lock"
flock -n 9 || { echo 'Another candidate operation is running'; exit 1; }
image_version=$(python3 -c 'import json; print(json.load(open("web/package.json"))["version"])')
case "$image_version" in ''|*[!0-9.]*) exit 1;; esac

load() {
  [ -f .env ] && [ -f candidate.env ] && [ -f app.env ] || { echo 'Run prepare first'; exit 1; }
  set -a; . ./.env; . ./candidate.env; set +a
  case "$AI_CANDIDATE_NAME" in plan-ai-[0-9]*-[a-f0-9]*) ;; *) exit 1;; esac
  case "$AI_CANDIDATE_NAME" in *[!a-z0-9-]*) exit 1;; esac
  case "$PLAN_MYSQL_DATABASE" in plan_acceptance_[0-9]*_[a-f0-9]*) ;; *) exit 1;; esac
  case "$PLAN_MYSQL_DATABASE" in *[!a-z0-9_]*) exit 1;; esac
  [ "$PLAN_VERSION" = "$image_version" ] || { echo 'Candidate metadata changed; refuse reuse'; exit 1; }
}
owned_container() {
  actual=$(docker inspect -f '{{index .Config.Labels "plan.ai.candidate"}}' "$AI_CANDIDATE_NAME" 2>/dev/null || true)
  [ "$actual" = "$directory" ] || { echo 'Candidate container ownership mismatch'; exit 1; }
}
no_active_jobs() {
  pending=$(docker exec -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" "$AI_MYSQL_CONTAINER" mysql -uroot -N "$PLAN_MYSQL_DATABASE" -e "SELECT COUNT(*) FROM ai_jobs WHERE state IN ('QUEUED','RUNNING');")
  [ "$pending" = 0 ] || { echo 'Active jobs exist; wait before stopping or restarting'; exit 1; }
}
start() {
  enabled=$1
  set -- docker run -d --name "$AI_CANDIDATE_NAME" --label "plan.ai.candidate=$directory" \
    --network plan-platform_default --memory 480m --cpus 0.75 \
    --log-opt max-size=5m --log-opt max-file=2 --env-file "$directory/app.env" \
    -p 127.0.0.1:43144:8080 -e "AI_ENABLED=$enabled"
  if [ "$enabled" = true ]; then
    [ "$(id -u)" = 0 ] || { echo 'Enable requires sudo to validate the restricted secret'; exit 1; }
    secret=/etc/plan-platform-ai/deepseek-api-key
    [ ! -L /etc/plan-platform-ai ] && [ ! -L "$secret" ] && [ -f "$secret" ] || { echo 'Restricted regular key file required'; exit 1; }
    identity=$(docker image inspect -f '{{.Config.User}}' "plan-platform-api:$PLAN_VERSION")
    [ "$identity" = plan ] || { echo 'Unexpected image user'; exit 1; }
    uid=$(docker run --rm --entrypoint id "plan-platform-api:$PLAN_VERSION" -u)
    gid=$(docker run --rm --entrypoint id "plan-platform-api:$PLAN_VERSION" -g)
    [ "$uid" -gt 0 ] && [ "$(stat -c %u "$secret")" = "$uid" ] && [ "$(stat -c %g "$secret")" = "$gid" ] && [ "$(stat -c %a "$secret")" = 600 ] || { echo 'Key ownership/permissions mismatch'; exit 1; }
    set -- "$@" -e AI_API_KEY_FILE=/run/secrets/deepseek-api-key \
      --mount "type=bind,source=$secret,target=/run/secrets/deepseek-api-key,readonly"
  fi
  if docker container inspect "$AI_CANDIDATE_NAME" >/dev/null 2>&1; then
    owned_container
    # Do not interrupt or replay a paid request, including on configuration changes.
    no_active_jobs
    docker stop "$AI_CANDIDATE_NAME" >/dev/null
    docker rm "$AI_CANDIDATE_NAME" >/dev/null
  fi
  "$@" "plan-platform-api:$PLAN_VERSION" >/dev/null
  attempt=0
  until curl -fsS http://127.0.0.1:43144/actuator/health/readiness >/dev/null 2>&1; do
    attempt=$((attempt+1))
    [ "$attempt" -lt 45 ] || { echo 'Candidate unhealthy; production is unchanged. Candidate retained for diagnosis.'; exit 1; }
    sleep 2
  done
  curl -fsS http://127.0.0.1:43144/api/health
  printf '\nCandidate AI enabled=%s; loopback only. No provider request was submitted.\n' "$enabled"
}

case "$action" in
  prepare)
    [ ! -e .env ] && [ ! -e candidate.env ] && [ ! -e app.env ] && [ ! -e ai-acceptance-private.json ] || { echo 'Existing candidate state preserved; use a fresh directory'; exit 1; }
    available=$(awk '/MemAvailable:/ {print $2}' /proc/meminfo)
    [ "$available" -ge 650000 ] || { echo 'At least 650 MB available RAM required; no resources changed'; exit 1; }
    ! ss -lnt | grep -q ':43144[[:space:]]' || { echo 'Candidate port already in use'; exit 1; }
    current=$(readlink -f "$root/current")
    [ "$(dirname "$current")" = "$root/releases" ] && [ -f "$current/.env" ] || exit 1
    docker image inspect "plan-platform-api:$image_version" >/dev/null
    docker network inspect plan-platform_default >/dev/null
    # Required verified production backup before any candidate schema is created.
    (cd "$current"; sh scripts/mysql-backup.sh)
    set -a; . "$current/.env"; set +a
    AI_MYSQL_CONTAINER=$(docker ps --filter label=com.docker.compose.project=plan-platform --filter label=com.docker.compose.service=mysql --format '{{.ID}}')
    case "$AI_MYSQL_CONTAINER" in ''|*[!a-f0-9]*) echo 'Exactly one production MySQL required'; exit 1;; esac
    suffix="$(date -u +%Y%m%d%H%M%S)_$(openssl rand -hex 4)"
    PLAN_MYSQL_DATABASE="plan_acceptance_$suffix"
    AI_CANDIDATE_NAME="plan-ai-$(printf '%s' "$suffix" | tr _ -)"
    AI_DB_USER="ai_$(openssl rand -hex 8)"
    DB_PASSWORD=$(openssl rand -hex 32)
    JWT_SECRET=$(openssl rand -hex 48)
    PLAN_VERSION=$image_version
    export PLAN_MYSQL_DATABASE AI_CANDIDATE_NAME AI_MYSQL_CONTAINER AI_DB_USER DB_PASSWORD JWT_SECRET PLAN_VERSION MYSQL_ROOT_PASSWORD
    # Sensitive values are never command output; all files are 0600 in a 0700 directory.
    python3 - <<'PY'
import datetime, hashlib, json, os, secrets
e = os.environ
for name, keys in {
    '.env': ['PLAN_VERSION','PLAN_MYSQL_DATABASE','MYSQL_ROOT_PASSWORD','DB_PASSWORD','JWT_SECRET'],
    'candidate.env': ['AI_CANDIDATE_NAME','AI_MYSQL_CONTAINER','AI_DB_USER'],
}.items():
    with open(name, 'x') as f:
        for key in keys:
            f.write(key + '=' + e[key] + '\n')
token = secrets.token_hex(32)
account = {'email': 'ai-admin-' + secrets.token_hex(8) + '@example.test', 'password': secrets.token_hex(24)+'A1', 'invitationToken': token}
with open('ai-acceptance-private.json', 'x') as f:
    json.dump({'admin': account}, f)
values = {
    'SPRING_PROFILES_ACTIVE': 'mysql', 'DB_USERNAME': e['AI_DB_USER'], 'DB_PASSWORD':e['DB_PASSWORD'], 'JWT_SECRET':e['JWT_SECRET'],
    'DB_URL':'jdbc:mysql://mysql:3306/'+e['PLAN_MYSQL_DATABASE']+'?serverTimezone=Asia/Shanghai&characterEncoding=utf8',
    'REGISTRATION_ENABLED':'false', 'INVITATION_ENABLED':'true', 'REFRESH_COOKIE_SECURE':'true',
    'EMAIL_VERIFICATION_ENABLED':'false', 'PASSWORD_RESET_ENABLED':'false', 'ANDROID_UPDATE_ENABLED':'false',
    'APP_PUBLIC_URL':'http://127.0.0.1:43144', 'CORS_ALLOWED_ORIGINS':'http://127.0.0.1:43144',
    'BOOTSTRAP_ADMIN_EMAIL':account['email'], 'BOOTSTRAP_ADMIN_TOKEN_HASH':hashlib.sha256(token.encode()).hexdigest(),
    'BOOTSTRAP_ADMIN_EXPIRES_AT':(datetime.datetime.now(datetime.timezone.utc)+datetime.timedelta(hours=24)).isoformat(),
    'JAVA_TOOL_OPTIONS':'-Xms64m -Xmx240m -XX:MaxMetaspaceSize=128m -XX:+ExitOnOutOfMemoryError -Duser.timezone=Asia/Shanghai',
    'SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE':'3', 'AI_MAX_CONCURRENT':'1', 'AI_QUEUE_CAPACITY':'1',
    'AI_MODEL':'deepseek-flash', 'AI_MAX_OUTPUT_TOKENS':'2048', 'AI_TIMEOUT_SECONDS':'60',
}
with open('app.env', 'x') as f:
    for key, value in values.items(): f.write(key+'='+value+'\n')
PY
    docker exec -i -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" "$AI_MYSQL_CONTAINER" mysql -uroot <<SQL
CREATE DATABASE $PLAN_MYSQL_DATABASE CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER '$AI_DB_USER'@'%' IDENTIFIED BY '$DB_PASSWORD';
GRANT ALL PRIVILEGES ON $PLAN_MYSQL_DATABASE.* TO '$AI_DB_USER'@'%';
SQL
    load
    start false
    ;;
  enable|disable)
    load
    if [ "$action" = enable ]; then start true; else start false; fi
    ;;
  stop)
    load; owned_container; no_active_jobs; docker stop "$AI_CANDIDATE_NAME" >/dev/null
    echo 'Only this candidate stopped; database, credentials and evidence retained.'
    ;;
  status)
    load; owned_container
    docker inspect -f 'Candidate running={{.State.Running}} image={{.Config.Image}}' "$AI_CANDIDATE_NAME"
    ;;
  *) echo 'Unknown action'; exit 1;;
esac
