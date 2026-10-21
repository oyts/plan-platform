param([Parameter(Mandatory=$true)][string]$InstallerSha256,[switch]$ConfirmedClosed)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$target=Join-Path $root '.desktop-tools/reminder-windows-installed'
$installer=Join-Path $root '.desktop-tools/stable-trial-installer/d99026d01b7f48a5baa84baeda9e8874/PlanCabin-Stable-Trial-setup.exe'
$exe=Join-Path $target 'plan-platform-desktop.exe'
$owner=[Security.Principal.WindowsIdentity]::GetCurrent()
if(-not $ConfirmedClosed -or $owner.Name -match 'CodexSandbox'){throw 'Independent test app must be closed; run as its Windows owner.'}
if($InstallerSha256 -notmatch '^[a-f0-9]{64}$' -or (Get-FileHash -LiteralPath $installer).Hash.ToLowerInvariant() -ne $InstallerSha256){throw 'Independent installer hash mismatch'}
if((Get-Item -LiteralPath $target).Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'Linked install directory refused'}
function AssertClosed {
 if(Get-CimInstance Win32_Process | Where-Object {$_.Name -eq 'plan-platform-desktop.exe' -and $_.ExecutablePath -eq $exe}){throw 'Independent test app is still running; nothing was stopped.'}
}
AssertClosed
$beforeVersion=(Get-Item -LiteralPath $exe).VersionInfo.ProductVersion
$backup=Join-Path $root ('.desktop-tools/stable-trial-private/'+[Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $backup | Out-Null
$acl=New-Object Security.AccessControl.DirectorySecurity
$acl.SetOwner($owner.User);$acl.SetAccessRuleProtection($true,$false)
foreach($sid in @($owner.User,[Security.Principal.SecurityIdentifier]'S-1-5-18')){$acl.AddAccessRule((New-Object Security.AccessControl.FileSystemAccessRule($sid,'FullControl','ContainerInherit,ObjectInherit','None','Allow')))}
Set-Acl -LiteralPath $backup -AclObject $acl
Copy-Item -LiteralPath $exe -Destination (Join-Path $backup 'original-test.exe')
$before=@();$roots=@{}
foreach($scope in @('roaming','local')){
 $base=if($scope -eq 'roaming'){$env:APPDATA}else{$env:LOCALAPPDATA}
 $path=Join-Path $base 'cn.zijun701.plan.desktop.regression';$roots[$scope]=$path
 if(-not (Test-Path -LiteralPath $path)){continue}
 $entries=@(Get-Item -LiteralPath $path)+@(Get-ChildItem -LiteralPath $path -Recurse -Force)
 if($entries|Where-Object {$_.Attributes -band [IO.FileAttributes]::ReparsePoint}){throw 'Linked regression data refused'}
 foreach($file in $entries|Where-Object {-not $_.PSIsContainer}){$before+=@{scope=$scope;path=$file.FullName.Substring($path.Length+1);sha256=(Get-FileHash -LiteralPath $file.FullName).Hash.ToLowerInvariant()}}
 & robocopy.exe $path (Join-Path $backup $scope) /E /COPY:DAT /DCOPY:DAT /NFL /NDL /NJH /NJS /NP /R:1 /W:1 | Out-Null
 if($LASTEXITCODE -ge 8){throw 'Regression data backup failed; no install'}
}
foreach($file in $before){if((Get-FileHash -LiteralPath (Join-Path $backup ($file.scope+'/'+$file.path))).Hash.ToLowerInvariant() -ne $file.sha256){throw 'Backup hash mismatch'}}
AssertClosed
$installed=Start-Process -FilePath $installer -ArgumentList "/S /UPDATE /D=$target" -WindowStyle Hidden -Wait -PassThru
if($installed.ExitCode -ne 0){throw 'Independent installer failed; original backup retained'}
$version=(Get-Item -LiteralPath $exe).VersionInfo.ProductVersion
if($version -ne '0.1.19'){throw 'Unexpected installed regression version'}
foreach($file in $before){$path=Join-Path $roots[$file.scope] $file.path;if(-not (Test-Path -LiteralPath $path) -or (Get-FileHash -LiteralPath $path).Hash.ToLowerInvariant() -ne $file.sha256){throw 'Regression data changed before first launch; retain backup and inspect'}}
$report=@{schema=1;versionBefore=$beforeVersion;versionAfter=$version;originalDataFilesUnchanged=$before.Count;installerSha256=$InstallerSha256;installedExeSha256=(Get-FileHash -LiteralPath $exe).Hash.ToLowerInvariant();formalAppChanged=$false;credentialsExported=$false;launched=$false}
$evidence=Join-Path $root 'acceptance-results/stable-trial/pc/2026-10-21T05-12-54-605Z/independent-installation.json'
$report|ConvertTo-Json|Set-Content -LiteralPath $evidence -Encoding UTF8
Write-Output "Independent regression installed: $beforeVersion -> $version; unchanged original files: $($before.Count)"
Write-Output "Protected synthetic backup retained: $backup"
