[CmdletBinding()]
param(
    [string]$EnvFile = '.env.uat',
    [ValidateRange(10000, 500000)]
    [int]$AmountVnd = 10000,
    [switch]$SkipNpmInstall,
    [ValidatePattern('^[A-Za-z0-9][A-Za-z0-9._/:@-]{2,200}$')]
    [string]$NodeImage = 'node:22-alpine',
    [ValidatePattern('^[A-Za-z0-9][A-Za-z0-9._/:@-]{2,200}$')]
    [string]$PlaywrightImage = 'mcr.microsoft.com/playwright:v1.62.1-noble'
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$e2eRoot = Join-Path $repoRoot 'e2e'
$resolvedEnvFile = if ([System.IO.Path]::IsPathRooted($EnvFile)) {
    [System.IO.Path]::GetFullPath($EnvFile)
} else {
    [System.IO.Path]::GetFullPath((Join-Path $repoRoot $EnvFile))
}
if (-not (Test-Path -LiteralPath $resolvedEnvFile -PathType Leaf)) {
    throw "Thiếu $resolvedEnvFile."
}

function Get-EnvValue([string]$Name) {
    $line = Get-Content -Encoding UTF8 $resolvedEnvFile |
        Where-Object { $_ -match "^$([regex]::Escape($Name))=" } |
        Select-Object -Last 1
    if (-not $line) { return $null }
    return ($line -split '=', 2)[1].Trim()
}

function Invoke-Docker([string[]]$Arguments) {
    & docker @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Docker preflight VNPAY thất bại: docker $($Arguments -join ' ')"
    }
}

$domain = Get-EnvValue 'NOVEL_DOMAIN'
$tmnCode = Get-EnvValue 'VNPAY_TMN_CODE'
$returnUrl = Get-EnvValue 'VNPAY_RETURN_URL'
$projectName = Get-EnvValue 'COMPOSE_PROJECT_NAME'
$expectedReturnUrl = "https://$domain/pay/vnpay/return"
if ($domain -notmatch '^(?:[A-Za-z0-9-]+\.)+[A-Za-z]{2,63}$' -or
    $tmnCode -notmatch '^[A-Za-z0-9]{8}$' -or
    $projectName -notmatch '^[A-Za-z0-9_-]{3,40}$' -or
    $returnUrl -ne $expectedReturnUrl) {
    throw 'Domain, merchant, project hoặc Return URL UAT không hợp lệ/không đồng bộ.'
}
if (-not (Test-Path -LiteralPath (Join-Path $e2eRoot 'package-lock.json') -PathType Leaf) -or
    -not (Test-Path -LiteralPath (Join-Path $PSScriptRoot 'vnpay-sandbox-smoke.mjs') -PathType Leaf)) {
    throw 'Thiếu package-lock hoặc harness VNPAY UAT.'
}

$reportPath = Join-Path $repoRoot 'e2e/test-results/vnpay-registration-preflight.json'
$nodeModulesVolume = "$projectName-vnpay-uat-node-modules"
$managed = @(
    'VNPAY_SMOKE_BASE_URL', 'VNPAY_SMOKE_USERNAME', 'VNPAY_SMOKE_PASSWORD',
    'VNPAY_SMOKE_REPORT_PATH', 'VNPAY_SMOKE_TMN_CODE', 'VNPAY_SMOKE_AMOUNT_VND',
    'VNPAY_SMOKE_MODE', 'VNPAY_SMOKE_EXPECTED_RETURN_URL'
)
$previous = @{}
foreach ($name in $managed) {
    $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}
try {
    $env:VNPAY_SMOKE_BASE_URL = "https://$domain"
    $env:VNPAY_SMOKE_USERNAME = '0901000001'
    $env:VNPAY_SMOKE_PASSWORD = 'E2eReader!2026Secure'
    $env:VNPAY_SMOKE_REPORT_PATH = '/work/e2e/test-results/vnpay-registration-preflight.json'
    $env:VNPAY_SMOKE_TMN_CODE = $tmnCode
    $env:VNPAY_SMOKE_AMOUNT_VND = [string]$AmountVnd
    $env:VNPAY_SMOKE_MODE = 'checkout-preflight'
    $env:VNPAY_SMOKE_EXPECTED_RETURN_URL = $returnUrl

    Invoke-Docker @('volume', 'create', $nodeModulesVolume)
    if (-not $SkipNpmInstall) {
        Invoke-Docker @(
            'run', '--rm',
            '--volume', "${repoRoot}:/work",
            '--volume', "${nodeModulesVolume}:/work/e2e/node_modules",
            '--workdir', '/work/e2e',
            $NodeImage, 'npm', 'ci'
        )
    }
    $harnessArguments = @('run', '--rm', '--ipc=host')
    foreach ($name in $managed) {
        $harnessArguments += @('--env', $name)
    }
    $harnessArguments += @(
        '--volume', "${repoRoot}:/work",
        '--volume', "${nodeModulesVolume}:/work/e2e/node_modules",
        '--workdir', '/work',
        $PlaywrightImage, 'node', 'scripts/vnpay-sandbox-smoke.mjs'
    )
    Invoke-Docker $harnessArguments
}
finally {
    foreach ($name in $managed) {
        [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process')
    }
}

$report = Get-Content -Raw -Encoding UTF8 $reportPath | ConvertFrom-Json
if (-not $report.passed -or $report.stage -ne 'CHECKOUT_PREFLIGHT' -or
    -not $report.checkoutUrlVerified -or -not $report.tmnCodeVerified -or
    -not $report.returnUrlVerified -or $report.providerServerDeliveryVerified) {
    throw 'Preflight checkout chưa chứng minh được merchant/amount/Return URL VNPAY.'
}
Write-Output 'Preflight checkout đạt: đăng nhập, tạo đơn và URL VNPAY Sandbox đúng merchant/amount/Return URL.'
Write-Output 'Có thể gửi nội dung đăng ký IPN cho VNPAY.'
