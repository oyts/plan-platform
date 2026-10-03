param(
    [ValidateSet('prepare','build','start','check','scenario','stop')][string]$Action = 'check',
    [ValidateSet('idle','optional','required','bad-hash','bad-signature','interrupted')][string]$Scenario = 'idle',
    [ValidateSet('all','windows','android')][string]$Platform = 'all'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $projectRoot
$node = [IO.Path]::GetFullPath((Join-Path $projectRoot '.desktop-tools/node22/node-v22.23.3-win-x64/node.exe'))
if (-not (Test-Path -LiteralPath $node)) { throw 'Portable Node.js 22 is required.' }
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'E:/biancheng/JDK21' }
$labDir = Join-Path $projectRoot '.desktop-tools/update-lab'
$outputRoot = Join-Path $projectRoot 'client-update-artifacts/local'
function Run-Lab([string[]]$Arguments) {
    & $node scripts/update-lab.mjs @Arguments
    if ($LASTEXITCODE -ne 0) { throw 'Local update lab operation failed. Keep the error above.' }
}
if ($Action -eq 'prepare') { Run-Lab @('prepare'); exit 0 }
if ($Action -eq 'scenario') { Run-Lab @('scenario', $Scenario); exit 0 }
if ($Action -eq 'check') { Run-Lab @('probe'); exit 0 }
if ($Action -eq 'build') {
    Run-Lab @('prepare')
    $certificate = Join-Path $labDir 'tls/ca.pem'
    $keyPath = Join-Path $projectRoot '.desktop-tools/test-signing/desktop-test.key'
    $publicKey = "$keyPath.pub"
    if (-not (Test-Path -LiteralPath $keyPath) -or -not (Test-Path -LiteralPath $publicKey)) { throw 'Existing test signing identity is required. Do not regenerate it for an installed client.' }
    $environmentNames = @('TAURI_SIGNING_PRIVATE_KEY','TAURI_SIGNING_PRIVATE_KEY_PASSWORD','PLAN_ANDROID_UPDATE_URL','PLAN_ANDROID_TEST_UPDATE_CERT','PLAN_ANDROID_TEST_VERSION','PLAN_ANDROID_TEST_VERSION_CODE')
    $savedEnvironment = @{}
    foreach ($name in $environmentNames) { $savedEnvironment[$name] = [Environment]::GetEnvironmentVariable($name) }
    try {
        $env:TAURI_SIGNING_PRIVATE_KEY = $keyPath
        $env:TAURI_SIGNING_PRIVATE_KEY_PASSWORD = ''
        $env:PLAN_ANDROID_UPDATE_URL = 'https://127.0.0.1:43133/android/latest.json'
        $env:PLAN_ANDROID_TEST_UPDATE_CERT = Get-Content -LiteralPath $certificate -Raw
        foreach ($role in @('bootstrap', 'target')) {
            $version = if ($role -eq 'bootstrap') { '0.1.1' } else { '0.1.2' }
            $code = if ($role -eq 'bootstrap') { '5' } else { '6' }
            $destination = Join-Path $outputRoot $role
            $env:PLAN_ANDROID_TEST_VERSION = $version
            $env:PLAN_ANDROID_TEST_VERSION_CODE = $code
            Write-Host "Building isolated $role clients ($version, Android code $code)..."
            # Child processes isolate build variables and native toolchain PATH changes.
            if ($Platform -ne 'android') {
                & powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/desktop.ps1 -Action build -UpdateUrl 'https://127.0.0.1:43133/windows/latest.json' -PublicKeyPath $publicKey -TestCertificatePath $certificate -TestVersion $version -ArtifactDirectory $destination
                if ($LASTEXITCODE -ne 0) { throw 'Windows lab build failed.' }
            }
            if ($Platform -ne 'windows') {
                & powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/android.ps1 -Action build -ArtifactDirectory $destination
                if ($LASTEXITCODE -ne 0) { throw 'Android lab build failed.' }
            }
        }
        Run-Lab @('register')
    } finally {
        foreach ($name in $environmentNames) { [Environment]::SetEnvironmentVariable($name, $savedEnvironment[$name], 'Process') }
    }
    Write-Host 'Local update test packages are ready. No client was installed or cloud service changed.'
    exit 0
}
$pidFile = Join-Path $labDir 'server.pid'
if ($Action -eq 'start') {
    if (Test-Path -LiteralPath $pidFile) {
        $existingId = [int](Get-Content -LiteralPath $pidFile)
        $existing = Get-CimInstance Win32_Process -Filter "ProcessId=$existingId" -ErrorAction SilentlyContinue
        if ($existing -and $existing.ExecutablePath -and [IO.Path]::GetFullPath($existing.ExecutablePath) -eq $node -and $existing.CommandLine -like '*scripts/update-lab.mjs serve*') { Run-Lab @('probe'); exit 0 }
        if ($existing) { throw 'PID file does not belong to the update lab. No process was changed.' }
    }
    if (-not (Test-Path -LiteralPath (Join-Path $labDir 'catalog.json'))) { throw 'Build and register the test packages first.' }
    $process = Start-Process -FilePath $node -ArgumentList 'scripts/update-lab.mjs serve' -WorkingDirectory $projectRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $labDir 'server.log') -RedirectStandardError (Join-Path $labDir 'server-error.log')
    Set-Content -LiteralPath $pidFile -Value $process.Id
    Start-Sleep -Milliseconds 1000
    Run-Lab @('probe')
} elseif ($Action -eq 'stop') {
    if (-not (Test-Path -LiteralPath $pidFile)) { Write-Host 'No managed update lab process.'; exit 0 }
    $targetId = [int](Get-Content -LiteralPath $pidFile)
    $target = Get-CimInstance Win32_Process -Filter "ProcessId=$targetId" -ErrorAction SilentlyContinue
    if ($target) {
        if (-not $target.ExecutablePath -or [IO.Path]::GetFullPath($target.ExecutablePath) -ne $node -or $target.CommandLine -notlike '*scripts/update-lab.mjs serve*') { throw 'Process identity mismatch. No process was stopped.' }
        Stop-Process -Id $targetId -ErrorAction Stop
    }
    Remove-Item -LiteralPath $pidFile
    Write-Host 'Only the local update lab was stopped. Business API is unchanged.'
}
