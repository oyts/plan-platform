param([Parameter(Mandatory=$true)][string]$CandidateDirectory,[Parameter(Mandatory=$true)][string]$InstallDirectory,[switch]$ConfirmedClosed)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $projectRoot
$owner=[Security.Principal.WindowsIdentity]::GetCurrent()
if ($owner.Name -match 'CodexSandbox' -or -not $ConfirmedClosed) { throw 'Run as the real Windows owner after explicitly saving and closing the client.' }
$candidate=[IO.Path]::GetFullPath($CandidateDirectory)
$production=[IO.Path]::GetFullPath((Join-Path $projectRoot 'production-artifacts'))+[IO.Path]::DirectorySeparatorChar
if (-not $candidate.StartsWith($production,[StringComparison]::OrdinalIgnoreCase)) { throw 'Expected a local verified production candidate.' }
$record=Get-Content (Join-Path $candidate 'metadata/candidate.json') -Raw -Encoding UTF8 | ConvertFrom-Json
if ($record.status -ne 'LOCAL_SIGNED_CANDIDATE' -or $record.version -ne '0.1.4' -or -not $record.windows.updaterSignatureVerified) { throw 'Candidate metadata not verified for this acceptance stage.' }
if ([IO.Path]::GetFileName($record.windows.file) -ne $record.windows.file) { throw 'Unsafe installer filename.' }
$installer=Join-Path $candidate ('windows/'+$record.windows.file)
if ((Get-FileHash -LiteralPath $installer).Hash.ToLowerInvariant() -ne $record.windows.sha256) { throw 'Installer differs from verified candidate.' }
$target=[IO.Path]::GetFullPath($InstallDirectory)
if ($target -match '["\r\n]' -or -not (Test-Path -LiteralPath $target -PathType Container) -or (Get-Item -LiteralPath $target).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Expected the real existing installation directory.' }
$exe=Join-Path $target 'plan-platform-desktop.exe'
if (-not (Test-Path -LiteralPath $exe -PathType Leaf)) { throw 'No original Plan Cabin executable in the selected directory.' }
if (Get-Process -Name plan-platform-desktop -ErrorAction SilentlyContinue) { throw 'Close all formal Plan Cabin processes before any backup or installation. Nothing was stopped.' }
$node=Join-Path $projectRoot '.desktop-tools/node22/node-v22.23.3-win-x64/node.exe'
$signatureCheck="import {readFileSync} from 'node:fs';import {verifyUpdaterSignature} from './scripts/formal-release-support.mjs';verifyUpdaterSignature(readFileSync(process.argv[1]),readFileSync('.release-signing/windows-updater.key.pub','utf8'),readFileSync(process.argv[1]+'.sig','utf8'));console.log('Installer signature verified with the preserved long-term public key.');"
& $node --input-type=module -e $signatureCheck $installer
if ($LASTEXITCODE -ne 0) { throw 'Real updater signature validation failed.' }
$run=[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')+'-'+[Guid]::NewGuid().ToString('N').Substring(0,8)
$backup=Join-Path $projectRoot ".desktop-tools/formal-windows/private/$run"
New-Item -ItemType Directory -Path $backup | Out-Null
$acl=New-Object Security.AccessControl.DirectorySecurity
$acl.SetOwner($owner.User);$acl.SetAccessRuleProtection($true,$false)
foreach ($sid in @($owner.User,[Security.Principal.SecurityIdentifier]'S-1-5-18')) { $acl.AddAccessRule((New-Object Security.AccessControl.FileSystemAccessRule($sid,'FullControl','ContainerInherit,ObjectInherit','None','Allow'))) }
Set-Acl -LiteralPath $backup -AclObject $acl
New-Item -ItemType Directory -Path (Join-Path $backup 'original-program') | Out-Null
foreach ($name in @('plan-platform-desktop.exe','uninstall.exe')) { $source=Join-Path $target $name;if(Test-Path -LiteralPath $source) { Copy-Item -LiteralPath $source -Destination (Join-Path $backup "original-program/$name") } }
$before=@();$roots=@{}
foreach ($scope in @('roaming','local')) {
    $base=if($scope -eq 'roaming'){$env:APPDATA}else{$env:LOCALAPPDATA}
    $path=Join-Path $base 'cn.zijun701.plan.desktop';$roots[$scope]=$path
    if(-not (Test-Path -LiteralPath $path)) {continue}
    $entries=@(Get-Item -LiteralPath $path)+@(Get-ChildItem -LiteralPath $path -Recurse -Force)
    if($entries | Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint }) { throw 'App data has linked paths; inspect before copying.' }
    foreach($file in $entries | Where-Object {-not $_.PSIsContainer}) {
        $relative=$file.FullName.Substring($path.Length+1)
        $before+=@{scope=$scope;path=$relative;sha256=(Get-FileHash -LiteralPath $file.FullName).Hash.ToLowerInvariant()}
    }
    & robocopy $path (Join-Path $backup $scope) /E /COPY:DAT /DCOPY:DAT /NFL /NDL /NJH /NJS /NP /R:1 /W:1 | Out-Null
    if($LASTEXITCODE -ge 8) { throw 'Local application data backup failed; installer was not started.' }
}
foreach($file in $before) {
    if((Get-FileHash -LiteralPath (Join-Path $backup ($file.scope+'/'+$file.path))).Hash.ToLowerInvariant() -ne $file.sha256) {throw 'Application backup hash mismatch; installation was not started.'}
}
[IO.File]::WriteAllText((Join-Path $backup 'data-before.json'),($before | ConvertTo-Json -Depth 4),[Text.UTF8Encoding]::new($false))
if (Get-Process -Name plan-platform-desktop -ErrorAction SilentlyContinue) { throw 'Client reopened during backup; installation was not started.' }
$beforeVersion=(Get-Item -LiteralPath $exe).VersionInfo.ProductVersion
# The generated Tauri NSIS template's /UPDATE path skips uninstalling. /D is last
# and unquoted even for a spaced path, as required by NSIS itself.
$process=Start-Process -FilePath $installer -ArgumentList "/S /UPDATE /D=$target" -WindowStyle Hidden -Wait -PassThru
if($process.ExitCode -ne 0) {throw "Installer failed. Protected original program/data retained at: $backup"}
$installedVersion=(Get-Item -LiteralPath $exe).VersionInfo.ProductVersion
if($installedVersion -ne $record.version) {throw 'Installed product version does not match the verified candidate. No automatic downgrade or data deletion performed.'}
foreach($file in $before) {
    $path=Join-Path $roots[$file.scope] $file.path
    if(-not (Test-Path -LiteralPath $path) -or (Get-FileHash -LiteralPath $path).Hash.ToLowerInvariant() -ne $file.sha256) { throw 'An existing app data file changed during installation. Stop acceptance and retain the protected backup.' }
}
$evidence=@{schema=1;versionBefore=$beforeVersion;versionAfter=$installedVersion;installerSha256=$record.windows.sha256;installedExeSha256=(Get-FileHash -LiteralPath $exe).Hash.ToLowerInvariant();originalDataFilesUnchanged=$before.Count;credentialStoreExported=$false;appLaunched=$false;publicRelease=$false}
[IO.File]::WriteAllText((Join-Path $backup 'installation.json'),($evidence | ConvertTo-Json),[Text.UTF8Encoding]::new($false))
Write-Host "Windows candidate installed in the original directory: $installedVersion"
Write-Host "Existing app data files unchanged before launch: $($before.Count). Credentials were not exported or modified."
Write-Host "Protected original program/data backup: $backup"
