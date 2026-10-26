# Each release must update this explicit affected-area plan with its version.
function Get-ReleaseTestScope {
    $root = Split-Path $PSScriptRoot -Parent
    $plan = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'release-test-scope.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    $version = (Get-Content -LiteralPath (Join-Path $root 'web/package.json') -Raw -Encoding UTF8 | ConvertFrom-Json).version
    if ($plan.version -ne $version -or -not $plan.reason) { throw 'Update the affected-area test plan for this release first.' }
    foreach ($field in @('frontendUnit','browser','backend','android')) {
        if ($null -eq $plan.$field) { throw "Missing test scope: $field" }
    }
    if ($plan.browserConfig -and ($plan.browserConfig -notmatch '^playwright\.[a-zA-Z0-9_-]+\.config\.ts$' -or -not (Test-Path -LiteralPath (Join-Path $root "web/$($plan.browserConfig)")))) { throw 'Invalid selected browser configuration.' }
    if ($plan.browserBackendTarget -and $plan.browserBackendTarget -notmatch '^target-[a-zA-Z0-9-]+$') { throw 'Invalid browser fixture build target.' }
    foreach ($filter in @($plan.windowsRustTests)) { if ($filter -notmatch '^[a-zA-Z0-9_:]+$') { throw 'Invalid selected Rust test filter.' } }
    foreach ($file in @($plan.frontendUnit) + @($plan.browser)) {
        if ($file -notmatch '^(src|e2e)/[a-zA-Z0-9_./-]+\.(test|spec)\.ts$' -or $file.Contains('..') -or -not (Test-Path -LiteralPath (Join-Path $root "web/$file"))) { throw 'Invalid affected frontend test path.' }
    }
    foreach ($name in @($plan.backend) + @($plan.android)) {
        if ($name -notmatch '^[a-zA-Z0-9_.$]+(#[a-zA-Z0-9_]+)?$') { throw 'Invalid affected native/backend test name.' }
    }
    return $plan
}
function Invoke-ReleaseFrontendTests([string]$Scope) {
    $arguments = @('--prefix','web','test')
    if ($Scope -eq 'affected') {
        $plan = Get-ReleaseTestScope
        if (-not @($plan.frontendUnit).Count) { return }
        $arguments += '--'
        $arguments += @($plan.frontendUnit)
    }
    if ($env:OS -eq 'Windows_NT') { & npm.cmd @arguments }
    else { & npm @arguments }
    if ($LASTEXITCODE -ne 0) { throw 'Selected frontend tests failed.' }
}
