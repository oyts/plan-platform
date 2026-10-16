param([string]$SshTarget = 'oyts@115.29.176.141', [string]$RemoteRoot = '/home/oyts/plan-platform', [ValidateSet('affected','full')][string]$TestScope = 'affected')
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath (Split-Path $PSScriptRoot -Parent)
if ($SshTarget -notmatch '^[a-zA-Z0-9_-]+@[a-zA-Z0-9.-]+$' -or $RemoteRoot -notmatch '^/(home/[a-zA-Z0-9_-]+|opt)/plan-platform$') { throw 'Unexpected SSH target or deployment root.' }
if (git status --porcelain) { throw 'Commit the tested release first. Existing changes are preserved.' }
$version = (Get-Content web/package.json -Raw | ConvertFrom-Json).version
if ($version -notmatch '^\d+\.\d+\.\d+$') { throw 'Expected x.y.z release version.' }
$commit = (git rev-parse --short=12 HEAD).Trim()
& ./scripts/prepare-cloud-release.ps1 -TestScope $TestScope
if ($LASTEXITCODE -ne 0) { throw 'Release verification failed; no upload performed.' }
$archive = Join-Path (Get-Location) "production-artifacts/plan-platform-$version.tar.gz"
if (-not (Test-Path -LiteralPath $archive)) { throw 'Run prepare-cloud-release.bat first.' }
$hash = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLower()
$remote = "$RemoteRoot/releases/$version-$commit"
& ssh -o BatchMode=yes -o ConnectTimeout=10 $SshTarget "test -f '$RemoteRoot/.env' && test ! -e '$remote' && install -d -m 700 '$remote'"
if ($LASTEXITCODE -ne 0) { throw 'Restricted .env missing or this release directory exists. No existing deployment changed.' }
& scp -q $archive "${SshTarget}:$remote/release.tar.gz"
if ($LASTEXITCODE -ne 0) { throw 'Upload failed; existing service unchanged.' }
$commands = @'
set -eu
previous=$(readlink -f '__ROOT__/current')
case "$previous" in '__ROOT__/releases/'*) ;; *) echo 'Verified previous release required'; exit 1;; esac
test -f "$previous/docker-compose.production.yml"
test -f "$previous/.env"
cd '__REMOTE__'
printf '%s\n' "$previous" > previous-release
test "$(sha256sum release.tar.gz | cut -d ' ' -f 1)" = '__HASH__'
tar -xzf release.tar.gz
cp -p '__ROOT__/.env' .env
chmod 600 .env
sed -i 's/^PLAN_VERSION=.*/PLAN_VERSION=__VERSION__/' .env
sh scripts/mysql-backup.sh
docker build --pull=false -t plan-platform-api:__VERSION__ backend
docker build --pull=false -t plan-platform-web:__VERSION__ web
# Retain separately approved static and timezone overlays for upgrade AND rollback.
compose_from() {
  directory=$1
  shift
  sh '__REMOTE__/scripts/production-compose.sh' "$directory" "$@"
}
# Never restart an application while a known paid request is queued/running.
# Read only counts; do not expose credentials or user conversation contents.
set -a; . ./.env; set +a
mysql_container=$(compose_from '__REMOTE__' ps -q mysql)
test -n "$mysql_container"
pending_ai=$(docker exec -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" "$mysql_container" mysql -uroot -N plan_platform -e "SELECT COUNT(*) FROM ai_jobs WHERE state IN ('QUEUED','RUNNING');")
test "$pending_ai" = 0 || { echo 'Active AI jobs found; deployment postponed, production unchanged.'; exit 1; }
rollback_on_error() {
  code=$?
  trap - EXIT HUP INT TERM
  if [ "$code" -ne 0 ]; then
    echo 'Upgrade validation failed; restoring previous application images, retaining database and backup.'
    compose_from "$previous" up -d || echo 'Rollback command failed; inspect retained release directories.'
  fi
  exit "$code"
}
trap rollback_on_error EXIT
trap 'exit 1' HUP INT TERM
compose_from '__REMOTE__' up -d
for attempt in $(seq 1 45); do
  if curl --max-time 5 -fsS http://127.0.0.1:43140/actuator/health | grep -q '"status":"UP"'; then break; fi
  sleep 2
done
curl --max-time 10 -fsS http://127.0.0.1:43140/api/health | grep -q '"version":"__VERSION__"'
curl --max-time 10 -fsS http://127.0.0.1:43140/api/auth/config | grep -q '"registrationEnabled":false'
curl --max-time 10 -fsS https://plan.zijun701.cn/api/health | grep -q '"version":"__VERSION__"'
curl --max-time 10 -fsS https://plan.zijun701.cn/api/auth/config | grep -q '"invitationEnabled":true'
cp -p scripts/daily-mysql-backup.sh '__ROOT__/daily-mysql-backup.sh'
chmod 700 '__ROOT__/daily-mysql-backup.sh'
test ! -e '__ROOT__/current' || test -L '__ROOT__/current'
test ! -e '__ROOT__/current.new'
ln -s '__REMOTE__' '__ROOT__/current.new'
mv -T '__ROOT__/current.new' '__ROOT__/current'
trap - EXIT HUP INT TERM
'@
$commands = $commands.Replace('__REMOTE__', $remote).Replace('__ROOT__', $RemoteRoot).Replace('__HASH__', $hash).Replace('__VERSION__', $version).Replace("`r`n", "`n")
# Send shell code through stdin. Windows PowerShell's native argument quoting
# otherwise strips nested JSON/shell quotes when passing a multiline argument.
$commands | & ssh -o BatchMode=yes $SshTarget 'tr -d ''\015'' | sh -s'
if ($LASTEXITCODE -ne 0) { throw 'Upgrade failed; inspect rollback output and retained new release/database. Do not repeat initialization or delete data.' }
& ssh -o BatchMode=yes $SshTarget "readlink -f '$RemoteRoot/current' | grep -Fx '$remote'"
if ($LASTEXITCODE -ne 0) { throw 'Current release pointer validation failed. No public cutover performed.' }
Write-Host "Existing Spring Boot service upgraded: $remote. HTTPS health validated; business acceptance still required. Do not repeat initial public-entry switching."
Write-Host 'No formal client was installed; no old container, image or volume was deleted.'
