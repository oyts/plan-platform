param(
    [ValidateSet('all', 'desktop', 'mobile-web')][string]$Profile = 'all',
    [switch]$PublicOnly,
    [string]$ChromiumPath
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $projectRoot
$testPython = Join-Path $projectRoot '.desktop-tools/public-web/venv/Scripts/python.exe'
if (-not (Test-Path -LiteralPath $testPython)) {
    throw 'Create the project test venv first. See docs/public-web-acceptance.md.'
}
if (-not $PublicOnly -and -not (Test-Path -LiteralPath '.desktop-tools/public-web/private/accounts.json') -and -not (Test-Path -LiteralPath '.desktop-tools/public-web/private/invitations.txt')) {
    throw 'Save the two dedicated invitations privately before business tests. Do not paste links or passwords into chat.'
}
$testArgs = @('scripts/public-web-acceptance.py', '--profile', $Profile)
if ($PublicOnly) { $testArgs += '--public-only' }
if (-not $ChromiumPath) {
    $existingBrowser = Join-Path $projectRoot '.desktop-tools/playwright/chromium-1243/chrome-win64/chrome.exe'
    if (Test-Path -LiteralPath $existingBrowser) { $ChromiumPath = $existingBrowser }
}
if ($ChromiumPath) {
    if (-not (Test-Path -LiteralPath $ChromiumPath -PathType Leaf)) { throw 'Chromium executable not found.' }
    $testArgs += @('--chromium', $ChromiumPath)
}
& $testPython @testArgs
exit $LASTEXITCODE
