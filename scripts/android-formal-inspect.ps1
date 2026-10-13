param([string]$Serial='', [string]$ScreenshotName='')
$ErrorActionPreference='Stop'
$OutputEncoding=[Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding=$OutputEncoding
$projectRoot=Split-Path $PSScriptRoot -Parent
$adb=Join-Path $projectRoot '.desktop-tools/android-sdk/platform-tools/adb.exe'
$package='cn.zijun701.plan.app'
$labelConfig=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'android-formal-inspect-labels.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$owner=[Security.Principal.WindowsIdentity]::GetCurrent()
if($owner.Name -match 'CodexSandbox'){throw 'Run formal device inspection as the actual Windows owner.'}
$devices=@((& $adb devices) | Where-Object {$_ -match '^\S+\s+device$'} | ForEach-Object {($_ -split '\s+')[0]})
if(-not $Serial -and $devices.Count -eq 1){$Serial=$devices[0]}
if(-not $Serial -or $Serial -notin $devices){throw 'Select exactly one authorized device.'}
function Invoke-Adb([string[]]$Arguments){
    $result=& $adb -s $Serial @Arguments
    if($LASTEXITCODE -ne 0){throw 'Device inspection command failed. Private output withheld.'}
    return $result
}
function Assert-Foreground {
    $state=(Invoke-Adb @('shell','dumpsys','activity','activities') | Select-String 'topResumedActivity=') -join ''
    if($state -notmatch 'cn\.zijun701\.plan\.app/'){throw 'Formal Plan Cabin is not foreground. No UI captured.'}
}
Assert-Foreground
$temporary='/data/local/tmp/plan-cabin-formal-inspect-'+[Guid]::NewGuid().ToString('N')
try {
    $null=Invoke-Adb @('shell','uiautomator','dump',"$temporary.xml")
    [xml]$ui=(Invoke-Adb @('shell','cat',"$temporary.xml")) -join "`n"
    $nodes=@($ui.SelectNodes('//node'))
    $authVisible=@($nodes | Where-Object {$_.password -eq 'true' -or $_.text -in $labelConfig.authentication}).Count -gt 0
    if($authVisible){Write-Output 'Authentication page visible. User must log in; no screenshot or input values captured.';return}
    $labels=@($labelConfig.navigation)
    # Only known product controls: omit input values, account names and task text.
    $controls=@($nodes | Where-Object {$_.class -notmatch 'EditText' -and ($_.text -in $labels -or $_.'content-desc' -in $labels)} | ForEach-Object {
        [pscustomobject]@{text=if($_.text -in $labels){$_.text}else{''};description=if($_.'content-desc' -in $labels){$_.'content-desc'}else{''};class=$_.class;bounds=$_.bounds;clickable=$_.clickable}
    })
    $screenshot=''
    if($ScreenshotName){
        if($controls.Count -eq 0){throw 'No known application controls are visible; screenshot withheld.'}
        if($ScreenshotName -notmatch '^[a-zA-Z0-9-]{1,64}$'){throw 'Use a simple screenshot basename.'}
        $directory=Join-Path $projectRoot '.desktop-tools/formal-android/private'
        if(-not (Test-Path -LiteralPath $directory)){
            New-Item -ItemType Directory -Path $directory | Out-Null
            $acl=New-Object Security.AccessControl.DirectorySecurity
            $acl.SetOwner($owner.User);$acl.SetAccessRuleProtection($true,$false)
            foreach($sid in @($owner.User,[Security.Principal.SecurityIdentifier]'S-1-5-18')){$acl.AddAccessRule((New-Object Security.AccessControl.FileSystemAccessRule($sid,'FullControl','ContainerInherit,ObjectInherit','None','Allow')))}
            Set-Acl -LiteralPath $directory -AclObject $acl
        }
        if((Get-Item -LiteralPath $directory).Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'Private capture directory cannot be a link.'}
        $existingAcl=Get-Acl -LiteralPath $directory
        $ownerSid=([Security.Principal.NTAccount]$existingAcl.Owner).Translate([Security.Principal.SecurityIdentifier]).Value
        $allowedSids=@($owner.User.Value,'S-1-5-18')
        if($ownerSid -ne $owner.User.Value -or -not $existingAcl.AreAccessRulesProtected){throw 'Private capture directory ownership or inheritance is unsafe.'}
        foreach($rule in $existingAcl.Access){
            if($rule.AccessControlType -eq 'Allow' -and $rule.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value -notin $allowedSids){throw 'Private capture directory grants access to another identity.'}
        }
        $screenshot=Join-Path $directory ($ScreenshotName+'-'+[Guid]::NewGuid().ToString('N')+'.png')
        Assert-Foreground
        $null=Invoke-Adb @('shell','screencap','-p',"$temporary.png")
        $null=Invoke-Adb @('pull',"$temporary.png",$screenshot)
    }
    [pscustomobject]@{package=$package;authenticationPage=$false;controls=$controls;screenshot=$screenshot} | ConvertTo-Json -Depth 4
} finally {
    # Remove only diagnostic files created by this invocation, never app data.
    $null=& $adb -s $Serial shell rm -f "$temporary.xml" "$temporary.png"
}
