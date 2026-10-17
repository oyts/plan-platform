param([switch]$SkipTests, [ValidateSet('affected','full')][string]$TestScope = 'affected')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $projectRoot
. (Join-Path $PSScriptRoot 'release-test-scope.ps1')
$affectedTests = if ($TestScope -eq 'affected' -and -not $SkipTests) { Get-ReleaseTestScope } else { $null }
$portableNode = Join-Path $projectRoot '.desktop-tools/node22/node-v22.23.3-win-x64'
if (Test-Path -LiteralPath "$portableNode/node.exe") { $env:PATH = "$portableNode;$env:PATH" }
if (-not $env:JAVA_HOME -and (Test-Path 'E:/biancheng/JDK21/bin/java.exe')) { $env:JAVA_HOME = 'E:/biancheng/JDK21' }
if ($env:JAVA_HOME) { $env:PATH = "$env:JAVA_HOME/bin;$env:PATH" }
& node scripts/verify-release.mjs
if ($LASTEXITCODE -ne 0) { throw 'Version check failed.' }
$maven = (Get-Command mvn.cmd -ErrorAction SilentlyContinue).Source
if (-not $maven -and (Test-Path 'E:/biancheng/maven/apache-maven-3.9.4-bin/apache-maven-3.9.4/bin/mvn.cmd')) { $maven = 'E:/biancheng/maven/apache-maven-3.9.4-bin/apache-maven-3.9.4/bin/mvn.cmd' }
if (-not $maven) { throw 'Maven 3.9+ required.' }
$arguments = @('-B','-f','backend/pom.xml','-Dplan.build.directory=target-acceptance','clean','package')
if ($SkipTests -or ($TestScope -eq 'affected' -and -not @($affectedTests.backend).Count)) { $arguments += '-DskipTests' }
elseif ($TestScope -eq 'affected') { $arguments += ('-Dtest=' + ($affectedTests.backend -join ',')) }
& $maven @arguments
if ($LASTEXITCODE -ne 0) { throw 'Isolated API build failed.' }
if (-not $SkipTests) { Invoke-ReleaseFrontendTests $TestScope }
& npm.cmd --prefix web run build
if ($LASTEXITCODE -ne 0) { throw 'Web build failed.' }
if (-not $SkipTests) {
    $localBrowsers = Join-Path $projectRoot '.desktop-tools/playwright'
    if (-not $env:PLAYWRIGHT_BROWSERS_PATH -and (Test-Path -LiteralPath $localBrowsers)) { $env:PLAYWRIGHT_BROWSERS_PATH = $localBrowsers }
    $browserArguments = @('--prefix','web','run','test:e2e','--')
    if ($TestScope -eq 'affected') { $browserArguments += @($affectedTests.browser) }
    if ($TestScope -eq 'full' -or @($affectedTests.browser).Count) {
        & npm.cmd @browserArguments
        if ($LASTEXITCODE -ne 0) { throw 'Selected browser acceptance failed; no release archive prepared.' }
    }
}
$version = (Get-Content web/package.json -Raw | ConvertFrom-Json).version
if ($version -notmatch '^\d+\.\d+\.\d+$') { throw 'Expected x.y.z release version.' }
$output = Join-Path $projectRoot "production-artifacts/$version"
$output = [IO.Path]::GetFullPath($output)
$artifactRoot = [IO.Path]::GetFullPath((Join-Path $projectRoot 'production-artifacts')) + [IO.Path]::DirectorySeparatorChar
if (-not $output.StartsWith($artifactRoot, [StringComparison]::OrdinalIgnoreCase)) { throw 'Output must stay within production-artifacts.' }
if (Test-Path -LiteralPath $output) {
    # Preserve previous outputs and avoid shipping obsolete hashed assets on rebuild.
    $previousOutput = "$output-before-$([DateTime]::UtcNow.ToString('yyyyMMddHHmmss'))"
    Move-Item -LiteralPath $output -Destination $previousOutput
}
New-Item -ItemType Directory -Force -Path "$output/backend","$output/web","$output/scripts" | Out-Null
Copy-Item -LiteralPath backend/target-acceptance/plan-platform-api.jar -Destination "$output/backend/api.jar" -Force
Copy-Item -LiteralPath backend/Dockerfile.prebuilt -Destination "$output/backend/Dockerfile" -Force
Copy-Item -LiteralPath web/Dockerfile.prebuilt -Destination "$output/web/Dockerfile" -Force
Copy-Item -LiteralPath web/nginx.production.conf -Destination "$output/web/nginx.conf" -Force
Copy-Item -LiteralPath web/package.json -Destination "$output/web/package.json" -Force
# Copy an enumerated build tree instead of archiving the repo, .env or credentials.
Copy-Item -LiteralPath web/dist -Destination "$output/web" -Recurse -Force
Copy-Item -LiteralPath docker-compose.production.yml -Destination $output -Force
Copy-Item -LiteralPath docker-compose.ai-secrets.yml -Destination $output -Force
foreach ($name in @('initialize-production.sh','mysql-backup.sh','mysql-restore-drill.sh','mysql-oss-upload.sh','daily-mysql-backup.sh','daily-migration-drill.sh','switch-public-entry.sh','rollback-public-entry.sh','production-compose.sh','configure-ai-secret.sh','verify-ai-secret-mount.sh','ai-candidate.sh','ai-migration-drill.sh','ai-mysql-acceptance.py','ai-adjustment-acceptance.py','configure-mail-secret.sh','prepare-mail-overlay.sh','activate-mail.sh')) {
    Copy-Item -LiteralPath "scripts/$name" -Destination "$output/scripts/$name" -Force
}
Copy-Item -LiteralPath scripts/operations-cloud-acceptance.py -Destination "$output/scripts/operations-cloud-acceptance.py" -Force
$archive = Join-Path $projectRoot "production-artifacts/plan-platform-$version.tar.gz"
& tar -czf $archive -C $output backend web scripts docker-compose.production.yml docker-compose.ai-secrets.yml
if ($LASTEXITCODE -ne 0) { throw 'Release archive failed.' }
Get-FileHash -LiteralPath $archive -Algorithm SHA256 | Format-List
Write-Host "Prepared: $archive. No upload, install or public cutover performed."
