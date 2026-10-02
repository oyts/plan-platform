param([ValidateSet('check','install','instrument')][string]$Action = 'check', [string]$Serial, [ValidateRange(1024,65535)][int]$Port = 43129)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $projectRoot
if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = Join-Path $projectRoot '.desktop-tools/android-sdk' }
$adb = Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'
if (-not (Test-Path -LiteralPath $adb)) { throw 'Set ANDROID_HOME to your Android SDK with platform-tools.' }
$listing = & $adb devices
if ($LASTEXITCODE -ne 0) { throw 'Cannot query Android devices.' }
$devices = @($listing | Where-Object { $_ -match '^\S+\s+device$' } | ForEach-Object { ($_ -split '\s+')[0] })
if ($Serial) {
    if ($Serial -notin $devices) { throw 'The selected device is not connected and authorized.' }
} elseif ($devices.Count -eq 1) { $Serial = $devices[0] }
else { $listing; throw 'Connect and authorize exactly one device, or supply -Serial. No device was changed.' }
Write-Host 'Authorized device found.'
if ($Action -eq 'check') { exit 0 }
if ($Action -eq 'install') {
    $clientVersion = (Get-Content -LiteralPath (Join-Path $projectRoot 'web/package.json') -Raw | ConvertFrom-Json).version
    $apk = Join-Path $projectRoot "android-artifacts/PlanCabin-$clientVersion-debug.apk"
    if (-not (Test-Path -LiteralPath $apk)) { throw 'Build the test APK first with build-android.bat.' }
    if (-not (Get-NetTCPConnection -State Listen -LocalAddress '127.0.0.1' -LocalPort $Port -ErrorAction SilentlyContinue)) {
        throw "Start the local test API on 127.0.0.1:$Port before preparing the phone. No APK was installed."
    }
    & "$env:ANDROID_HOME/build-tools/36.0.0/apksigner.bat" verify $apk
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
    & $adb -s $Serial reverse "tcp:$Port" "tcp:$Port"
    if ($LASTEXITCODE -ne 0) { throw 'USB port forwarding failed.' }
    & $adb -s $Serial install -r $apk
    if ($LASTEXITCODE -ne 0) { throw 'APK installation failed. Do not uninstall an existing client to bypass a signature mismatch without backing up its data.' }
    Write-Host "Test APK installed. Open Plan Cabin and configure http://127.0.0.1:$Port. The USB forwarding lasts only while connected."
} else {
    # Build only the instrumentation APK. Do not let Gradle manage device
    # installation/uninstallation of the user's main application.
    if (-not $env:JAVA_HOME -and (Test-Path 'E:/biancheng/JDK21/bin/java.exe')) { $env:JAVA_HOME='E:/biancheng/JDK21' }
    if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to Java 21.' }
    $env:PATH="$env:JAVA_HOME/bin;$env:PATH"
    $env:GRADLE_USER_HOME=Join-Path ([IO.Path]::GetTempPath()) 'plan-cabin-gradle'
    $env:PLAN_ANDROID_BUILD_ROOT=Join-Path ([IO.Path]::GetTempPath()) 'plan-cabin-android-build'
    $env:ANDROID_SERIAL=$Serial
    $installed = & $adb -s $Serial shell pm path cn.zijun701.plan.mobile
    if ($installed -notmatch '^package:') { throw 'Install the current test APK with prepare-android-test.bat first.' }
    Set-Location web/android
    & ./gradlew.bat --no-daemon --console=plain :app:assembleDebugAndroidTest
    if ($LASTEXITCODE -ne 0) { throw 'Android device test APK build failed.' }
    $testApk = Join-Path $env:PLAN_ANDROID_BUILD_ROOT 'app/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
    & $adb -s $Serial install -r $testApk
    if ($LASTEXITCODE -ne 0) { throw 'Device test APK installation failed; no application was uninstalled.' }
    $result = & $adb -s $Serial shell am instrument -w -r cn.zijun701.plan.mobile.test/androidx.test.runner.AndroidJUnitRunner
    $result
    if ($LASTEXITCODE -ne 0 -or ($result -join "`n") -notmatch 'OK\s*\(\d+ tests?\)') { throw 'Android Keystore device tests failed. Keep the output above.' }
    Write-Host 'Device Keystore instrumentation passed; manual UI/share/back/upgrade acceptance is still required.'
}
