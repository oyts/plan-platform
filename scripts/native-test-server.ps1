$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath (Join-Path $projectRoot 'backend')
if (Get-NetTCPConnection -State Listen -LocalPort 43129 -ErrorAction SilentlyContinue) {
    throw 'Port 43129 is occupied. Do not stop an unrelated process; check the existing local test server first.'
}
if (-not $env:JAVA_HOME -and (Test-Path 'E:/biancheng/JDK21/bin/java.exe')) { $env:JAVA_HOME = 'E:/biancheng/JDK21' }
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to Java 21 before starting the local test API.' }
$java = Join-Path $env:JAVA_HOME 'bin/java.exe'
$jar = Join-Path $projectRoot 'backend/target/plan-platform-api-0.1.0.jar'
if (-not (Test-Path -LiteralPath $jar)) { throw 'Run verify-cross-client.bat to build the API first.' }
Write-Host 'Local native test API: http://127.0.0.1:43129. Keep this window open.'
Write-Host 'Use synthetic test accounts only. Database: backend/data/desktop-local.'
& $java '-Duser.timezone=Asia/Shanghai' -jar $jar `
    '--server.address=127.0.0.1' '--server.port=43129' '--spring.profiles.active=dev' `
    '--spring.datasource.url=jdbc:h2:file:./data/desktop-local;MODE=MySQL;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE' `
    '--spring.datasource.driver-class-name=org.h2.Driver' '--spring.datasource.username=sa' '--spring.datasource.password=' `
    '--app.security.registration-enabled=true' '--app.security.email-verification-enabled=false' `
    '--app.security.refresh-cookie-secure=false' '--app.security.password-reset-enabled=false' `
    '--app.security.bootstrap-admin-email=' '--spring.h2.console.enabled=false' `
    '--app.jwt.secret=native-local-test-only-secret-at-least-32-bytes' '--app.jwt.issuer=plan-platform-native-local'
if ($LASTEXITCODE -ne 0) { throw 'Local native test API failed. Keep the error above.' }
