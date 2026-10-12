param([switch]$AutoPassphrase)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $projectRoot
$owner=[Security.Principal.WindowsIdentity]::GetCurrent()
if($owner.Name -match 'CodexSandbox'){throw 'Run backup interaction in your own Windows account.'}
$run=[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')+'-'+[Guid]::NewGuid().ToString('N').Substring(0,8)
$directory=Join-Path $projectRoot ".desktop-tools/signing-backups/private/$run"
New-Item -ItemType Directory -Path $directory | Out-Null
$acl=New-Object Security.AccessControl.DirectorySecurity
$acl.SetOwner($owner.User);$acl.SetAccessRuleProtection($true,$false)
foreach($sid in @($owner.User,[Security.Principal.SecurityIdentifier]'S-1-5-18')){$acl.AddAccessRule((New-Object Security.AccessControl.FileSystemAccessRule($sid,'FullControl','ContainerInherit,ObjectInherit','None','Allow')))}
Set-Acl -LiteralPath $directory -AclObject $acl
$files=@('windows-updater.key','windows-updater.key.pub','android-release.p12','android-release.der','credentials.dpapi','identity.json')
$before=@{};foreach($name in $files){$before[$name]=(Get-FileHash -LiteralPath (Join-Path $projectRoot ".release-signing/$name")).Hash}
$backupPath=Join-Path $directory 'PlanCabin.plan-signing-backup'
$passphraseFile=''
if($AutoPassphrase){
    Add-Type -AssemblyName System.Security
    $rng=[Security.Cryptography.RandomNumberGenerator]::Create();$random=New-Object byte[] 32;$rng.GetBytes($random);$rng.Dispose()
    $plain=[Text.Encoding]::UTF8.GetBytes([Convert]::ToBase64String($random).TrimEnd('=').Replace('+','-').Replace('/','_'))
    $passphraseFile=Join-Path $directory 'passphrase.dpapi'
    try{[IO.File]::WriteAllBytes($passphraseFile,[Security.Cryptography.ProtectedData]::Protect($plain,[Text.Encoding]::UTF8.GetBytes('PlanCabin.BackupPassphrase.v1'),[Security.Cryptography.DataProtectionScope]::CurrentUser))}
    finally{[Array]::Clear($random,0,$random.Length);[Array]::Clear($plain,0,$plain.Length)}
}
Write-Host 'This creates an encrypted LOCAL backup, then restores an isolated copy and proves both signing identities.'
if($AutoPassphrase){Write-Host 'A separate random passphrase was generated and owner-protected. It will only be displayed in your local dialog after the recovery proof.'}
else{Write-Host 'Type a separate strong backup passphrase twice, then type it once more for the restore rehearsal. Nothing typed is shown.'}
& ./scripts/release-signing.ps1 -Action backup -BackupPath $backupPath -BackupPassphraseFile $passphraseFile
if($LASTEXITCODE -ne 0){throw 'Backup failed; restore rehearsal was not started.'}
& ./scripts/release-signing.ps1 -Action restore -BackupPath $backupPath -Rehearsal -BackupPassphraseFile $passphraseFile
if($LASTEXITCODE -ne 0){throw 'Isolated restore proof failed. Keep the original signing environment; do not claim backup acceptance.'}
foreach($name in $files){if((Get-FileHash -LiteralPath (Join-Path $projectRoot ".release-signing/$name")).Hash -ne $before[$name]){throw 'An original signing file changed; stop release preparation.'}}
$custodyConfirmed=$false
if($AutoPassphrase){
    & ./scripts/show-backup-passphrase.ps1 -PassphraseFile $passphraseFile
    if($LASTEXITCODE -ne 0){throw 'Passphrase custody was not confirmed. Local encrypted backup retained; original keys are unchanged.'}
    $custodyConfirmed=$true
}
$evidence=@{schema=1;backupSha256=(Get-FileHash -LiteralPath $backupPath).Hash.ToLowerInvariant();backupBytes=(Get-Item -LiteralPath $backupPath).Length;restoreCryptographicProof=$true;originalSigningFilesUnchanged=$true;passphraseCustodyConfirmed=$custodyConfirmed;independentCopyConfirmed=$false;createdUtc=[DateTime]::UtcNow.ToString('o')}
[IO.File]::WriteAllText((Join-Path $directory 'backup-check.json'),($evidence | ConvertTo-Json),[Text.UTF8Encoding]::new($false))
Write-Host "Verified encrypted backup: $backupPath"
Write-Host 'Original keys unchanged. Only upload the encrypted .plan-signing-backup file to your private cloud; do not upload .release-signing or rehearsal files.'
Write-Warning 'The backup is still on this computer. Keep the passphrase separately and retain another independent copy before public release.'
