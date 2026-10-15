$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'release-test-scope.ps1')
$plan = Get-ReleaseTestScope
if ($plan.version -ne '0.1.9' -or @($plan.frontendUnit).Count -ne 1 -or $plan.frontendUnit[0] -ne 'src/router/scroll.test.ts') { throw 'Unexpected affected unit selection.' }
if (@($plan.browser).Count -ne 1 -or $plan.browser[0] -ne 'e2e/navigation.spec.ts') { throw 'Unexpected affected browser selection.' }
if (@($plan.backend).Count -or @($plan.android).Count) { throw 'Unchanged backend/Java business tests must not be selected.' }
function npm.cmd { $script:capturedArguments = @($args); $global:LASTEXITCODE = 0 }
Invoke-ReleaseFrontendTests 'affected'
if (($script:capturedArguments -join '|') -ne '--prefix|web|test|--|src/router/scroll.test.ts') { throw 'Affected scope accidentally expanded.' }
Invoke-ReleaseFrontendTests 'full'
if (($script:capturedArguments -join '|') -ne '--prefix|web|test') { throw 'Explicit full scope does not run the full suite.' }
Remove-Item Function:npm.cmd
Write-Host 'PASS: affected selections, unchanged backend/Java excluded, affected command, explicit full command.'
