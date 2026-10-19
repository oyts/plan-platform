param(
    [ValidateSet('check', 'dev', 'build', 'test', 'regression', 'notification-probe')][string]$Action = 'check',
    [string]$UpdateUrl = '',
    [string]$PublicKeyPath = '',
    [string]$TestCertificatePath = '',
    [string]$TestVersion = '',
    [string]$ArtifactDirectory = '',
    [string]$NativeTestFilter = '',
    [switch]$IncludeLabTlsTest
)
$ErrorActionPreference = 'Stop'
$publicKeyFile = if ($PublicKeyPath) { (Resolve-Path -LiteralPath $PublicKeyPath).Path } else { '' }
$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $projectRoot
$portableNode = Join-Path $projectRoot '.desktop-tools/node22/node-v22.23.3-win-x64'
if (Test-Path -LiteralPath "$portableNode/node.exe") { $env:PATH = "$portableNode;$env:PATH" }
Remove-Item Env:PLAN_DESKTOP_TEST_UPDATE_CERT -ErrorAction SilentlyContinue
if ($TestCertificatePath) {
    if ($Action -ne 'build' -or $UpdateUrl -ne 'https://127.0.0.1:43133/windows/latest.json') { throw 'Test certificates are limited to local updater lab builds.' }
    $labOutputPrefix = [IO.Path]::GetFullPath((Join-Path $projectRoot 'client-update-artifacts/local')) + [IO.Path]::DirectorySeparatorChar
    if (-not $ArtifactDirectory -or -not [IO.Path]::GetFullPath($ArtifactDirectory).StartsWith($labOutputPrefix, [StringComparison]::OrdinalIgnoreCase)) { throw 'Lab builds must use an isolated local artifact directory.' }
}
if ($TestVersion -and (-not $TestCertificatePath -or $TestVersion -notmatch '^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$')) { throw 'Version overrides require a local test certificate and x.y.z version.' }

$portableCargo = Join-Path $projectRoot '.desktop-tools/cargo'
if (Test-Path -LiteralPath "$portableCargo/bin/cargo.exe") {
    $env:CARGO_HOME = $portableCargo
    $env:RUSTUP_HOME = Join-Path $projectRoot '.desktop-tools/rustup'
    $env:PATH = "$portableCargo/bin;$env:PATH"
}
if (-not (Get-Command cargo -ErrorAction SilentlyContinue)) { throw 'Rust is missing. Install Rust MSVC with rustup, then reopen the terminal.' }

$vcvars = $env:PLAN_DESKTOP_VCVARS
if (-not $vcvars) {
    $vswhere = 'C:/Program Files (x86)/Microsoft Visual Studio/Installer/vswhere.exe'
    if (Test-Path -LiteralPath $vswhere) {
        $vsPath = & $vswhere -latest -products '*' -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
        if ($vsPath) { $vcvars = Join-Path $vsPath 'VC/Auxiliary/Build/vcvars64.bat' }
    }
    if (-not $vcvars -and (Test-Path -LiteralPath 'E:/biancheng/Visual Studio/VC/Auxiliary/Build/vcvars64.bat')) {
        $vcvars = 'E:/biancheng/Visual Studio/VC/Auxiliary/Build/vcvars64.bat'
    }
}
if ($vcvars) {
    if (-not (Test-Path -LiteralPath $vcvars)) { throw 'PLAN_DESKTOP_VCVARS points to a missing vcvars64.bat.' }
    $nativeEnvironment = & cmd.exe /d /c "`"$vcvars`" >nul && set"
    if ($LASTEXITCODE -ne 0) { throw 'Visual Studio C++ environment initialization failed.' }
    foreach ($line in $nativeEnvironment) {
        if ($line -match '^([^=]+)=(.*)$') { [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process') }
    }
}
if (-not (Get-Command link.exe -ErrorAction SilentlyContinue)) { throw 'MSVC linker is missing. Install Desktop development with C++ in Visual Studio.' }
if (-not ($env:LIB -split ';' | Where-Object { Test-Path -LiteralPath (Join-Path $_ 'kernel32.lib') })) {
    throw 'Windows SDK x64 libraries are missing. Install Windows SDK Desktop C++ x64, then reopen the terminal.'
}
if (-not (Get-Command rc.exe -ErrorAction SilentlyContinue)) { throw 'Windows SDK resource compiler is missing.' }

# cmd.exe's `set` is line-oriented. Load multiline PEM only AFTER importing vcvars,
# otherwise the environment import silently truncates it to the BEGIN marker.
if ($TestCertificatePath -or $IncludeLabTlsTest) {
    $certificateFile = if ($TestCertificatePath) { $TestCertificatePath } else { Join-Path $projectRoot '.desktop-tools/update-lab/tls/ca.pem' }
    $env:PLAN_DESKTOP_TEST_UPDATE_CERT = Get-Content -LiteralPath $certificateFile -Raw
    if ($env:PLAN_DESKTOP_TEST_UPDATE_CERT -notmatch '(?s)\A\s*-----BEGIN CERTIFICATE-----\s+[A-Za-z0-9+/=\s]+-----END CERTIFICATE-----\s*\z') { throw 'Expected a complete PEM test certificate.' }
    if ($IncludeLabTlsTest) { $env:PLAN_DESKTOP_UPDATE_URL = 'https://127.0.0.1:43133/windows/latest.json' }
}

& rustc --version
& cargo --version
if ($Action -eq 'check') { Write-Host 'Rust and Microsoft C++ tools are ready.'; exit 0 }

Set-Location -LiteralPath (Join-Path $projectRoot 'web')
if (-not (Test-Path -LiteralPath 'node_modules/@tauri-apps/cli')) {
    & npm.cmd ci
    if ($LASTEXITCODE -ne 0) { throw 'npm ci failed.' }
}
if ($Action -eq 'test') {
    if ($NativeTestFilter) {
        & cargo test --manifest-path src-tauri/Cargo.toml --locked $NativeTestFilter
        if ($LASTEXITCODE -ne 0) { throw 'Selected native tests failed.' }
        exit 0
    }
    & npm.cmd run build:desktop-web
    if ($LASTEXITCODE -ne 0) { throw 'Desktop frontend build failed.' }
    & cargo test --manifest-path src-tauri/Cargo.toml --locked
    if ($LASTEXITCODE -ne 0) { throw 'Rust tests failed.' }
    if ($IncludeLabTlsTest) {
        $env:PLAN_UPDATE_LAB_CA_PATH = Join-Path $projectRoot '.desktop-tools/update-lab/tls/ca.pem'
        & cargo test --manifest-path src-tauri/Cargo.toml --locked local_https_requires -- --ignored
        if ($LASTEXITCODE -ne 0) { throw 'Native updater TLS integration failed.' }
    }
    & npm.cmd test
    if ($LASTEXITCODE -ne 0) { throw 'Web tests failed.' }
    exit 0
}
if ($Action -eq 'dev') {
    & npm.cmd run desktop:dev
    exit $LASTEXITCODE
}

if ($Action -eq 'regression' -or $Action -eq 'notification-probe') {
    if ($UpdateUrl -or $PublicKeyPath -or $TestCertificatePath -or $TestVersion) { throw 'Regression builds cannot enable updater lab overrides.' }
    foreach ($name in @('PLAN_DESKTOP_UPDATE_URL','PLAN_DESKTOP_UPDATE_PUBKEY','PLAN_DESKTOP_TEST_UPDATE_CERT','TAURI_CONFIG')) { Remove-Item "Env:$name" -ErrorAction SilentlyContinue }
    $regressionConfig = Join-Path $projectRoot 'web/src-tauri/tauri.regression.conf.json'
    if($Action -eq 'notification-probe') {
        & npx.cmd tauri build --debug --bundles nsis --config $regressionConfig --config (Join-Path $projectRoot 'web/src-tauri/tauri.reminder-probe.conf.json') --features native-regression -- --locked
    } else {
        & npx.cmd tauri build --debug --no-bundle --config $regressionConfig --features native-regression -- --locked
    }
    if ($LASTEXITCODE -ne 0) { throw 'Isolated desktop regression build failed.' }
    $regressionOutput = Join-Path $projectRoot '.desktop-tools/native-regression'
    New-Item -ItemType Directory -Force -Path $regressionOutput | Out-Null
    Copy-Item -LiteralPath 'src-tauri/target/debug/plan-platform-desktop.exe' -Destination (Join-Path $regressionOutput 'plan-regression.exe') -Force
    if($Action -eq 'notification-probe') {
        $probeOutput=Join-Path $projectRoot ('.desktop-tools/reminder-windows-installer/'+[Guid]::NewGuid().ToString('N'))
        New-Item -ItemType Directory -Path $probeOutput | Out-Null
        $probeInstaller=Join-Path $projectRoot ("web/src-tauri/target/debug/bundle/nsis/Plan Cabin Regression_${version}_x64-setup.exe")
        if(-not $version){$version=(Get-Content (Join-Path $projectRoot 'web/package.json') -Raw|ConvertFrom-Json).version;$probeInstaller=Join-Path $projectRoot ("web/src-tauri/target/debug/bundle/nsis/Plan Cabin Regression_${version}_x64-setup.exe")}
        Copy-Item -LiteralPath $probeInstaller -Destination (Join-Path $probeOutput 'PlanCabin-Notification-Test-setup.exe')
        Write-Host "Independent notification probe installer: $probeOutput"
    }
    exit 0
}

$tauriArguments = @('--ci')
if ($UpdateUrl -or $PublicKeyPath) {
    if (-not $UpdateUrl -or -not $PublicKeyPath) { throw 'UpdateUrl and PublicKeyPath must both be supplied.' }
    $parsedUpdateUrl = [Uri]$UpdateUrl
    if ($parsedUpdateUrl.Scheme -ne 'https' -or $parsedUpdateUrl.UserInfo -or $parsedUpdateUrl.Fragment) { throw 'UpdateUrl must use HTTPS without credentials or fragments.' }
    if (-not $env:TAURI_SIGNING_PRIVATE_KEY) { throw 'Set TAURI_SIGNING_PRIVATE_KEY to your private key path or content before enabling updates.' }
    $env:PLAN_DESKTOP_UPDATE_URL = $UpdateUrl
    $env:PLAN_DESKTOP_UPDATE_PUBKEY = (Get-Content -LiteralPath $publicKeyFile -Raw).Trim()
    $updaterConfig = @{ bundle = @{ createUpdaterArtifacts = $true }; plugins = @{ updater = @{ pubkey = $env:PLAN_DESKTOP_UPDATE_PUBKEY; endpoints = @($UpdateUrl) } } } | ConvertTo-Json -Depth 5 -Compress
    if ($TestVersion) {
        $configObject = $updaterConfig | ConvertFrom-Json
        $configObject | Add-Member -NotePropertyName version -NotePropertyValue $TestVersion
        $updaterConfig = $configObject | ConvertTo-Json -Depth 5 -Compress
    }
    $generatedConfigDir = Join-Path $projectRoot 'web/src-tauri/gen'
    New-Item -ItemType Directory -Force -Path $generatedConfigDir | Out-Null
    $generatedConfig = Join-Path $generatedConfigDir 'updater-build.json'
    [IO.File]::WriteAllText($generatedConfig, $updaterConfig, (New-Object Text.UTF8Encoding($false)))
    $tauriArguments += @('--config', $generatedConfig)
} else {
    Remove-Item Env:PLAN_DESKTOP_UPDATE_URL -ErrorAction SilentlyContinue
    Remove-Item Env:PLAN_DESKTOP_UPDATE_PUBKEY -ErrorAction SilentlyContinue
    Remove-Item Env:TAURI_CONFIG -ErrorAction SilentlyContinue
}
$tauriArguments += @('--', '--locked')
& npm.cmd run desktop:build -- @tauriArguments
if ($LASTEXITCODE -ne 0) { throw 'Desktop installer build failed.' }
$artifactDirectory = if ($ArtifactDirectory) { [IO.Path]::GetFullPath($ArtifactDirectory) } else { Join-Path $projectRoot 'desktop-artifacts' }
New-Item -ItemType Directory -Force -Path $artifactDirectory | Out-Null
$builtVersion = if ($TestVersion) { $TestVersion } else { (Get-Content src-tauri/tauri.conf.json -Raw -Encoding UTF8 | ConvertFrom-Json).version }
$installers = Get-ChildItem -LiteralPath 'src-tauri/target/release/bundle/nsis' -Filter "Plan Cabin_${builtVersion}_x64-setup.exe"
if (-not $installers) { throw 'Build succeeded but no Windows installer was found.' }
foreach ($installer in $installers) {
    Copy-Item -LiteralPath $installer.FullName -Destination $artifactDirectory -Force
    if ($UpdateUrl) {
        if (-not (Test-Path -LiteralPath ($installer.FullName + '.sig'))) { throw 'Signed update build is missing its signature.' }
        Copy-Item -LiteralPath ($installer.FullName + '.sig') -Destination $artifactDirectory -Force
    } else {
        # A manual-only build must not carry signatures or metadata from an earlier signed build.
        Remove-Item -LiteralPath (Join-Path $artifactDirectory ($installer.Name + '.sig')) -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath (Join-Path $artifactDirectory 'latest.json') -ErrorAction SilentlyContinue
    }
}
Get-ChildItem -LiteralPath $artifactDirectory | Select-Object Name, Length
Write-Host "Installer ready: $artifactDirectory"
