param([Parameter(Mandatory=$true)][string]$StageDirectory)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $root
$stage=[IO.Path]::GetFullPath($StageDirectory)
$prefix=[IO.Path]::GetFullPath((Join-Path $root 'production-artifacts/invited-android'))+[IO.Path]::DirectorySeparatorChar
if(-not $stage.StartsWith($prefix,[StringComparison]::OrdinalIgnoreCase)){throw 'Expected private Android staging directory.'}
$manifest=Get-Content -LiteralPath "$stage/current.json" -Raw | ConvertFrom-Json
if($manifest.version -notmatch '^\d+\.\d+\.\d+$' -or [int]$manifest.versionCode -lt 10){throw 'Invalid release metadata.'}
$apk="$stage/PlanCabin-$($manifest.version)-release.apk"
if((Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLower() -ne $manifest.sha256 -or (Get-Item -LiteralPath $apk).Length -ne $manifest.size){throw 'Staged APK hash/size mismatch.'}
$run='android-'+$manifest.versionCode+'-'+[Guid]::NewGuid().ToString('N').Substring(0,8)
$remote="/home/oyts/plan-platform/android-private/staging/$run"
$target='oyts@115.29.176.141'
$archive="$stage/upload.tar.gz"
if(Test-Path -LiteralPath $archive){throw 'Existing upload preserved; prepare a fresh staging directory.'}
& tar -czf $archive -C $stage current.json release.json "PlanCabin-$($manifest.version)-release.apk" publish.sh
if($LASTEXITCODE -ne 0){throw 'Archive failed.'}
$hash=(Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLower()
& ssh -o BatchMode=yes -o ConnectTimeout=10 $target "test ! -e '$remote' && install -d -m 700 '$remote'"
if($LASTEXITCODE -ne 0){throw 'Private staging creation failed; current service unchanged.'}
& scp -q $archive "${target}:$remote/upload.tar.gz"
if($LASTEXITCODE -ne 0){throw 'Private upload failed; current source unchanged.'}
$command=@'
set -eu
cd '__STAGE__'
test "$(sha256sum upload.tar.gz | cut -d ' ' -f 1)" = '__HASH__'
tar -xzf upload.tar.gz
sh publish.sh '__STAGE__' '__CODE__' '__VERSION__'
'@
$command=$command.Replace('__STAGE__',$remote).Replace('__HASH__',$hash).Replace('__CODE__',[string]$manifest.versionCode).Replace('__VERSION__',[string]$manifest.version).Replace("`r`n","`n")
$command | & ssh -o BatchMode=yes $target 'tr -d ''\015'' | sh -s'
if($LASTEXITCODE -ne 0){throw 'Private source activation failed; review retained rollback files.'}
$node=Join-Path $root '.desktop-tools/node22/node-v22.23.3-win-x64/node.exe'
& $node scripts/verify-invited-android-release.mjs $stage
if($LASTEXITCODE -ne 0){
    & ssh -o BatchMode=yes $target "sh '$remote/rollback.sh'"
    if($LASTEXITCODE -ne 0){throw 'Protected verification AND rollback failed; inspect backend before retrying.'}
    throw 'Protected verification failed; previous private source restored, database retained.'
}
Write-Host "Verified protected Android source: $($manifest.version). No public APK or registration switch."
