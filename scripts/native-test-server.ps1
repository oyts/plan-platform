param(
    [ValidateRange(1024, 65535)][int]$Port = 43129,
    [ValidatePattern('^[a-z0-9][a-z0-9-]{0,63}$')][string]$DatabaseName = 'desktop-local'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath (Join-Path $projectRoot 'backend')
# Probe by binding rather than hiding network-query permission errors. Do this
# before opening a database so an occupied port cannot trigger a migration.
$portProbe = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, $Port)
try {
    $portProbe.Server.ExclusiveAddressUse = $true
    $portProbe.Start()
} catch {
    throw "Cannot bind 127.0.0.1:$Port. The port may be occupied or unavailable; no database was opened. Keep any existing test server running."
} finally { $portProbe.Stop() }
if (-not $env:JAVA_HOME -and (Test-Path 'E:/biancheng/JDK21/bin/java.exe')) { $env:JAVA_HOME = 'E:/biancheng/JDK21' }
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to Java 21 before starting the local test API.' }
$java = Join-Path $env:JAVA_HOME 'bin/java.exe'
$jar = Join-Path $projectRoot 'backend/target/plan-platform-api-0.1.0.jar'
if (-not (Test-Path -LiteralPath $jar)) { throw 'Run verify-cross-client.bat to build the API first.' }
Write-Host "Local native test API: http://127.0.0.1:$Port. Keep this window open."
Write-Host "Use synthetic test accounts only. Database: backend/data/$DatabaseName."
& $java '-Duser.timezone=Asia/Shanghai' -jar $jar `
    '--server.address=127.0.0.1' "--server.port=$Port" '--spring.profiles.active=dev' `
    "--spring.datasource.url=jdbc:h2:file:./data/$DatabaseName;MODE=MySQL;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE" `
    '--spring.datasource.driver-class-name=org.h2.Driver' '--spring.datasource.username=sa' '--spring.datasource.password=' `
    '--app.security.registration-enabled=true' '--app.security.email-verification-enabled=false' `
    '--app.security.refresh-cookie-secure=false' '--app.security.password-reset-enabled=false' `
    '--app.security.bootstrap-admin-email=' '--spring.h2.console.enabled=false' `
    '--app.jwt.secret=native-local-test-only-secret-at-least-32-bytes' '--app.jwt.issuer=plan-platform-native-local'
if ($LASTEXITCODE -ne 0) { throw 'Local native test API failed. Keep the error above.' }
