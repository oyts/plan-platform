param(
    [Parameter(Mandatory=$true)][string]$CandidateDirectory,
    [Parameter(Mandatory=$true)][string]$SigningBackupDirectory,
    [Parameter(Mandatory=$true)][string]$CloudBackupCopy,
    [switch]$ConfirmWindowsAcceptance,
    [switch]$ConfirmOldCloudPairCleanup
)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $projectRoot
if(-not $ConfirmWindowsAcceptance -or -not $ConfirmOldCloudPairCleanup){throw 'Confirm formal Windows acceptance and old cloud backup pair cleanup. No files were generated.'}
$node=Join-Path $projectRoot '.desktop-tools/node22/node-v22.23.3-win-x64/node.exe'
& $node scripts/verify-release.mjs
if($LASTEXITCODE -ne 0){throw 'Version consistency failed. No publication output generated.'}
& $node scripts/prepare-windows-public-release.mjs $CandidateDirectory $SigningBackupDirectory $CloudBackupCopy --windows-accepted --old-cloud-pair-cleaned
if($LASTEXITCODE -ne 0){throw 'Windows publication preparation failed; nothing was uploaded or installed.'}
