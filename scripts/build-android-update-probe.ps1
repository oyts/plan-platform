param([ValidateSet('bootstrap','target','both')][string]$Role='both')
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $root
if(-not(Test-Path -LiteralPath '.android-signing/debug.keystore')){throw 'Existing debug signing identity required; nothing regenerated.'}
$node=Join-Path $root '.desktop-tools/node22/node-v22.23.3-win-x64/node.exe'
& $node scripts/update-lab.mjs prepare
if($LASTEXITCODE -ne 0){throw 'Local TLS is not ready; do not bypass validation.'}
$saved=@{};$names=@('PLAN_NATIVE_REGRESSION','PLAN_ANDROID_UPDATE_PROBE','PLAN_PUBLIC_RELEASE','PLAN_ANDROID_TEST_VERSION','PLAN_ANDROID_TEST_VERSION_CODE','PLAN_ANDROID_TEST_UPDATE_CERT','PLAN_ANDROID_UPDATE_URL','PLAN_ANDROID_BUILD_ROOT')
foreach($name in $names){$saved[$name]=[Environment]::GetEnvironmentVariable($name)}
try{
 $env:PLAN_NATIVE_REGRESSION='1';$env:PLAN_ANDROID_UPDATE_PROBE='1';Remove-Item Env:PLAN_PUBLIC_RELEASE -ErrorAction SilentlyContinue
 $env:PLAN_ANDROID_TEST_UPDATE_CERT=Get-Content -LiteralPath '.desktop-tools/update-lab/tls/ca.pem' -Raw
 $env:PLAN_ANDROID_UPDATE_URL='https://127.0.0.1:43133/android/latest.json'
 $env:PLAN_ANDROID_BUILD_ROOT=Join-Path ([IO.Path]::GetTempPath()) 'plan-cabin-update-probe-android'
 foreach($entry in @(@{role='bootstrap';version='0.1.25';code='30'},@{role='target';version='0.1.26';code='31'})){
  if($Role -ne 'both' -and $Role -ne $entry.role){continue}
  $out=Join-Path $root ('client-update-artifacts/local/stage5-android/'+$entry.role)
  if(Test-Path -LiteralPath $out){throw "Retain immutable existing probe output: $out"}
  $env:PLAN_ANDROID_TEST_VERSION=$entry.version;$env:PLAN_ANDROID_TEST_VERSION_CODE=$entry.code
  & powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/android.ps1 -Action build -ArtifactDirectory $out -TestScope affected
  if($LASTEXITCODE -ne 0){throw 'Isolated Android probe build failed; partial outputs retained.'}
 }
 & $node scripts/prepare-android-update-probe.mjs
 if($LASTEXITCODE -ne 0){throw 'Probe pair metadata/signature validation failed.'}
}finally{foreach($name in $names){[Environment]::SetEnvironmentVariable($name,$saved[$name],'Process')}}
Write-Host 'Isolated Android update probe ready; nothing installed or published.'
