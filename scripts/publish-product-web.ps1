param()
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $root
if(git status --porcelain){throw 'Commit the tested website changes first. Existing work is preserved.'}
$commit=(git rev-parse --short=12 HEAD).Trim()
$runtime=Join-Path $root '.desktop-tools/node22/node-v22.23.3-win-x64'
$env:PATH="$runtime;$env:PATH"
$env:PLAYWRIGHT_BROWSERS_PATH=Join-Path $root '.desktop-tools/playwright'
& npm.cmd --prefix web run build
if($LASTEXITCODE -ne 0){throw 'Website build failed. No upload.'}
$id='web-'+[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ').ToLower()+'-'+$commit
$stage=Join-Path $root "production-artifacts/product-web/$id"
if(Test-Path -LiteralPath $stage){throw 'Existing website artifacts retained.'}
New-Item -ItemType Directory -Path $stage | Out-Null
Copy-Item -LiteralPath web/dist -Destination "$stage/dist" -Recurse
Copy-Item -LiteralPath web/Dockerfile.prebuilt -Destination "$stage/Dockerfile"
Copy-Item -LiteralPath web/nginx.production.conf -Destination "$stage/nginx.conf"
Copy-Item -LiteralPath scripts/production-compose.sh,scripts/publish-product-web.sh -Destination $stage
$archive=Join-Path $stage 'website.tar.gz'
& tar -czf $archive -C $stage dist Dockerfile nginx.conf production-compose.sh publish-product-web.sh
if($LASTEXITCODE -ne 0){throw 'Website archive failed.'}
$hash=(Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLower()
$target='oyts@115.29.176.141'
$remote="/home/oyts/plan-platform/product-web/releases/$id"
& ssh -o BatchMode=yes -o ConnectTimeout=10 $target "test ! -e '$remote' && install -d -m 700 '$remote'"
if($LASTEXITCODE -ne 0){throw 'Remote staging failed. Current website unchanged.'}
& scp -q $archive "${target}:$remote/website.tar.gz"
if($LASTEXITCODE -ne 0){throw 'Upload failed. Current website unchanged.'}
$commands=@'
set -eu
cd '__REMOTE__'
test "$(sha256sum website.tar.gz | cut -d ' ' -f 1)" = '__HASH__'
tar -xzf website.tar.gz
sh -n publish-product-web.sh
sh publish-product-web.sh activate '__REMOTE__'
'@
$commands=$commands.Replace('__REMOTE__',$remote).Replace('__HASH__',$hash).Replace("`r`n","`n")
$commands | & ssh -o BatchMode=yes $target 'tr -d ''\015'' | sh -s'
if($LASTEXITCODE -ne 0){throw 'Website activation failed. Inspect retained rollback output and artifacts.'}
& "$runtime/node.exe" scripts/verify-product-web.mjs
if($LASTEXITCODE -ne 0){
    & ssh -o BatchMode=yes $target "sh '$remote/publish-product-web.sh' rollback '$remote'"
    if($LASTEXITCODE -ne 0){throw 'Browser acceptance and rollback failed; inspect retained website configurations.'}
    throw 'Browser acceptance failed; previous website restored. API and database retained.'
}
Write-Host "Website verified: $remote"
Write-Host 'No API/MySQL restart, native installation or download/update-source change.'
