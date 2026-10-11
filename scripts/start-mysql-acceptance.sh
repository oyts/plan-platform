#!/bin/sh
set -eu
umask 077
cd "$(dirname "$0")/.."
[ -z "$(docker ps -aq --filter name=^plan-platform-acceptance-api$)" ] || { echo 'Acceptance container already exists; no existing process changed.'; exit 1; }
set -a; . ./.env; set +a
database=plan_acceptance_20261012
test_password=$(openssl rand -hex 32)
container=$(docker compose -p plan-platform -f docker-compose.production.yml ps -q mysql)
docker exec -i -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" "$container" mysql -uroot <<SQL
CREATE DATABASE IF NOT EXISTS $database CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'plan_acceptance'@'%' IDENTIFIED BY '$test_password';
ALTER USER 'plan_acceptance'@'%' IDENTIFIED BY '$test_password';
GRANT ALL ON $database.* TO 'plan_acceptance'@'%';
SQL
{
  printf 'SPRING_PROFILES_ACTIVE=mysql\nDB_USERNAME=plan_acceptance\nDB_PASSWORD=%s\nJWT_SECRET=%s\n' "$test_password" "$(openssl rand -hex 48)"
  printf 'DB_URL=jdbc:mysql://mysql:3306/%s?serverTimezone=Asia/Shanghai\n' "$database"
  printf 'REGISTRATION_ENABLED=true\nREFRESH_COOKIE_SECURE=true\nBOOTSTRAP_ADMIN_EMAIL=\nJAVA_TOOL_OPTIONS=-Xms64m -Xmx256m -XX:MaxMetaspaceSize=160m\n'
} > acceptance.env
chmod 600 acceptance.env
docker run -d --name plan-platform-acceptance-api --network plan-platform_default --memory 512m --env-file acceptance.env -p 127.0.0.1:43142:8080 "plan-platform-api:$PLAN_VERSION"
echo 'Acceptance API listens only on loopback 43142; synthetic data only.'
