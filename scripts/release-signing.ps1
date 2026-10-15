param(
    [ValidateSet('init','check','backup','verify-backup','restore','build-windows','build-android')][string]$Action = 'check',
    [string]$ArtifactDirectory = '',
    [string]$BackupPath = '',
    [string]$BackupPassphraseFile = '',
    [switch]$Rehearsal,
    [switch]$DirectMavenCentral,
    [ValidateSet('affected','full')][string]$TestScope = 'affected'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $projectRoot
$signingRoot = Join-Path $projectRoot '.release-signing'
$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
if ($identity.Name -match 'CodexSandbox') { throw 'Run formal signing as the real Windows owner, not a sandbox account.' }
Add-Type -AssemblyName System.Security
$entropy = [Text.Encoding]::UTF8.GetBytes('PlanCabin.ReleaseSigning.v1')
$credentialsFile = Join-Path $signingRoot 'credentials.dpapi'
$metadataFile = Join-Path $signingRoot 'identity.json'
$node = Join-Path $projectRoot '.desktop-tools/node22/node-v22.23.3-win-x64/node.exe'
$javaRoot = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { 'E:/biancheng/JDK21' }
$keytool = Join-Path $javaRoot 'bin/keytool.exe'
$privateKey = Join-Path $signingRoot 'windows-updater.key'
$publicKey = "$privateKey.pub"
$keystore = Join-Path $signingRoot 'android-release.p12'
$certificate = Join-Path $signingRoot 'android-release.der'
function Invoke-PrivateTool([string]$Program, [string[]]$Arguments) {
    # All output is captured, since Tauri key generation prints private material.
    # A native failure never echoes the command, arguments or captured output.
    $start = New-Object Diagnostics.ProcessStartInfo
    $start.FileName = $Program
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    if ($start.PSObject.Properties['ArgumentList']) {
        foreach ($argument in $Arguments) { $start.ArgumentList.Add($argument) }
    } else {
        $start.Arguments = ($Arguments | ForEach-Object { '"' + ($_ -replace '(\\*)"','$1$1\"' -replace '(\\+)$','$1$1') + '"' }) -join ' '
    }
    try {
        $process = New-Object Diagnostics.Process
        $process.StartInfo = $start
        if (-not $process.Start()) { throw 'start failed' }
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        $process.WaitForExit()
        $null = $stdout.Result; $null = $stderr.Result
        if ($process.ExitCode -ne 0) { throw 'nonzero exit' }
    } catch { throw 'Private signing tool failed. Output withheld; existing keys were not overwritten.' }
    finally { if ($process) { $process.Dispose() } }
}
function Read-Credentials {
    $bytes = [Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes($credentialsFile), $entropy, [Security.Cryptography.DataProtectionScope]::CurrentUser)
    try { return ([Text.Encoding]::UTF8.GetString($bytes) | ConvertFrom-Json) }
    finally { [Array]::Clear($bytes,0,$bytes.Length) }
}
function Write-NewPrivateFile([string]$Path,[byte[]]$Bytes) {
    $stream=[IO.File]::Open($Path,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
    try{$stream.Write($Bytes,0,$Bytes.Length)}finally{$stream.Dispose()}
}
function Read-BackupPassphrase([string]$Prompt) {
    if(-not $BackupPassphraseFile){return (Read-Host $Prompt -AsSecureString)}
    $path=[IO.Path]::GetFullPath($BackupPassphraseFile)
    $allowed=[IO.Path]::GetFullPath((Join-Path $projectRoot '.desktop-tools/signing-backups/private'))+[IO.Path]::DirectorySeparatorChar
    if(-not $path.StartsWith($allowed,[StringComparison]::OrdinalIgnoreCase) -or -not (Test-Path -LiteralPath $path -PathType Leaf) -or (Get-Item -LiteralPath $path).Length -gt 4KB){throw 'Expected an owner-protected local backup passphrase file.'}
    $passEntropy=[Text.Encoding]::UTF8.GetBytes('PlanCabin.BackupPassphrase.v1')
    $bytes=[Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes($path),$passEntropy,[Security.Cryptography.DataProtectionScope]::CurrentUser)
    $secure=New-Object Security.SecureString
    try{foreach($character in [Text.Encoding]::UTF8.GetString($bytes).ToCharArray()){$secure.AppendChar($character)};$secure.MakeReadOnly();return $secure}
    finally{[Array]::Clear($bytes,0,$bytes.Length)}
}
function Check-Identity {
    if (-not (Test-Path -LiteralPath $metadataFile)) { throw 'Formal signing identity is absent. Initialize it first; do not use debug keys.' }
    $metadata = Get-Content -LiteralPath $metadataFile -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($metadata.schema -ne 1 -or $metadata.ownerSid -ne $identity.User.Value -or $metadata.androidPackage -ne 'cn.zijun701.plan.app') { throw 'Signing owner/schema/package mismatch.' }
    if (@($metadata.files.PSObject.Properties).Count -ne 4) { throw 'Incomplete signing identity hash list.' }
    foreach ($entry in $metadata.files.PSObject.Properties) {
        if ($entry.Name -notin @('windows-updater.key','windows-updater.key.pub','android-release.p12','android-release.der')) { throw 'Unexpected signing identity filename.' }
        $path = Join-Path $signingRoot $entry.Name
        if ((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() -ne $entry.Value) { throw 'Signing identity content changed. Do not regenerate or replace it.' }
    }
    $null = Read-Credentials
    Write-Host 'Formal identity verified: original keys, owner-bound password protection, Android new package.'
}
function Invoke-BackupCodec($Request,[string]$Mode='') {
    $start=New-Object Diagnostics.ProcessStartInfo
    $start.FileName=$node;$start.Arguments="scripts/signing-backup.mjs $Mode";$start.WorkingDirectory=$projectRoot
    $start.UseShellExecute=$false;$start.CreateNoWindow=$true
    $start.RedirectStandardInput=$true;$start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true
    $codec=New-Object Diagnostics.Process;$codec.StartInfo=$start
    try {
        if(-not $codec.Start()) {throw 'Signing backup process could not start.'}
        $stdout=$codec.StandardOutput.ReadToEndAsync();$stderr=$codec.StandardError.ReadToEndAsync()
        $codec.StandardInput.Write(($Request | ConvertTo-Json -Depth 8 -Compress));$codec.StandardInput.Close()
        $codec.WaitForExit();$null=$stderr.Result
        if($codec.ExitCode -ne 0) {throw 'Signing backup validation/decryption failed. Private output withheld.'}
        return $stdout.Result
    } finally {$codec.Dispose()}
}
function Test-IdentityProof {
    $credentials=Read-Credentials
    $names=@('PLAN_RELEASE_KEYTOOL_PASSWORD','TAURI_SIGNING_PRIVATE_KEY','TAURI_SIGNING_PRIVATE_KEY_PATH','TAURI_SIGNING_PRIVATE_KEY_PASSWORD')
    $saved=@{};foreach($name in $names){$saved[$name]=[Environment]::GetEnvironmentVariable($name,'Process');[Environment]::SetEnvironmentVariable($name,$null,'Process')}
    $probe=Join-Path $signingRoot ('restore-proof-'+[Guid]::NewGuid().ToString('N'))
    try {
        $env:PLAN_RELEASE_KEYTOOL_PASSWORD=$credentials.android
        Invoke-PrivateTool $keytool @('-exportcert','-keystore',$keystore,'-storepass:env','PLAN_RELEASE_KEYTOOL_PASSWORD','-alias','plan-cabin-release','-file',"$probe.der")
        if((Get-FileHash -LiteralPath "$probe.der").Hash -ne (Get-FileHash -LiteralPath $certificate).Hash){throw 'Restored Android keystore does not contain the preserved certificate.'}
        Invoke-PrivateTool $keytool @('-certreq','-keystore',$keystore,'-storepass:env','PLAN_RELEASE_KEYTOOL_PASSWORD','-keypass:env','PLAN_RELEASE_KEYTOOL_PASSWORD','-alias','plan-cabin-release','-file',"$probe.csr")
        [IO.File]::WriteAllText($probe,'PlanCabin isolated signing recovery proof',[Text.UTF8Encoding]::new($false))
        $env:TAURI_SIGNING_PRIVATE_KEY_PATH=$privateKey;$env:TAURI_SIGNING_PRIVATE_KEY_PASSWORD=$credentials.windows
        Invoke-PrivateTool $node @('web/node_modules/@tauri-apps/cli/tauri.js','signer','sign','--app-version','0.1.4',$probe)
        $proof="import {readFileSync} from 'node:fs';import {verifyUpdaterSignature} from './scripts/formal-release-support.mjs';verifyUpdaterSignature(readFileSync(process.argv[1]),readFileSync(process.argv[2],'utf8'),readFileSync(process.argv[1]+'.sig','utf8'));"
        Invoke-PrivateTool $node @('--input-type=module','-e',$proof,$probe,$publicKey)
        Write-Host 'Recovery cryptographic proof passed: Android original certificate and Windows original updater public key.'
    } finally {
        $credentials=$null;foreach($name in $names){[Environment]::SetEnvironmentVariable($name,$saved[$name],'Process')}
        foreach($path in @($probe,"$probe.der","$probe.sig","$probe.csr")){if(Test-Path -LiteralPath $path){Remove-Item -LiteralPath $path}}
    }
}
if($Rehearsal -and $Action -ne 'restore'){throw 'Rehearsal is only for isolated restore tests.'}
if($Action -in @('verify-backup','restore')) {
    if(-not $BackupPath -or -not (Test-Path -LiteralPath $BackupPath -PathType Leaf)){throw 'Supply an existing encrypted signing backup.'}
    if((Get-Item -LiteralPath $BackupPath).Length -gt 2MB){throw 'Signing backup exceeds the format size limit.'}
    if($Action -eq 'restore' -and -not $Rehearsal -and (Test-Path -LiteralPath $signingRoot) -and (Get-ChildItem -LiteralPath $signingRoot -Force)){throw 'Existing signing identity is never overwritten. Use Rehearsal or restore on an empty maintenance environment.'}
    $secret=Read-BackupPassphrase 'Offline backup passphrase (not a signing key password)'
    $pointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
    try {
        $password=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
        $archive=Get-Content -LiteralPath $BackupPath -Raw -Encoding UTF8 | ConvertFrom-Json
        $payload=(Invoke-BackupCodec @{archive=$archive;password=$password} 'open') | ConvertFrom-Json
        $password=$null
        if($Action -eq 'verify-backup'){Write-Host 'Encrypted backup decrypted and all four signing file hashes verified. No keys were restored or replaced.';exit 0}
        if($Rehearsal){$signingRoot=Join-Path $projectRoot ('.desktop-tools/signing-restore/private/'+[Guid]::NewGuid().ToString('N'))}
        if((Test-Path -LiteralPath $signingRoot) -and ((Get-Item -LiteralPath $signingRoot).Attributes -band [IO.FileAttributes]::ReparsePoint)){throw 'Restore directory cannot be a link.'}
        New-Item -ItemType Directory -Path $signingRoot -Force | Out-Null
        $restoreAcl=New-Object Security.AccessControl.DirectorySecurity
        $restoreAcl.SetOwner($identity.User);$restoreAcl.SetAccessRuleProtection($true,$false)
        foreach($sid in @($identity.User,[Security.Principal.SecurityIdentifier]'S-1-5-18')){$restoreAcl.AddAccessRule((New-Object Security.AccessControl.FileSystemAccessRule($sid,'FullControl','ContainerInherit,ObjectInherit','None','Allow')))}
        Set-Acl -LiteralPath $signingRoot -AclObject $restoreAcl
        foreach($entry in $payload.files.PSObject.Properties){
            $stream=[IO.File]::Open((Join-Path $signingRoot $entry.Name),[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
            try{$bytes=[Convert]::FromBase64String($entry.Value);$stream.Write($bytes,0,$bytes.Length)}finally{$stream.Dispose();if($bytes){[Array]::Clear($bytes,0,$bytes.Length)}}
        }
        $credentialsFile=Join-Path $signingRoot 'credentials.dpapi';$metadataFile=Join-Path $signingRoot 'identity.json'
        $privateKey=Join-Path $signingRoot 'windows-updater.key';$publicKey="$privateKey.pub";$keystore=Join-Path $signingRoot 'android-release.p12';$certificate=Join-Path $signingRoot 'android-release.der'
        $plain=[Text.Encoding]::UTF8.GetBytes(($payload.credentials | ConvertTo-Json -Compress))
        try{Write-NewPrivateFile $credentialsFile ([Security.Cryptography.ProtectedData]::Protect($plain,$entropy,[Security.Cryptography.DataProtectionScope]::CurrentUser))}finally{[Array]::Clear($plain,0,$plain.Length)}
        $payload.metadata.ownerSid=$identity.User.Value;$payload.metadata.portableBackupComplete=$false
        $payload.metadata | Add-Member -NotePropertyName restoredFromBackupUtc -NotePropertyValue ([DateTime]::UtcNow.ToString('o')) -Force
        Write-NewPrivateFile $metadataFile ([Text.Encoding]::UTF8.GetBytes(($payload.metadata | ConvertTo-Json -Depth 5)))
        Check-Identity;Test-IdentityProof
        Write-Host "Verified restored identity: $signingRoot"
        if($Rehearsal){Write-Host 'Original signing directory was not modified. Rehearsal keys remain owner-restricted and ignored by Git.'}
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer);$secret.Dispose();$payload=$null;$archive=$null;$password=$null
    }
    exit 0
}
if ($Action -eq 'init') {
    if (Test-Path -LiteralPath $metadataFile) { Check-Identity; exit 0 }
    if ((Test-Path -LiteralPath $signingRoot) -and (Get-ChildItem -LiteralPath $signingRoot -Force)) { throw 'Incomplete signing directory exists. Stop and inspect; never regenerate over partial keys.' }
    New-Item -ItemType Directory -Path $signingRoot -Force | Out-Null
    if ((Get-Item -LiteralPath $signingRoot).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Signing directory cannot be a link.' }
    $acl = New-Object Security.AccessControl.DirectorySecurity
    $acl.SetOwner($identity.User)
    $acl.SetAccessRuleProtection($true,$false)
    foreach ($sid in @($identity.User, [Security.Principal.SecurityIdentifier]'S-1-5-18')) {
        $rule = New-Object Security.AccessControl.FileSystemAccessRule($sid,'FullControl','ContainerInherit,ObjectInherit','None','Allow')
        $acl.AddAccessRule($rule)
    }
    Set-Acl -LiteralPath $signingRoot -AclObject $acl
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    $windowsBytes = New-Object byte[] 32; $androidBytes = New-Object byte[] 32
    $rng.GetBytes($windowsBytes); $rng.GetBytes($androidBytes); $rng.Dispose()
    $credentials = @{ windows = [Convert]::ToBase64String($windowsBytes); android = [Convert]::ToBase64String($androidBytes) }
    $plain = [Text.Encoding]::UTF8.GetBytes(($credentials | ConvertTo-Json -Compress))
    try { [IO.File]::WriteAllBytes($credentialsFile,[Security.Cryptography.ProtectedData]::Protect($plain,$entropy,[Security.Cryptography.DataProtectionScope]::CurrentUser)) }
    finally { [Array]::Clear($plain,0,$plain.Length); [Array]::Clear($windowsBytes,0,$windowsBytes.Length); [Array]::Clear($androidBytes,0,$androidBytes.Length) }
    Invoke-PrivateTool $node @('web/node_modules/@tauri-apps/cli/tauri.js','signer','generate','--ci','--password',$credentials.windows,'--write-keys',$privateKey)
    $oldPassword = $env:PLAN_RELEASE_KEYTOOL_PASSWORD
    try {
        $env:PLAN_RELEASE_KEYTOOL_PASSWORD = $credentials.android
        Invoke-PrivateTool $keytool @('-genkeypair','-storetype','PKCS12','-keystore',$keystore,'-storepass:env','PLAN_RELEASE_KEYTOOL_PASSWORD','-keypass:env','PLAN_RELEASE_KEYTOOL_PASSWORD','-alias','plan-cabin-release','-dname','CN=PlanCabin Release, OU=Open Source Project, C=CN','-keyalg','RSA','-keysize','3072','-sigalg','SHA256withRSA','-validity','10950')
        Invoke-PrivateTool $keytool @('-exportcert','-keystore',$keystore,'-storepass:env','PLAN_RELEASE_KEYTOOL_PASSWORD','-alias','plan-cabin-release','-file',$certificate)
    } finally { $env:PLAN_RELEASE_KEYTOOL_PASSWORD = $oldPassword; $credentials = $null }
    $hashes = @{}
    foreach ($path in @($privateKey,$publicKey,$keystore,$certificate)) { $hashes[[IO.Path]::GetFileName($path)] = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() }
    $record = @{ schema=1; ownerSid=$identity.User.Value; androidPackage='cn.zijun701.plan.app'; alias='plan-cabin-release'; createdUtc=[DateTime]::UtcNow.ToString('o'); files=$hashes; portableBackupComplete=$false }
    [IO.File]::WriteAllText($metadataFile,($record | ConvertTo-Json -Depth 4),[Text.UTF8Encoding]::new($false))
    Check-Identity
    Write-Warning 'Keys are created, not backed up portably yet. DPAPI passwords are tied to this Windows user/machine; do not lose this environment.'
    exit 0
}
Check-Identity
if ($Action -eq 'check') { exit 0 }
if ($Action -eq 'backup') {
    if (-not $BackupPath) { throw 'Supply BackupPath on your own offline storage. Never upload signing backups to Git or public OSS.' }
    $destination = [IO.Path]::GetFullPath($BackupPath)
    if (Test-Path -LiteralPath $destination) { throw 'Backup destination already exists; it will not be overwritten.' }
    $first = Read-BackupPassphrase 'New offline backup passphrase (at least 16 characters; keep separately)'
    $second = Read-BackupPassphrase 'Repeat offline backup passphrase'
    $firstPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($first)
    $secondPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($second)
    try {
        $password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($firstPtr)
        if ($password.Length -lt 16 -or $password -cne [Runtime.InteropServices.Marshal]::PtrToStringBSTR($secondPtr)) { throw 'Backup passphrases differ or are too short.' }
        $files = @{}
        foreach ($path in @($privateKey,$publicKey,$keystore,$certificate)) { $files[[IO.Path]::GetFileName($path)] = [Convert]::ToBase64String([IO.File]::ReadAllBytes($path)) }
        $payload = @{ schema=1; metadata=(Get-Content $metadataFile -Raw -Encoding UTF8 | ConvertFrom-Json); credentials=(Read-Credentials); files=$files }
        $encrypted=[Text.Encoding]::UTF8.GetBytes((Invoke-BackupCodec @{password=$password;payload=$payload}))
        $stream=[IO.File]::Open($destination,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
        try { $stream.Write($encrypted,0,$encrypted.Length) } finally { $stream.Dispose() }
        Write-Host 'Encrypted portable backup written and round-trip verified. Keep it offline; keep the passphrase separately.'
        Write-Warning 'A copy on this computer alone is not an independent backup. No public release gate was changed.'
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($firstPtr); [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($secondPtr)
        $password=$null; $payload=$null; $files=$null; $first.Dispose(); $second.Dispose()
    }
    exit 0
}
if (-not $ArtifactDirectory) { throw 'Supply a new isolated production-artifacts directory; no existing bundle is overwritten.' }
$output = [IO.Path]::GetFullPath($ArtifactDirectory)
$allowed = [IO.Path]::GetFullPath((Join-Path $projectRoot 'production-artifacts')) + [IO.Path]::DirectorySeparatorChar
if (-not $output.StartsWith($allowed,[StringComparison]::OrdinalIgnoreCase)) { throw 'Formal artifacts must stay under production-artifacts.' }
if ((Test-Path -LiteralPath $output) -and (Get-ChildItem -LiteralPath $output -Force)) { throw 'Artifact directory already has files. Choose a fresh immutable candidate directory.' }
$variables = @('TAURI_SIGNING_PRIVATE_KEY','TAURI_SIGNING_PRIVATE_KEY_PASSWORD','TAURI_CONFIG','PLAN_ANDROID_KEYSTORE','PLAN_ANDROID_STORE_PASSWORD','PLAN_ANDROID_KEY_ALIAS','PLAN_ANDROID_KEY_PASSWORD','PLAN_ANDROID_UPDATE_URL','PLAN_ANDROID_BUILD_ROOT','PLAN_NATIVE_REGRESSION','PLAN_PUBLIC_RELEASE','PLAN_ANDROID_TEST_UPDATE_CERT','PLAN_ANDROID_TEST_VERSION','PLAN_ANDROID_TEST_VERSION_CODE','PLAN_DESKTOP_TEST_UPDATE_CERT','PLAN_DESKTOP_UPDATE_URL','PLAN_DESKTOP_UPDATE_PUBKEY','JAVA_HOME')
$saved = @{}; foreach ($name in $variables) { $saved[$name] = [Environment]::GetEnvironmentVariable($name,'Process'); [Environment]::SetEnvironmentVariable($name,$null,'Process') }
$credentials = Read-Credentials
try {
    $env:JAVA_HOME = $javaRoot
    if ($Action -eq 'build-android') {
        $env:PLAN_ANDROID_KEYSTORE = $keystore; $env:PLAN_ANDROID_KEY_ALIAS = 'plan-cabin-release'
        $env:PLAN_ANDROID_STORE_PASSWORD = $credentials.android; $env:PLAN_ANDROID_KEY_PASSWORD = $credentials.android
        $env:PLAN_ANDROID_UPDATE_URL = 'https://plan.zijun701.cn/api/client-updates/android/latest'
        $androidArguments = @('-NoProfile','-ExecutionPolicy','Bypass','-File','scripts/android.ps1','-Action','release','-ArtifactDirectory',$output,'-TestScope',$TestScope)
        if ($DirectMavenCentral) { $androidArguments += '-DirectMavenCentral' }
        & powershell @androidArguments
    } else {
        $env:TAURI_SIGNING_PRIVATE_KEY = $privateKey; $env:TAURI_SIGNING_PRIVATE_KEY_PASSWORD = $credentials.windows
        & powershell -NoProfile -ExecutionPolicy Bypass -File scripts/desktop.ps1 -Action build -UpdateUrl https://plan.zijun701.cn/client-updates/windows/latest.json -PublicKeyPath $publicKey -ArtifactDirectory $output
    }
    if ($LASTEXITCODE -ne 0) { throw 'Formal client build failed. Existing keys and installed apps are preserved.' }
} finally {
    $credentials = $null
    foreach ($name in $variables) { [Environment]::SetEnvironmentVariable($name,$saved[$name],'Process') }
}
