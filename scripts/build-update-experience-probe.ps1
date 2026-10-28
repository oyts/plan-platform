param([ValidateSet('bootstrap','target','both')][string]$Role='both')
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $projectRoot
$node=Join-Path $projectRoot '.desktop-tools/node22/node-v22.23.3-win-x64/node.exe'
& $node scripts/update-lab.mjs prepare
if($LASTEXITCODE -ne 0){throw 'Retain existing TLS identity; never bypass an expired certificate.'}
$key=Join-Path $projectRoot '.desktop-tools/test-signing/desktop-test.key'
if(-not(Test-Path -LiteralPath $key)){throw 'Existing test signing key required; production keys are not used.'}
$saved=@{}
foreach($name in @('TAURI_SIGNING_PRIVATE_KEY','TAURI_SIGNING_PRIVATE_KEY_PASSWORD')){$saved[$name]=[Environment]::GetEnvironmentVariable($name)}
try {
    $env:TAURI_SIGNING_PRIVATE_KEY=$key;$env:TAURI_SIGNING_PRIVATE_KEY_PASSWORD=''
    foreach($entry in @(@{role='bootstrap';version='0.1.24'},@{role='target';version='0.1.25'})){
        if($Role -ne 'both' -and $Role -ne $entry.role){continue}
        $out=Join-Path $projectRoot ('client-update-artifacts/local/stage5-pc-isolated/'+$entry.role)
        if(Test-Path -LiteralPath $out){throw "Immutable probe output already exists; inspect before rebuilding: $out"}
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/desktop.ps1 -Action update-probe -UpdateUrl 'https://127.0.0.1:43133/windows/latest.json' -PublicKeyPath "$key.pub" -TestCertificatePath (Join-Path $projectRoot '.desktop-tools/update-lab/tls/ca.pem') -TestVersion $entry.version -ArtifactDirectory $out
        if($LASTEXITCODE -ne 0){throw 'Isolated update probe build failed; partial outputs retained.'}
    }
}finally{foreach($name in $saved.Keys){[Environment]::SetEnvironmentVariable($name,$saved[$name],'Process')}}
Write-Host 'Local-only signed update probes ready. Nothing installed or published; production candidates unchanged.'
