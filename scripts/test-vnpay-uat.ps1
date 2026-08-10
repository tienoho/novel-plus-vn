[CmdletBinding()]
param(
    [string]$EnvFile = '.env.uat',
    [ValidateRange(10, 600)]
    [int]$ProviderIpnTimeoutSeconds = 180,
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
if (-not (Test-Path -LiteralPath $resolvedEnvFile -PathType Leaf)) { throw "Thiếu $resolvedEnvFile." }

function Get-EnvValue([string]$Name) {
    $line = Get-Content -Encoding UTF8 $resolvedEnvFile |
        Where-Object { $_ -match "^$([regex]::Escape($Name))=" } |
        Select-Object -Last 1
    if (-not $line) { return $null }
    return ($line -split '=', 2)[1].Trim()
}

$domain = Get-EnvValue 'NOVEL_DOMAIN'
$tmnCode = Get-EnvValue 'VNPAY_TMN_CODE'
$returnUrl = Get-EnvValue 'VNPAY_RETURN_URL'
$xuPerThousandVnd = Get-EnvValue 'VNPAY_XU_PER_1000_VND'
$projectName = Get-EnvValue 'COMPOSE_PROJECT_NAME'
$secretDirectory = Get-EnvValue 'SECRETS_DIR'
if ($tmnCode -notmatch '^[A-Za-z0-9]{8}$' -or `
    $returnUrl -ne "https://$domain/pay/vnpay/return" -or `
    $xuPerThousandVnd -notmatch '^\d+$' -or [int]$xuPerThousandVnd -le 0 -or `
    $projectName -notmatch '^[A-Za-z0-9_-]{3,40}$' -or -not $secretDirectory) {
    throw 'Cấu hình merchant/project/secret UAT không hợp lệ.'
}
if (-not (Test-Path -LiteralPath (Join-Path $e2eRoot 'package-lock.json') -PathType Leaf) -or `
    -not (Test-Path -LiteralPath (Join-Path $PSScriptRoot 'vnpay-sandbox-smoke.mjs') -PathType Leaf)) {
    throw 'Thiếu package-lock hoặc harness VNPAY UAT.'
}
& "$PSScriptRoot/preflight-vnpay-uat.ps1" -EnvFile $EnvFile
$reportPath = Join-Path $repoRoot 'e2e/test-results/vnpay-uat-smoke.json'
$resolvedSecretDirectory = if ([System.IO.Path]::IsPathRooted($secretDirectory)) {
    [System.IO.Path]::GetFullPath($secretDirectory)
} else {
    [System.IO.Path]::GetFullPath((Join-Path $repoRoot $secretDirectory))
}
$rootSecretFile = Join-Path $resolvedSecretDirectory 'mysql_root_password'
if (-not (Test-Path -LiteralPath $rootSecretFile -PathType Leaf)) {
    throw 'Thiếu mysql_root_password của UAT.'
}

function Get-SandboxTestValue([string]$EnvironmentName, [string]$FileName) {
    $value = [Environment]::GetEnvironmentVariable($EnvironmentName, 'Process')
    if ($value) { return $value.Trim() }
    $path = Join-Path $resolvedSecretDirectory $FileName
    if (Test-Path -LiteralPath $path -PathType Leaf) {
        return [System.IO.File]::ReadAllText($path).Trim()
    }
    throw "Thiếu $EnvironmentName hoặc file secret $FileName."
}

$rootPassword = [System.IO.File]::ReadAllText($rootSecretFile).Trim()

function Invoke-MySqlScalar([string]$Sql) {
    $output = & docker exec -e "MYSQL_PWD=$rootPassword" "$projectName-mysql-1" `
        mysql --batch --skip-column-names --default-character-set=utf8mb4 `
        -uroot novel_plus -e $Sql
    if ($LASTEXITCODE -ne 0) { throw "MySQL assertion UAT thất bại: $Sql" }
    $value = ($output | Select-Object -Last 1).Trim()
    if ($value -notmatch '^-?\d+$') { throw "MySQL không trả scalar nguyên: $value" }
    return [long]$value
}

function Invoke-Docker([string[]]$Arguments) {
    & docker @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Docker UAT thất bại: docker $($Arguments -join ' ')"
    }
}

$readerId = 991000000000000001
$balanceBefore = Invoke-MySqlScalar "SELECT account_balance FROM user WHERE id=$readerId"

$managed = @('VNPAY_SMOKE_BASE_URL', 'VNPAY_SMOKE_USERNAME', 'VNPAY_SMOKE_PASSWORD',
    'VNPAY_SMOKE_REPORT_PATH', 'VNPAY_SMOKE_TMN_CODE', 'VNPAY_SMOKE_AMOUNT_VND',
    'VNPAY_SMOKE_MODE', 'VNPAY_SMOKE_EXPECTED_RETURN_URL',
    'VNPAY_SMOKE_IPN_MODE', 'VNPAY_SMOKE_PROVIDER_IPN_TIMEOUT_MS',
    'VNPAY_SANDBOX_CARD_NUMBER', 'VNPAY_SANDBOX_CARD_HOLDER',
    'VNPAY_SANDBOX_CARD_DATE', 'VNPAY_SANDBOX_OTP')
$previous = @{}
foreach ($name in $managed) { $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
try {
    $env:VNPAY_SANDBOX_CARD_NUMBER = Get-SandboxTestValue 'VNPAY_SANDBOX_CARD_NUMBER' 'vnpay_sandbox_card_number'
    $env:VNPAY_SANDBOX_CARD_HOLDER = Get-SandboxTestValue 'VNPAY_SANDBOX_CARD_HOLDER' 'vnpay_sandbox_card_holder'
    $env:VNPAY_SANDBOX_CARD_DATE = Get-SandboxTestValue 'VNPAY_SANDBOX_CARD_DATE' 'vnpay_sandbox_card_date'
    $env:VNPAY_SANDBOX_OTP = Get-SandboxTestValue 'VNPAY_SANDBOX_OTP' 'vnpay_sandbox_otp'
    $env:VNPAY_SMOKE_BASE_URL = "https://$domain"
    $env:VNPAY_SMOKE_USERNAME = '0901000001'
    $env:VNPAY_SMOKE_PASSWORD = 'E2eReader!2026Secure'
    $env:VNPAY_SMOKE_REPORT_PATH = '/work/e2e/test-results/vnpay-uat-smoke.json'
    $env:VNPAY_SMOKE_TMN_CODE = $tmnCode
    $env:VNPAY_SMOKE_AMOUNT_VND = [string]$AmountVnd
    $env:VNPAY_SMOKE_MODE = 'full'
    $env:VNPAY_SMOKE_EXPECTED_RETURN_URL = $returnUrl
    $env:VNPAY_SMOKE_IPN_MODE = 'provider'
    $env:VNPAY_SMOKE_PROVIDER_IPN_TIMEOUT_MS = [string]($ProviderIpnTimeoutSeconds * 1000)
    $nodeModulesVolume = "$projectName-vnpay-uat-node-modules"
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
    $forwardedEnvironment = @(
        'VNPAY_SMOKE_BASE_URL', 'VNPAY_SMOKE_USERNAME', 'VNPAY_SMOKE_PASSWORD',
        'VNPAY_SMOKE_REPORT_PATH', 'VNPAY_SMOKE_TMN_CODE', 'VNPAY_SMOKE_AMOUNT_VND',
        'VNPAY_SMOKE_MODE', 'VNPAY_SMOKE_EXPECTED_RETURN_URL',
        'VNPAY_SMOKE_IPN_MODE', 'VNPAY_SMOKE_PROVIDER_IPN_TIMEOUT_MS',
        'VNPAY_SANDBOX_CARD_NUMBER', 'VNPAY_SANDBOX_CARD_HOLDER',
        'VNPAY_SANDBOX_CARD_DATE', 'VNPAY_SANDBOX_OTP'
    )
    $harnessArguments = @('run', '--rm', '--ipc=host')
    foreach ($name in $forwardedEnvironment) {
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
$outTradeNo = [long]$report.outTradeNo
$expectedXu = [int](($AmountVnd / 1000) * [int]$xuPerThousandVnd)
$balanceAfter = Invoke-MySqlScalar "SELECT account_balance FROM user WHERE id=$readerId"
$walletBalance = Invoke-MySqlScalar "SELECT available_balance FROM wallet_account WHERE owner_type='USER' AND owner_id=$readerId AND account_type='READER_XU'"
$orderCount = Invoke-MySqlScalar "SELECT COUNT(*) FROM order_pay WHERE out_trade_no=$outTradeNo AND pay_channel=4 AND pay_status=1 AND total_amount=$AmountVnd AND account_amount=$expectedXu"
$ledgerCount = Invoke-MySqlScalar "SELECT COUNT(*) FROM ledger_transaction WHERE idempotency_key='VNPAY_TOP_UP:$outTradeNo'"
$zeroSumMismatch = Invoke-MySqlScalar "SELECT COUNT(*) FROM (SELECT ledger_transaction_id FROM wallet_entry GROUP BY ledger_transaction_id HAVING SUM(amount)<>0) mismatch"
$rootPassword = $null
if (-not $report.passed -or -not $report.providerServerDeliveryVerified -or `
    $balanceAfter -ne $balanceBefore + $expectedXu -or $walletBalance -ne $balanceAfter -or `
    $orderCount -ne 1 -or $ledgerCount -ne 1 -or $zeroSumMismatch -ne 0) {
    throw 'UAT chưa chứng minh được provider IPN hoặc settlement/ledger chính xác.'
}
Write-Output "UAT VNPAY đạt: VNPAY gọi IPN, replay=02, Xu +$expectedXu, ledger zero-sum."
