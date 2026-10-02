param(
    [ValidateSet('check', 'dev', 'build', 'test')][string]$Action = 'check',
    [string]$UpdateUrl = '',
    [string]$PublicKeyPath = ''
)
$ErrorActionPreference = 'Stop'
$publicKeyFile = if ($PublicKeyPath) { (Resolve-Path -LiteralPath $PublicKeyPath).Path } else { '' }
$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $projectRoot

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

& rustc --version
& cargo --version
if ($Action -eq 'check') { Write-Host 'Rust and Microsoft C++ tools are ready.'; exit 0 }

Set-Location -LiteralPath (Join-Path $projectRoot 'web')
if (-not (Test-Path -LiteralPath 'node_modules/@tauri-apps/cli')) {
    & npm.cmd ci
    if ($LASTEXITCODE -ne 0) { throw 'npm ci failed.' }
}
if ($Action -eq 'test') {
    & npm.cmd run build:desktop-web
    if ($LASTEXITCODE -ne 0) { throw 'Desktop frontend build failed.' }
    & cargo test --manifest-path src-tauri/Cargo.toml --locked
    if ($LASTEXITCODE -ne 0) { throw 'Rust tests failed.' }
    & npm.cmd test
    if ($LASTEXITCODE -ne 0) { throw 'Web tests failed.' }
    exit 0
}
if ($Action -eq 'dev') {
    & npm.cmd run desktop:dev
    exit $LASTEXITCODE
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
$artifactDirectory = Join-Path $projectRoot 'desktop-artifacts'
New-Item -ItemType Directory -Force -Path $artifactDirectory | Out-Null
$installers = Get-ChildItem -LiteralPath 'src-tauri/target/release/bundle/nsis' -Filter '*-setup.exe'
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
