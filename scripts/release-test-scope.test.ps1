$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'release-test-scope.ps1')
$plan = Get-ReleaseTestScope
if (-not @($plan.frontendUnit).Count -or -not @($plan.browser).Count) { throw 'Expected explicit affected frontend selections.' }
function npm.cmd { $script:capturedArguments = @($args); $global:LASTEXITCODE = 0 }
function npm { $script:capturedArguments = @($args); $global:LASTEXITCODE = 0 }
Invoke-ReleaseFrontendTests 'affected'
if (($script:capturedArguments -join '|') -ne ((@('--prefix','web','test','--') + @($plan.frontendUnit)) -join '|')) { throw 'Affected scope accidentally expanded.' }
Invoke-ReleaseFrontendTests 'full'
if (($script:capturedArguments -join '|') -ne '--prefix|web|test') { throw 'Explicit full scope does not run the full suite.' }
Remove-Item Function:npm.cmd
Remove-Item Function:npm
Write-Host 'PASS: explicit affected selections, affected command, explicit full command.'
