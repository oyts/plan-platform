param(
    [switch]$ForTesting,
    [string]$MinimumWindowsVersion = '0.0.0',
    [int]$MinimumAndroidVersionCode = 1,
    [string]$Notes = 'Plan Cabin client update'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $projectRoot
$portableNode = Join-Path $projectRoot '.desktop-tools/node22/node-v22.23.3-win-x64'
if (Test-Path -LiteralPath "$portableNode/node.exe") { $env:PATH = "$portableNode;$env:PATH" }
if (-not $env:JAVA_HOME -and (Test-Path 'E:/biancheng/JDK21')) { $env:JAVA_HOME = 'E:/biancheng/JDK21' }
if ($env:JAVA_HOME) { $env:PATH = "$env:JAVA_HOME/bin;$env:PATH" }
$version = (Get-Content web/package.json -Raw | ConvertFrom-Json).version
if ($version -notmatch '^\d+\.\d+\.\d+$') { throw 'Expected x.y.z version.' }
$installerName = "Plan Cabin_${version}_x64-setup.exe"
$variant = if ($ForTesting) { 'debug' } else { 'release' }
$apkName = "PlanCabin-$version-$variant.apk"
$installer = Join-Path $projectRoot "desktop-artifacts/$installerName"
$apk = Join-Path $projectRoot "android-artifacts/$apkName"
foreach ($file in @($installer, "$installer.sig", $apk)) {
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { throw "Missing artifact: $file. Build both clients first." }
}
$base = 'https://plan.zijun701.cn/client-updates'
$oldWindowsMinimum = $env:PLAN_DESKTOP_MINIMUM_VERSION
$oldAndroidMinimum = $env:PLAN_ANDROID_MINIMUM_VERSION_CODE
try {
    $env:PLAN_DESKTOP_MINIMUM_VERSION = $MinimumWindowsVersion
    $env:PLAN_ANDROID_MINIMUM_VERSION_CODE = [string]$MinimumAndroidVersionCode
    & node scripts/desktop-release.mjs $installer "$base/windows/releases/$version/$([uri]::EscapeDataString($installerName))" $Notes
    if ($LASTEXITCODE -ne 0) { throw 'Windows manifest failed.' }
    & node scripts/android-release.mjs $apk "$base/android/releases/$version/$apkName" $Notes
    if ($LASTEXITCODE -ne 0) { throw 'Android manifest failed.' }
} finally {
    $env:PLAN_DESKTOP_MINIMUM_VERSION = $oldWindowsMinimum
    $env:PLAN_ANDROID_MINIMUM_VERSION_CODE = $oldAndroidMinimum
}
$outputRoot = Join-Path $projectRoot "client-update-artifacts/$variant"
foreach ($platform in @('windows','android')) {
    New-Item -ItemType Directory -Force -Path "$outputRoot/$platform/releases/$version" | Out-Null
    $source = if ($platform -eq 'windows') { 'desktop-artifacts/latest.json' } else { 'android-artifacts/latest.json' }
    Copy-Item -LiteralPath $source -Destination "$outputRoot/$platform/latest.json" -Force
}
Copy-Item -LiteralPath $installer, "$installer.sig" -Destination "$outputRoot/windows/releases/$version/" -Force
Copy-Item -LiteralPath $apk -Destination "$outputRoot/android/releases/$version/" -Force
Write-Host "Prepared locally: $outputRoot"
Write-Host 'No upload or installation was performed. Publish installers first, latest.json last.'
if ($ForTesting) { Write-Warning 'TEST ONLY: debug APK and test update keys are not a public release identity.' }
