# Narrow compatible server patch: use the exact JAR that passed the mail tests.
# No native/web rebuild, schema change, actual invitation, or secret output.
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $root
if(git status --porcelain){throw 'Commit the tested patch first. Existing work is preserved.'}
$revision=(git rev-parse HEAD).Trim()
$short=$revision.Substring(0,12)
$version=(Get-Content web/package.json -Raw | ConvertFrom-Json).version
if($version -ne '0.1.13'){throw 'This compatible mail-only patch is scoped to 0.1.13.'}
$build=Join-Path $root 'backend/target-invitation-copy'
$jar=Join-Path $build 'plan-platform-api.jar'
if(-not (Test-Path -LiteralPath $jar)){throw 'Run the selected mail tests and package first.'}
foreach($name in @('InvitationMailCopyIntegrationTest','AccountRecoveryIntegrationTest')){
    [xml]$report=Get-Content -LiteralPath "$build/surefire-reports/TEST-com.oyts.planplatform.auth.$name.xml" -Raw
    if([int]$report.testsuite.tests -lt 4 -or [int]$report.testsuite.failures -ne 0 -or [int]$report.testsuite.errors -ne 0 -or [int]$report.testsuite.skipped -ne 0){throw 'Required focused mail test did not pass.'}
}
$latestSource=Get-ChildItem backend/src/main -Recurse -File | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
if($latestSource.LastWriteTimeUtc -gt (Get-Item -LiteralPath $jar).LastWriteTimeUtc){throw 'API source changed after the tested JAR was built.'}
$id="$version-mailcopy-$short"
$stage=Join-Path $root "production-artifacts/invitation-mail-copy/$id"
if(Test-Path -LiteralPath $stage){throw 'Previous patch artifacts retained; inspect before retrying.'}
New-Item -ItemType Directory -Path "$stage/backend" | Out-Null
Copy-Item -LiteralPath $jar -Destination "$stage/backend/api.jar"
Copy-Item -LiteralPath backend/Dockerfile.prebuilt -Destination "$stage/backend/Dockerfile"
$jarHash=(Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLower()
@{version=$version;revision=$revision;jarSha256=$jarHash;scope='Invitation CC only; no automatic real email';tests=10} | ConvertTo-Json | Set-Content -LiteralPath "$stage/metadata.json" -Encoding UTF8
& tar -czf "$stage/patch.tar.gz" -C $stage backend metadata.json
if($LASTEXITCODE -ne 0){throw 'Archive failed. No upload.'}
$hash=(Get-FileHash -LiteralPath "$stage/patch.tar.gz" -Algorithm SHA256).Hash.ToLower()
$target='oyts@115.29.176.141'
$remote="/home/oyts/plan-platform/releases/$id"
& ssh -o BatchMode=yes -o ConnectTimeout=10 $target "test ! -e '$remote' && install -d -m 700 '$remote'"
if($LASTEXITCODE -ne 0){throw 'Remote preparation failed. Existing deployment unchanged.'}
& scp -q "$stage/patch.tar.gz" "${target}:$remote/patch.tar.gz"
if($LASTEXITCODE -ne 0){throw 'Upload failed. Existing deployment unchanged.'}
$commands=@'
set -eu
root=/home/oyts/plan-platform
next='__REMOTE__'
previous=$(readlink -f "$root/current")
test "$(dirname "$previous")" = "$root/releases"
cd "$next"
test "$(sha256sum patch.tar.gz | cut -d ' ' -f 1)" = '__ARCHIVE_HASH__'
tar -xzf patch.tar.gz
test "$(sha256sum backend/api.jar | cut -d ' ' -f 1)" = '__JAR_HASH__'
cp -p "$previous/.env" .env
chmod 600 .env
cp -a "$previous/scripts" scripts
cp -p "$previous/docker-compose.production.yml" docker-compose.production.yml
if [ -f "$previous/docker-compose.ai-secrets.yml" ]; then cp -p "$previous/docker-compose.ai-secrets.yml" docker-compose.ai-secrets.yml; fi
test "$(grep -c 'image: plan-platform-api:' docker-compose.production.yml)" = 1
sed -i 's|image: plan-platform-api:.*|image: plan-platform-api:__ID__|' docker-compose.production.yml
printf '%s\n' "$previous" > previous-release
mysql_before=$(docker inspect --format='{{.Id}}' plan-platform-mysql-1)
web_before=$(docker inspect --format='{{.Id}}' plan-platform-web-1)
compose() { directory=$1; shift; sh "$next/scripts/production-compose.sh" "$directory" "$@"; }
compose "$next" config --quiet
docker build --pull=false --label org.opencontainers.image.revision=__REVISION__ -t plan-platform-api:__ID__ backend
sh "$previous/scripts/mysql-backup.sh"
(
  . "$previous/.env"
  pending=$(docker exec -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" plan-platform-mysql-1 mysql -uroot -N plan_platform -e "SELECT COUNT(*) FROM ai_jobs WHERE state IN ('QUEUED','RUNNING');")
  test "$pending" = 0 || { echo 'Active AI requests; compatible patch deferred.'; exit 1; }
)
test "$(readlink -f "$root/current")" = "$previous"
reload_web() { docker exec plan-platform-web-1 nginx -t && docker exec plan-platform-web-1 nginx -s reload; }
healthy() {
  for attempt in $(seq 1 45); do
    if [ "$(docker inspect --format='{{.State.Health.Status}}' plan-platform-backend-1)" = healthy ]; then return 0; fi
    sleep 2
  done
  return 1
}
failed() {
  trap - EXIT HUP INT TERM
  echo 'Patch validation failed; restoring previous API image, retaining database and backups.'
  compose "$previous" up -d --no-deps backend && healthy && reload_web || echo 'API rollback needs review; all files and data retained.'
  exit 1
}
trap failed EXIT
trap 'exit 1' HUP INT TERM
compose "$next" up -d --no-deps backend
healthy
reload_web
curl -fsS --max-time 10 https://plan.zijun701.cn/api/health | grep -q '"version":"0.1.13"'
config=$(curl -fsS --max-time 10 https://plan.zijun701.cn/api/auth/config)
printf '%s' "$config" | grep -q '"registrationEnabled":false'
printf '%s' "$config" | grep -q '"passwordResetEnabled":true'
test "$(docker inspect --format='{{.Id}}' plan-platform-mysql-1)" = "$mysql_before"
test "$(docker inspect --format='{{.Id}}' plan-platform-web-1)" = "$web_before"
test "$(docker inspect --format='{{.Config.Image}}' plan-platform-backend-1)" = 'plan-platform-api:__ID__'
test "$(docker inspect --format='{{index .Config.Labels "org.opencontainers.image.revision"}}' plan-platform-backend-1)" = '__REVISION__'
test ! -e "$root/current.mailcopy-new"
ln -s "$next" "$root/current.mailcopy-new"
mv -Tf "$root/current.mailcopy-new" "$root/current"
trap - EXIT HUP INT TERM
echo 'Compatible invitation CC patch active. Website and MySQL containers unchanged; public registration still closed.'
echo 'No real invitation or paid AI request was sent by deployment.'
echo "Previous release retained: $previous"
echo "Patched release: $next"
'@
$commands=$commands.Replace('__REMOTE__',$remote).Replace('__ARCHIVE_HASH__',$hash).Replace('__JAR_HASH__',$jarHash).Replace('__ID__',$id).Replace('__REVISION__',$revision).Replace("`r`n","`n")
$commands | & ssh -o BatchMode=yes $target 'tr -d ''\015'' | sh -s'
if($LASTEXITCODE -ne 0){throw 'Backend patch deployment failed. Review retained rollback output; do not reinitialize or delete data.'}
Write-Host "Server mail patch verified: $remote; administrator inbox receipt still requires an intentional invitation."
