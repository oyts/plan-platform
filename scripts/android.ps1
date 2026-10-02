param([ValidateSet('check','build','test','release')][string]$Action = 'build')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $projectRoot
$portableNode = Join-Path $projectRoot '.desktop-tools/node22/node-v22.23.3-win-x64'
if (Test-Path "$portableNode/node.exe") { $env:PATH = "$portableNode;$env:PATH" }
$nodeMajor = [int]((& node --version).Trim().TrimStart('v').Split('.')[0])
if ($nodeMajor -lt 22) { throw 'Android builds require Node.js 22+.' }
if (-not $env:JAVA_HOME -and (Test-Path 'E:/biancheng/JDK21/bin/java.exe')) { $env:JAVA_HOME = 'E:/biancheng/JDK21' }
if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME/bin/java.exe")) { throw 'Set JAVA_HOME to a JDK 21 installation.' }
$env:PATH = "$env:JAVA_HOME/bin;$env:PATH"
if (-not $env:ANDROID_HOME) {
    $localSdk = Join-Path $projectRoot '.desktop-tools/android-sdk'
    if (Test-Path $localSdk) { $env:ANDROID_HOME = $localSdk }
}
if (-not $env:ANDROID_HOME -or -not (Test-Path "$env:ANDROID_HOME/platforms/android-36/android.jar")) { throw 'Install Android SDK platform 36 and build-tools 36.0.0, then set ANDROID_HOME.' }
if (-not $env:GRADLE_USER_HOME) {
    $oldGradleCache = Join-Path $projectRoot '.desktop-tools/gradle'
    if ($projectRoot -match '[^\x00-\x7F]') {
        # Gradle's Windows test-worker bootstrap cannot reliably read a Unicode cache path.
        # Keep the source project in place; only use an ASCII temporary build cache.
        $env:GRADLE_USER_HOME = Join-Path ([IO.Path]::GetTempPath()) 'plan-cabin-gradle'
        New-Item -ItemType Directory -Force -Path $env:GRADLE_USER_HOME | Out-Null
        foreach ($folder in @('caches','wrapper')) {
            $destination = Join-Path $env:GRADLE_USER_HOME $folder
            $source = Join-Path $oldGradleCache $folder
            if ((Test-Path $source) -and -not (Test-Path $destination)) {
                # robocopy supports the long dependency paths that Windows PowerShell 5 Copy-Item cannot read.
                & robocopy $source $destination /E /NFL /NDL /NJH /NJS /NP /R:1 /W:1 | Out-Null
                if ($LASTEXITCODE -ge 8) { throw 'Unable to copy the generated Gradle cache to the ASCII build directory.' }
            }
        }
    } else { $env:GRADLE_USER_HOME = $oldGradleCache }
}
if ($env:GRADLE_USER_HOME -match '[^\x00-\x7F]') { throw 'Use an ASCII GRADLE_USER_HOME path for Windows test workers.' }
if ($projectRoot -match '[^\x00-\x7F]' -and -not $env:PLAN_ANDROID_BUILD_ROOT) {
    $env:PLAN_ANDROID_BUILD_ROOT = Join-Path ([IO.Path]::GetTempPath()) 'plan-cabin-android-build'
}
if ($env:PLAN_ANDROID_BUILD_ROOT -match '[^\x00-\x7F]') { throw 'Use an ASCII PLAN_ANDROID_BUILD_ROOT path for Windows test workers.' }
# JVM does not automatically honor the proxy variables used by npm/curl.
# Mirror the existing process proxy without changing the system network settings.
if ($env:HTTPS_PROXY) {
    $proxyUri = [Uri]$env:HTTPS_PROXY
    if ($proxyUri.Scheme -eq 'http' -and -not $proxyUri.UserInfo) {
        $env:JAVA_TOOL_OPTIONS = "$env:JAVA_TOOL_OPTIONS -Dhttps.proxyHost=$($proxyUri.Host) -Dhttps.proxyPort=$($proxyUri.Port) -Dhttp.proxyHost=$($proxyUri.Host) -Dhttp.proxyPort=$($proxyUri.Port)".Trim()
        $bypassHosts = ($env:NO_PROXY -split ',' | ForEach-Object { $_.Trim() -replace '^\.', '*.' } | Where-Object { $_ -and $_ -notmatch '/' }) -join '|'
        if ($bypassHosts) { $env:JAVA_TOOL_OPTIONS += " -Dhttp.nonProxyHosts=$bypassHosts" }
    }
}
Write-Host "Node and Java are ready. Android SDK: $env:ANDROID_HOME"
if ($Action -eq 'check') { exit 0 }
if ($Action -eq 'release') {
    foreach ($name in @('PLAN_ANDROID_KEYSTORE','PLAN_ANDROID_STORE_PASSWORD','PLAN_ANDROID_KEY_ALIAS','PLAN_ANDROID_KEY_PASSWORD')) {
        if (-not [Environment]::GetEnvironmentVariable($name)) { throw "Set $name before building a signed release APK. Never commit signing passwords." }
    }
    if (-not (Test-Path -LiteralPath $env:PLAN_ANDROID_KEYSTORE)) { throw 'Release signing keystore was not found.' }
    $env:PLAN_ANDROID_KEYSTORE = (Resolve-Path -LiteralPath $env:PLAN_ANDROID_KEYSTORE).Path
}
$debugDir = Join-Path $projectRoot '.android-signing'
New-Item -ItemType Directory -Force -Path $debugDir | Out-Null
$debugKey = Join-Path $debugDir 'debug.keystore'
if (-not (Test-Path $debugKey)) {
    & keytool -genkeypair -keystore $debugKey -storepass android -keypass android -alias androiddebugkey -dname 'CN=Android Debug,O=PlanCabin,C=CN' -keyalg RSA -keysize 2048 -validity 10000
    if ($LASTEXITCODE -ne 0) { throw 'Debug signing key generation failed.' }
}
Set-Location (Join-Path $projectRoot 'web')
$version = (Get-Content 'package.json' -Raw | ConvertFrom-Json).version
$androidGradle = Get-Content 'android/app/build.gradle' -Raw
if ($androidGradle -notmatch ('versionName\s+"' + [regex]::Escape($version) + '"')) { throw 'Android versionName differs from web/package.json. Align the release versions first.' }
if (-not (Test-Path 'node_modules/@capacitor/cli')) { & npm.cmd ci; if ($LASTEXITCODE -ne 0) { throw 'npm ci failed.' } }
& npm.cmd test
if ($LASTEXITCODE -ne 0) { throw 'Frontend tests failed.' }
& npm.cmd run build:android-web
if ($LASTEXITCODE -ne 0) { throw 'Android frontend build failed.' }
& npx.cmd cap sync android
if ($LASTEXITCODE -ne 0) { throw 'Android sync failed.' }
Set-Location android
$tasks = @(':app:testDebugUnitTest')
if ($Action -ne 'test') { $tasks += $(if ($Action -eq 'release') { ':app:assembleRelease' } else { ':app:assembleDebug' }) }
& ./gradlew.bat --no-daemon --console=plain @tasks
if ($LASTEXITCODE -ne 0) { throw 'Android build or unit tests failed. Keep the error above.' }
if ($Action -eq 'test') { exit 0 }
$variant = if ($Action -eq 'release') { 'release' } else { 'debug' }
$appBuildRoot = if ($env:PLAN_ANDROID_BUILD_ROOT) { Join-Path $env:PLAN_ANDROID_BUILD_ROOT 'app' } else { 'app/build' }
$apk = Join-Path $appBuildRoot "outputs/apk/$variant/app-$variant.apk"
if (-not (Test-Path $apk)) { throw 'The expected signed APK was not generated.' }
$artifactDir = Join-Path $projectRoot 'android-artifacts'
New-Item -ItemType Directory -Force -Path $artifactDir | Out-Null
$destination = Join-Path $artifactDir "PlanCabin-$version-$variant.apk"
Copy-Item -LiteralPath $apk -Destination $destination -Force
& "$env:ANDROID_HOME/build-tools/36.0.0/apksigner.bat" verify --verbose $destination
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
Get-FileHash -LiteralPath $destination -Algorithm SHA256 | Format-List
Write-Host "APK ready: $destination"
