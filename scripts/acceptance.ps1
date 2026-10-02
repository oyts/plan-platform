param([switch]$InstallBrowser)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $projectRoot
$portableNode = Join-Path $projectRoot '.desktop-tools/node22/node-v22.23.3-win-x64'
if (Test-Path "$portableNode/node.exe") { $env:PATH = "$portableNode;$env:PATH" }
if ([int]((& node --version).TrimStart('v').Split('.')[0]) -lt 22) { throw 'Use Node.js 22+.' }
if (-not $env:JAVA_HOME -and (Test-Path 'E:/biancheng/JDK21/bin/java.exe')) { $env:JAVA_HOME = 'E:/biancheng/JDK21' }
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to Java 21.' }
$env:PATH = "$env:JAVA_HOME/bin;$env:PATH"
$maven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
if ($maven) { $mavenPath = $maven.Source }
elseif (Test-Path 'E:/biancheng/maven/apache-maven-3.9.4-bin/apache-maven-3.9.4/bin/mvn.cmd') { $mavenPath = 'E:/biancheng/maven/apache-maven-3.9.4-bin/apache-maven-3.9.4/bin/mvn.cmd' }
else { throw 'Install Maven 3.9+ and add it to PATH.' }
# Never reuse an occupied acceptance port or stop an unrelated development server.
foreach ($port in @(43130,43131)) {
    if (Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue) { throw "Port $port is already occupied. Close only the test server you started, then retry." }
}
& $mavenPath -f backend/pom.xml -B package
if ($LASTEXITCODE -ne 0) { throw 'Backend tests or packaging failed.' }
$env:PLAYWRIGHT_BROWSERS_PATH = Join-Path $projectRoot '.desktop-tools/playwright'
Set-Location web
if (-not (Test-Path node_modules/@playwright/test)) { & npm.cmd ci; if ($LASTEXITCODE -ne 0) { throw 'npm ci failed.' } }
if ($InstallBrowser) { & npx.cmd playwright install chromium; if ($LASTEXITCODE -ne 0) { throw 'Chromium installation failed.' } }
& npm.cmd test
if ($LASTEXITCODE -ne 0) { throw 'Frontend unit tests failed.' }
& npm.cmd run build
if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }
& node scripts/check-pwa.mjs
if ($LASTEXITCODE -ne 0) { throw 'PWA asset checks failed.' }
& npm.cmd run test:e2e
if ($LASTEXITCODE -ne 0) { throw 'Browser acceptance failed. See web/playwright-report. Native-device checks remain separate.' }
Write-Host 'Browser acceptance passed. This does not certify EXE/APK installation or device behavior.'
