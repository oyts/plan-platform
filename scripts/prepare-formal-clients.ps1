param([switch]$DirectMavenCentral, [ValidateSet('affected','full')][string]$TestScope = 'affected')
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $projectRoot
& powershell -NoProfile -ExecutionPolicy Bypass -File scripts/release-signing.ps1 -Action check
if ($LASTEXITCODE -ne 0) { throw 'Signing identity check failed. Never substitute test keys.' }
$version=(Get-Content web/package.json -Raw -Encoding UTF8 | ConvertFrom-Json).version
$run=[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')+'-'+[Guid]::NewGuid().ToString('N').Substring(0,8)
$base=Join-Path $projectRoot "production-artifacts/client-release/$version/$run"
foreach ($platform in @('windows','android')) {
    $arguments=@('-NoProfile','-ExecutionPolicy','Bypass','-File','scripts/release-signing.ps1','-Action',"build-$platform",'-ArtifactDirectory',"$base/$platform",'-TestScope',$TestScope)
    if ($platform -eq 'android' -and $DirectMavenCentral) { $arguments+='-DirectMavenCentral' }
    & powershell @arguments
    if ($LASTEXITCODE -ne 0) { throw "Formal $platform build failed; partial candidate retained: $base" }
}
$node=Join-Path $projectRoot '.desktop-tools/node22/node-v22.23.3-win-x64/node.exe'
& $node scripts/verify-formal-candidate.mjs "$base/android/PlanCabin-$version-release.apk" "$base/windows/Plan Cabin_${version}_x64-setup.exe" "$base/metadata"
if ($LASTEXITCODE -ne 0) { throw 'Formal artifact verification failed; do not install or publish this candidate.' }
Write-Host "Local signed candidate: $base"
Write-Host 'No installation, public upload, registration switch or filing submission was performed.'
