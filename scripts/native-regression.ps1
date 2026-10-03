param(
    [ValidateSet('check','build','run','test')][string]$Action = 'check',
    [ValidateSet('both','windows','android')][string]$Platform = 'both',
    [string]$Serial = ''
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $projectRoot
$node = Join-Path $projectRoot '.desktop-tools/node22/node-v22.23.3-win-x64/node.exe'
if (-not (Test-Path -LiteralPath $node)) { $node = (Get-Command node -ErrorAction Stop).Source }
if (-not $env:JAVA_HOME -and (Test-Path -LiteralPath 'E:/biancheng/JDK21/bin/java.exe')) { $env:JAVA_HOME = 'E:/biancheng/JDK21' }
if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = Join-Path $projectRoot '.desktop-tools/android-sdk' }
$adb = Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'
$output = Join-Path $projectRoot '.desktop-tools/native-regression'
if (-not (Test-Path -LiteralPath 'web/node_modules/@playwright/test')) { throw 'Run npm ci in web first.' }
if ($Action -eq 'test') {
    & $node --test scripts/native-regression.test.mjs
    if ($LASTEXITCODE -ne 0) { throw 'Regression harness unit tests failed.' }
    exit 0
}
if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath "$env:JAVA_HOME/bin/java.exe")) { throw 'Set JAVA_HOME to Java 21.' }
if (-not (Test-Path -LiteralPath 'backend/target/plan-platform-api-0.1.0.jar')) { throw 'Build the API with Maven package first.' }
if ($Action -eq 'build') {
    $fingerprintCode = "import {sourceFingerprint} from './scripts/native-regression-support.mjs'; console.log(await sourceFingerprint(process.cwd()));"
    $sourceHash = & $node --input-type=module -e $fingerprintCode
    if ($LASTEXITCODE -ne 0) { throw 'Source fingerprint failed.' }
    # Child build processes have isolated environment variables. Existing lab/production
    # build environment, artifact directories and installed apps are never overwritten.
    & powershell -NoProfile -ExecutionPolicy Bypass -File scripts/desktop.ps1 -Action regression
    if ($LASTEXITCODE -ne 0) { throw 'Desktop regression build failed.' }
    $saved = @{}
    $variables = @('PLAN_NATIVE_REGRESSION','PLAN_ANDROID_BUILD_ROOT','PLAN_ANDROID_TEST_VERSION','PLAN_ANDROID_TEST_VERSION_CODE','PLAN_ANDROID_TEST_UPDATE_CERT','PLAN_ANDROID_UPDATE_URL')
    foreach ($name in $variables) { $saved[$name] = [Environment]::GetEnvironmentVariable($name, 'Process'); [Environment]::SetEnvironmentVariable($name, $null, 'Process') }
    try {
        $env:PLAN_NATIVE_REGRESSION = '1'
        $env:PLAN_ANDROID_BUILD_ROOT = Join-Path ([IO.Path]::GetTempPath()) 'plan-cabin-regression-android'
        & ./scripts/android.ps1 -Action build -ArtifactDirectory $output
        if ($LASTEXITCODE -ne 0) { throw 'Android regression build failed.' }
        Set-Location -LiteralPath $projectRoot
        $afterHash = & $node --input-type=module -e $fingerprintCode
        if ($LASTEXITCODE -ne 0 -or $afterHash -ne $sourceHash) { throw 'Client source changed during build; rebuild without concurrent edits.' }
        $version = (Get-Content -LiteralPath (Join-Path $projectRoot 'web/package.json') -Raw | ConvertFrom-Json).version
        $apk = Join-Path $output "PlanCabin-$version-debug.apk"
        # aapt on Windows cannot decode Unicode argv paths. Use an ASCII basename
        # from the artifact working directory, without moving the source tree.
        Set-Location -LiteralPath $output
        $badging = & "$env:ANDROID_HOME/build-tools/36.0.0/aapt.exe" dump badging ([IO.Path]::GetFileName($apk))
        if ($LASTEXITCODE -ne 0 -or ($badging -join "`n") -notmatch "package: name='cn.zijun701.plan.mobile.regression'") { throw 'Refusing a non-isolated APK.' }
        $manifest = @{ schema = 1; sourceSha256 = $sourceHash; apiSha256 = (Get-FileHash (Join-Path $projectRoot 'backend/target/plan-platform-api-0.1.0.jar') -Algorithm SHA256).Hash; androidPackage = 'cn.zijun701.plan.mobile.regression'; desktopIdentifier = 'cn.zijun701.plan.desktop.regression'; desktopSha256 = (Get-FileHash (Join-Path $output 'plan-regression.exe') -Algorithm SHA256).Hash; apkName = [IO.Path]::GetFileName($apk); apkSha256 = (Get-FileHash $apk -Algorithm SHA256).Hash }
        [IO.File]::WriteAllText((Join-Path $output 'build.json'), ($manifest | ConvertTo-Json), (New-Object Text.UTF8Encoding($false)))
    } finally {
        Set-Location -LiteralPath $projectRoot
        foreach ($name in $variables) { [Environment]::SetEnvironmentVariable($name, $saved[$name], 'Process') }
    }
    Write-Host 'Isolated debug clients built; nothing was installed or uninstalled.'
    exit 0
}
$arguments = @('scripts/native-regression.mjs', '--platform', $Platform)
if ($Serial) { $arguments += @('--serial', $Serial) }
if ($Action -eq 'check') { $arguments += '--check' }
& $node @arguments
if ($LASTEXITCODE -ne 0) { throw 'Native regression failed. See acceptance-results/native/latest.html. A missing phone is a failed preflight, not a passed or skipped device test.' }
