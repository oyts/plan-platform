$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $projectRoot
$node = Join-Path $projectRoot '.desktop-tools/node22/node-v22.23.3-win-x64/node.exe'
if (-not (Test-Path -LiteralPath $node)) { $node = (Get-Command node -ErrorAction Stop).Source }
if (-not (Test-Path -LiteralPath '.desktop-tools/public-web/private/accounts.json')) {
    throw 'First complete invited USER registration using the public web acceptance workflow. Do not send passwords or invitation links to chat.'
}
& $node scripts/cloud-native-acceptance.mjs
exit $LASTEXITCODE
