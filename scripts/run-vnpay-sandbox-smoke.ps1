[CmdletBinding()]
param(
    [ValidatePattern('^[A-Za-z0-9_-]{3,40}$')]
    [string]$ProjectName = 'novel-vnpay-sandbox',
    [ValidateRange(30, 1800)]
    [int]$TimeoutSeconds = 600,
    [ValidateRange(1024, 65535)]
    [int]$MySqlHostPort = 13309,
    [ValidateRange(1024, 65535)]
    [int]$FrontHostPort = 18083,
    [string]$TmnCode = $env:VNPAY_SANDBOX_TMN_CODE,
    [ValidateRange(1000, 500000000)]
    [int]$AmountVnd = 10000,
    [string]$HashSecretFile = $env:VNPAY_SANDBOX_HASH_SECRET_FILE,
    [switch]$SkipImageBuild,
    [switch]$SkipNpmInstall,
    [switch]$SkipBrowserInstall,
    [switch]$CheckoutPreflightOnly,
    [switch]$KeepEnvironment
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$e2eRoot = Join-Path $repoRoot 'e2e'
$secretDir = Join-Path ([System.IO.Path]::GetTempPath()) "$ProjectName-secrets"
$reportPath = Join-Path $e2eRoot 'test-results/vnpay-sandbox-smoke.json'
$readerId = 991000000000000001
$readerUsername = '0901000001'
$readerPassword = 'E2eReader!2026Secure'
$expectedXu = [int](($AmountVnd / 1000) * 100)

if ($TmnCode -notmatch '^[A-Za-z0-9]{8}$') {
    throw 'Cần truyền -TmnCode hoặc VNPAY_SANDBOX_TMN_CODE gồm đúng 8 ký tự chữ/số.'
}
if ($HashSecretFile -and (Test-Path -LiteralPath $HashSecretFile -PathType Leaf)) {
    $hashSecret = [System.IO.File]::ReadAllText((Resolve-Path $HashSecretFile)).Trim()
}
elseif ($env:VNPAY_SANDBOX_HASH_SECRET) {
    $hashSecret = $env:VNPAY_SANDBOX_HASH_SECRET.Trim()
}
else {
    throw 'Cần file secret ngoài Git hoặc biến VNPAY_SANDBOX_HASH_SECRET cho tiến trình one-shot.'
}
if ($hashSecret.Length -lt 32 -or $hashSecret -match '\s') {
    throw 'Secret checksum VNPAY Sandbox không hợp lệ.'
}
if (-not (Test-Path -LiteralPath (Join-Path $e2eRoot 'fixtures/seed.sql'))) {
    throw 'Thiếu fixture SQL E2E.'
}
if (-not (Test-Path -LiteralPath (Join-Path $PSScriptRoot 'vnpay-sandbox-smoke.mjs'))) {
    throw 'Thiếu Node harness VNPAY Sandbox.'
}

function New-RandomSecret([int]$ByteCount = 48) {
    $bytes = [byte[]]::new($ByteCount)
    $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $generator.GetBytes($bytes) } finally { $generator.Dispose() }
    return [Convert]::ToBase64String($bytes)
}

function Invoke-Compose([string[]]$Arguments) {
    & docker compose -p $ProjectName `
        -f "$repoRoot/compose.yaml" `
        -f "$repoRoot/compose.test.yaml" `
        -f "$repoRoot/compose.e2e.yaml" `
        -f "$repoRoot/compose.vnpay-sandbox.yaml" @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose VNPAY Sandbox thất bại: $($Arguments -join ' ')"
    }
}

function Wait-Migration {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $state = & docker inspect -f '{{.State.Status}}:{{.State.ExitCode}}' "$ProjectName-migrate-1" 2>$null
        if ($state -eq 'exited:0') { return }
        if ($state -match '^exited:' -and $state -ne 'exited:0') {
            Invoke-Compose @('logs', '--no-color', '--tail=250', 'migrate')
            throw "Flyway Sandbox thất bại: $state"
        }
        Start-Sleep -Seconds 3
    } while ((Get-Date) -lt $deadline)
    throw "Flyway chưa hoàn tất sau $TimeoutSeconds giây."
}

function Wait-Healthy([string]$Service) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $state = & docker inspect -f `
            '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' `
            "$ProjectName-$Service-1" 2>$null
        if ($state -eq 'healthy' -or $state -eq 'running') { return }
        if ($state -in @('exited', 'dead')) {
            Invoke-Compose @('logs', '--no-color', '--tail=250', $Service)
            throw "Service $Service đã dừng."
        }
        Start-Sleep -Seconds 3
    } while ((Get-Date) -lt $deadline)
    throw "Service $Service chưa healthy sau $TimeoutSeconds giây."
}

function Invoke-MySqlScalar([string]$Sql) {
    $output = & docker exec -e "MYSQL_PWD=$script:MySqlRootPassword" "$ProjectName-mysql-1" `
        mysql --batch --skip-column-names --default-character-set=utf8mb4 `
        -uroot novel_plus -e $Sql
    if ($LASTEXITCODE -ne 0) { throw "MySQL assertion thất bại: $Sql" }
    $value = ($output | Select-Object -Last 1).Trim()
    if ($value -notmatch '^-?\d+$') { throw "MySQL không trả scalar nguyên: $value" }
    return [long]$value
}

$secretNames = @(
    'mysql_root_password', 'mysql_app_password', 'redis_password', 'jwt_secret',
    'cache_manager_password', 'pii_encryption_key', 'admin_bootstrap_password',
    'crawler_admin_password', 'backup_encryption_password', 'vnpay_hash_secret',
    'vnpay_recurring_password', 'vnpay_recurring_client_secret',
    'vnpay_recurring_hash_secret', 'vietqr_webhook_secret',
    'alertmanager_webhook_url', 'grafana_admin_password'
)
$managedEnvironment = @(
    'SECRETS_DIR', 'MYSQL_DATABASE', 'MYSQL_HOST_PORT', 'VNPAY_SANDBOX_FRONT_PORT', 'NOVEL_THEME',
    'VNPAY_ENABLED', 'VNPAY_TMN_CODE', 'VNPAY_RETURN_URL',
    'VNPAY_RECONCILIATION_ENABLED', 'VNPAY_SMOKE_BASE_URL', 'VNPAY_SMOKE_USERNAME',
    'VNPAY_SMOKE_PASSWORD', 'VNPAY_SMOKE_REPORT_PATH', 'VNPAY_SMOKE_TMN_CODE',
    'VNPAY_SMOKE_AMOUNT_VND', 'VNPAY_SMOKE_MODE', 'VNPAY_SMOKE_EXPECTED_RETURN_URL',
    'VNPAY_SANDBOX_CARD_NUMBER', 'VNPAY_SANDBOX_CARD_HOLDER',
    'VNPAY_SANDBOX_CARD_DATE', 'VNPAY_SANDBOX_OTP'
)
$previousEnvironment = @{}
foreach ($name in $managedEnvironment) {
    $previousEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}

New-Item -ItemType Directory -Force -Path $secretDir, (Split-Path -Parent $reportPath) | Out-Null
foreach ($name in $secretNames) {
    $bytes = if ($name -eq 'pii_encryption_key') { 32 } else { 48 }
    [System.IO.File]::WriteAllText((Join-Path $secretDir $name), (New-RandomSecret $bytes),
        [System.Text.UTF8Encoding]::new($false))
}
[System.IO.File]::WriteAllText((Join-Path $secretDir 'vnpay_hash_secret'), $hashSecret,
    [System.Text.UTF8Encoding]::new($false))
[System.IO.File]::WriteAllText((Join-Path $secretDir 'alertmanager_webhook_url'),
    'http://127.0.0.1:65535/novel-alerts', [System.Text.UTF8Encoding]::new($false))
$hashSecret = $null

$script:MySqlRootPassword = [System.IO.File]::ReadAllText(
    (Join-Path $secretDir 'mysql_root_password')).Trim()
$env:SECRETS_DIR = $secretDir
$env:MYSQL_DATABASE = 'novel_plus'
$env:MYSQL_HOST_PORT = [string]$MySqlHostPort
$env:VNPAY_SANDBOX_FRONT_PORT = [string]$FrontHostPort
$env:NOVEL_THEME = 'green'
$env:VNPAY_ENABLED = 'true'
$env:VNPAY_TMN_CODE = $TmnCode
$env:VNPAY_RETURN_URL = "http://localhost:$FrontHostPort/pay/vnpay/return"
$env:VNPAY_RECONCILIATION_ENABLED = 'false'
$env:VNPAY_SMOKE_BASE_URL = "http://localhost:$FrontHostPort"
$env:VNPAY_SMOKE_USERNAME = $readerUsername
$env:VNPAY_SMOKE_PASSWORD = $readerPassword
$env:VNPAY_SMOKE_REPORT_PATH = $reportPath
$env:VNPAY_SMOKE_TMN_CODE = $TmnCode
$env:VNPAY_SMOKE_AMOUNT_VND = [string]$AmountVnd
$env:VNPAY_SMOKE_MODE = if ($CheckoutPreflightOnly) { 'checkout-preflight' } else { 'full' }
$env:VNPAY_SMOKE_EXPECTED_RETURN_URL = $env:VNPAY_RETURN_URL

try {
    if (-not $SkipNpmInstall) {
        Push-Location $e2eRoot
        try { & npm ci; if ($LASTEXITCODE -ne 0) { throw 'npm ci thất bại.' } } finally { Pop-Location }
    }
    if (-not $SkipBrowserInstall) {
        Push-Location $e2eRoot
        try {
            & npm exec playwright install chromium
            if ($LASTEXITCODE -ne 0) { throw 'Cài Chromium Playwright thất bại.' }
        } finally { Pop-Location }
    }

    $bootstrap = @('up', '-d')
    if (-not $SkipImageBuild) { $bootstrap += '--build' }
    $bootstrap += @('mysql', 'pushgateway', 'migrate', 'redis')
    Invoke-Compose $bootstrap
    Wait-Migration
    Wait-Healthy 'mysql'
    Wait-Healthy 'redis'

    & docker exec -e "MYSQL_PWD=$script:MySqlRootPassword" "$ProjectName-mysql-1" `
        mysql --default-character-set=utf8mb4 -uroot novel_plus -e 'source /e2e/fixtures/seed.sql'
    if ($LASTEXITCODE -ne 0) { throw 'Không nạp được fixture VNPAY Sandbox.' }
    $balanceBefore = Invoke-MySqlScalar "SELECT account_balance FROM user WHERE id=$readerId"

    $front = @('up', '-d')
    if (-not $SkipImageBuild) { $front += '--build' }
    $front += 'front'
    Invoke-Compose $front
    Wait-Healthy 'front'

    & node (Join-Path $PSScriptRoot 'vnpay-sandbox-smoke.mjs')
    if ($LASTEXITCODE -ne 0) { throw 'Playwright VNPAY Sandbox smoke thất bại.' }
    $report = Get-Content -Raw -Encoding UTF8 $reportPath | ConvertFrom-Json
    if (-not $report.passed -or $report.outTradeNo -notmatch '^\d+$') {
        throw 'Report VNPAY Sandbox không hợp lệ.'
    }
    $outTradeNo = [long]$report.outTradeNo
    $balanceAfter = Invoke-MySqlScalar "SELECT account_balance FROM user WHERE id=$readerId"
    $walletBalance = Invoke-MySqlScalar "SELECT available_balance FROM wallet_account WHERE owner_type='USER' AND owner_id=$readerId AND account_type='READER_XU'"
    $ledgerCount = Invoke-MySqlScalar "SELECT COUNT(*) FROM ledger_transaction WHERE idempotency_key='VNPAY_TOP_UP:$outTradeNo'"
    $zeroSumMismatch = Invoke-MySqlScalar "SELECT COUNT(*) FROM (SELECT ledger_transaction_id FROM wallet_entry GROUP BY ledger_transaction_id HAVING SUM(amount)<>0) mismatch"
    if ($CheckoutPreflightOnly) {
        $pendingOrderCount = Invoke-MySqlScalar "SELECT COUNT(*) FROM order_pay WHERE out_trade_no=$outTradeNo AND pay_channel=4 AND pay_status=2 AND total_amount=$AmountVnd AND account_amount=$expectedXu"
        if ($report.stage -ne 'CHECKOUT_PREFLIGHT' -or -not $report.checkoutUrlVerified -or
            -not $report.tmnCodeVerified -or -not $report.returnUrlVerified -or
            $balanceAfter -ne $balanceBefore -or $walletBalance -ne $balanceBefore -or
            $pendingOrderCount -ne 1 -or $ledgerCount -ne 0 -or $zeroSumMismatch -ne 0) {
            throw 'Assertion checkout-preflight VNPAY Sandbox thất bại.'
        }
        Write-Output 'Checkout-preflight Sandbox đạt: URL đúng và đơn vẫn pending, chưa cộng Xu/chưa ghi ledger.'
        Write-Output "Báo cáo: $reportPath"
        return
    }
    $orderCount = Invoke-MySqlScalar "SELECT COUNT(*) FROM order_pay WHERE out_trade_no=$outTradeNo AND pay_channel=4 AND pay_status=1 AND total_amount=$AmountVnd AND account_amount=$expectedXu"
    if ($balanceAfter -ne $balanceBefore + $expectedXu -or `
        $walletBalance -ne $balanceAfter -or `
        $orderCount -ne 1 -or $ledgerCount -ne 1 -or $zeroSumMismatch -ne 0) {
        throw 'Assertion settlement/ledger VNPAY Sandbox thất bại.'
    }

    $report | Add-Member -NotePropertyName balanceBefore -NotePropertyValue $balanceBefore
    $report | Add-Member -NotePropertyName balanceAfter -NotePropertyValue $balanceAfter
    $report | Add-Member -NotePropertyName expectedXu -NotePropertyValue $expectedXu
    $report | Add-Member -NotePropertyName ledgerZeroSum -NotePropertyValue $true
    $report | ConvertTo-Json -Depth 6 | Set-Content -Encoding UTF8 $reportPath
    Write-Output "VNPAY Sandbox smoke đạt: Return=00, IPN=00, replay=02, Xu +$expectedXu, ledger zero-sum."
    Write-Output 'Giới hạn: IPN do harness chuyển tiếp payload VNPAY ký; chưa xác minh VNPAY gọi URL HTTPS public.'
    Write-Output "Báo cáo: $reportPath"
}
finally {
    $cleanupError = $null
    if (-not $KeepEnvironment) {
        try { Invoke-Compose @('down', '-v', '--remove-orphans') } catch { $cleanupError = $_ }
    }
    if (Test-Path -LiteralPath $secretDir) {
        Remove-Item -LiteralPath $secretDir -Recurse -Force
    }
    foreach ($name in $managedEnvironment) {
        [Environment]::SetEnvironmentVariable($name, $previousEnvironment[$name], 'Process')
    }
    if ($cleanupError) { throw $cleanupError }
}
