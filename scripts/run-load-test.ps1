[CmdletBinding()]
param(
    [ValidateRange(1, 5000)]
    [int]$VirtualUsers = 500,
    [ValidateRange(0, 1800)]
    [int]$RampSeconds = 30,
    [ValidateRange(1, 7200)]
    [int]$DurationSeconds = 120,
    [ValidateRange(0, 60000)]
    [int]$ThinkTimeMs = 1000,
    [ValidateRange(30, 1800)]
    [int]$TimeoutSeconds = 600,
    [ValidatePattern('^[A-Za-z0-9_-]{3,40}$')]
    [string]$ProjectName = 'novel-load',
    [ValidateRange(1024, 65535)]
    [int]$MySqlHostPort = 13308,
    [ValidateRange(1024, 65535)]
    [int]$CaddyHttpHostPort = 15080,
    [ValidateRange(1024, 65535)]
    [int]$CaddyHttpsHostPort = 15443,
    [switch]$SkipImageBuild,
    [switch]$KeepEnvironment
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$loadRoot = Join-Path $PSScriptRoot 'load-test'
$resultRoot = Join-Path $repoRoot 'e2e/test-results'
$reportPath = Join-Path $resultRoot 'load-test-report.json'
$secretDir = Join-Path ([System.IO.Path]::GetTempPath()) "$ProjectName-secrets"
$resolvedTemp = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
$resolvedSecret = [System.IO.Path]::GetFullPath($secretDir)
$runId = "load$((Get-Date).ToUniversalTime().ToString('yyyyMMddHHmmss'))"

if (-not $resolvedSecret.StartsWith($resolvedTemp, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Thư mục secret load test phải nằm trong thư mục tạm của hệ điều hành."
}
foreach ($requiredFile in @(
    (Join-Path $loadRoot 'load_test_scenario.js'),
    (Join-Path $loadRoot 'fixture.sql'),
    (Join-Path $repoRoot 'compose.load.yaml')
)) {
    if (-not (Test-Path -LiteralPath $requiredFile)) {
        throw "Thiếu tệp load test bắt buộc: $requiredFile"
    }
}

function New-RandomSecret([int]$ByteCount = 48) {
    $bytes = [byte[]]::new($ByteCount)
    $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $generator.GetBytes($bytes)
    }
    finally {
        $generator.Dispose()
    }
    return [Convert]::ToBase64String($bytes)
}

function Invoke-Compose([string[]]$Arguments) {
    & docker compose -p $ProjectName `
        -f "$repoRoot/compose.yaml" `
        -f "$repoRoot/compose.test.yaml" `
        -f "$repoRoot/compose.load.yaml" @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose load test thất bại: $($Arguments -join ' ')"
    }
}

function Wait-Migration {
    $container = "$ProjectName-migrate-1"
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $state = & docker inspect -f '{{.State.Status}}:{{.State.ExitCode}}' $container 2>$null
        if ($state -eq 'exited:0') {
            return
        }
        if ($state -match '^exited:' -and $state -ne 'exited:0') {
            Invoke-Compose @('logs', '--no-color', '--tail=250', 'migrate')
            throw "Flyway load test thất bại: $state"
        }
        Start-Sleep -Seconds 3
    } while ((Get-Date) -lt $deadline)
    throw "Flyway chưa hoàn tất sau $TimeoutSeconds giây."
}

function Wait-ServiceHealthy([string]$Service) {
    $container = "$ProjectName-$Service-1"
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $state = & docker inspect -f `
            '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' `
            $container 2>$null
        if ($state -eq 'healthy' -or $state -eq 'running') {
            return
        }
        if ($state -eq 'exited' -or $state -eq 'dead') {
            Invoke-Compose @('logs', '--no-color', '--tail=250', $Service)
            throw "Service $Service đã dừng trong lúc chuẩn bị load test."
        }
        Start-Sleep -Seconds 3
    } while ((Get-Date) -lt $deadline)
    Invoke-Compose @('logs', '--no-color', '--tail=250', $Service)
    throw "Service $Service chưa healthy sau $TimeoutSeconds giây."
}

function Invoke-MySqlScalar([string]$Sql) {
    $container = "$ProjectName-mysql-1"
    $output = & docker exec -e "MYSQL_PWD=$script:MySqlRootPassword" $container `
        mysql --batch --skip-column-names --default-character-set=utf8mb4 `
        -uroot $script:MySqlDatabase -e $Sql
    if ($LASTEXITCODE -ne 0) {
        throw "MySQL assertion thất bại: $Sql"
    }
    $value = ($output | Select-Object -Last 1).Trim()
    if ($value -notmatch '^-?\d+(?:\.\d+)?$') {
        throw "MySQL không trả scalar số cho assertion: $value"
    }
    return [decimal]$value
}

function Invoke-PrometheusScalar([string]$Query, [int]$RetryCount = 10) {
    $encodedQuery = [System.Uri]::EscapeDataString($Query)
    $url = "http://127.0.0.1:9090/api/v1/query?query=$encodedQuery"
    for ($attempt = 1; $attempt -le $RetryCount; $attempt++) {
        $raw = & docker exec "$ProjectName-prometheus-1" wget -qO- $url 2>$null
        if ($LASTEXITCODE -eq 0 -and $raw) {
            $payload = $raw | ConvertFrom-Json
            if ($payload.status -eq 'success' -and $payload.data.result.Count -gt 0) {
                return [decimal]$payload.data.result[0].value[1]
            }
        }
        Start-Sleep -Seconds 3
    }
    throw "Prometheus chưa có dữ liệu cho truy vấn: $Query"
}

function Assert-Zero([string]$Name, [decimal]$Value) {
    if ($Value -ne 0) {
        throw "$Name phải bằng 0 nhưng giá trị thực tế là $Value."
    }
}

$secretNames = @(
    'mysql_root_password',
    'mysql_app_password',
    'redis_password',
    'jwt_secret',
    'cache_manager_password',
    'pii_encryption_key',
    'admin_bootstrap_password',
    'crawler_admin_password',
    'backup_encryption_password',
    'vnpay_hash_secret',
    'vnpay_recurring_password',
    'vnpay_recurring_client_secret',
    'vnpay_recurring_hash_secret',
    'vietqr_webhook_secret',
    'alertmanager_webhook_url',
    'grafana_admin_password'
)
$managedEnvironment = @(
    'SECRETS_DIR', 'MYSQL_HOST_PORT', 'CADDY_HTTP_PORT', 'CADDY_HTTPS_PORT',
    'NOVEL_DOMAIN', 'NOVEL_ADMIN_DOMAIN', 'NOVEL_CRAWL_DOMAIN', 'NOVEL_GRAFANA_DOMAIN',
    'ALERTMANAGER_ALLOW_HTTP', 'CADDY_RATE_LIMIT_REQUESTS', 'CADDY_WRITE_RATE_LIMIT_REQUESTS',
    'NOVEL_THEME', 'CRAWLER_ADMIN_USERNAME', 'GAMIFICATION_VOTE_IP_HASH_SALT',
    'NODE_TLS_REJECT_UNAUTHORIZED', 'LOAD_BASE_URL', 'LOAD_VUS', 'LOAD_RAMP_SECONDS',
    'LOAD_DURATION_SECONDS', 'LOAD_THINK_TIME_MS', 'LOAD_REPORT_PATH', 'LOAD_RUN_ID'
)
$previousEnvironment = @{}
foreach ($name in $managedEnvironment) {
    $previousEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}

New-Item -ItemType Directory -Force -Path $secretDir, $resultRoot | Out-Null
foreach ($secretName in $secretNames) {
    $byteCount = if ($secretName -eq 'pii_encryption_key') { 32 } else { 48 }
    [System.IO.File]::WriteAllText(
        (Join-Path $secretDir $secretName),
        (New-RandomSecret $byteCount),
        [System.Text.UTF8Encoding]::new($false)
    )
}
[System.IO.File]::WriteAllText(
    (Join-Path $secretDir 'admin_bootstrap_password'),
    'LoadAdmin!2026Secure',
    [System.Text.UTF8Encoding]::new($false)
)
[System.IO.File]::WriteAllText(
    (Join-Path $secretDir 'crawler_admin_password'),
    'LoadCrawler!2026Secure',
    [System.Text.UTF8Encoding]::new($false)
)
[System.IO.File]::WriteAllText(
    (Join-Path $secretDir 'alertmanager_webhook_url'),
    'http://127.0.0.1:65535/novel-alerts',
    [System.Text.UTF8Encoding]::new($false)
)

$script:MySqlRootPassword = [System.IO.File]::ReadAllText(
    (Join-Path $secretDir 'mysql_root_password')
).Trim()
$script:MySqlDatabase = if ($env:MYSQL_DATABASE) { $env:MYSQL_DATABASE } else { 'novel_plus' }
if ($script:MySqlDatabase -notmatch '^[A-Za-z0-9_]{1,64}$') {
    throw "Tên database load test không hợp lệ."
}

$env:SECRETS_DIR = $secretDir
$env:MYSQL_HOST_PORT = [string]$MySqlHostPort
$env:CADDY_HTTP_PORT = [string]$CaddyHttpHostPort
$env:CADDY_HTTPS_PORT = [string]$CaddyHttpsHostPort
$env:NOVEL_DOMAIN = 'localhost'
$env:NOVEL_ADMIN_DOMAIN = 'admin.localhost'
$env:NOVEL_CRAWL_DOMAIN = 'crawl.localhost'
$env:NOVEL_GRAFANA_DOMAIN = 'grafana.localhost'
$env:ALERTMANAGER_ALLOW_HTTP = 'true'
# Một load generator chỉ có một IP. Nâng giới hạn proxy để mô phỏng 500 IP người dùng,
# không biến rate limiter theo IP thành bottleneck giả của bài đo application/database.
$env:CADDY_RATE_LIMIT_REQUESTS = '1000000'
$env:CADDY_WRITE_RATE_LIMIT_REQUESTS = '1000000'
$env:NOVEL_THEME = 'green'
$env:CRAWLER_ADMIN_USERNAME = 'admin'
$env:GAMIFICATION_VOTE_IP_HASH_SALT = 'load-test-vote-hash-salt-2026-at-least-32-characters'
$env:NODE_TLS_REJECT_UNAUTHORIZED = '0'
$env:LOAD_BASE_URL = "https://localhost:$CaddyHttpsHostPort"
$env:LOAD_VUS = [string]$VirtualUsers
$env:LOAD_RAMP_SECONDS = [string]$RampSeconds
$env:LOAD_DURATION_SECONDS = [string]$DurationSeconds
$env:LOAD_THINK_TIME_MS = [string]$ThinkTimeMs
$env:LOAD_REPORT_PATH = $reportPath
$env:LOAD_RUN_ID = $runId

$cleanupExitCode = 0
try {
    $bootstrapArguments = @('up', '-d')
    if (-not $SkipImageBuild) {
        $bootstrapArguments += '--build'
    }
    $bootstrapArguments += @('mysql', 'pushgateway', 'migrate', 'redis')
    Invoke-Compose $bootstrapArguments
    Wait-Migration
    Wait-ServiceHealthy 'mysql'
    Wait-ServiceHealthy 'redis'

    & docker exec -e "MYSQL_PWD=$script:MySqlRootPassword" "$ProjectName-mysql-1" `
        mysql --default-character-set=utf8mb4 -uroot $script:MySqlDatabase `
        -e 'source /load-test/fixture.sql'
    if ($LASTEXITCODE -ne 0) {
        throw "Không thể nạp fixture load test."
    }

    $applicationArguments = @('up', '-d')
    if (-not $SkipImageBuild) {
        $applicationArguments += '--build'
    }
    $applicationArguments += @(
        'front', 'crawl', 'admin', 'alertmanager', 'prometheus', 'grafana', 'caddy'
    )
    Invoke-Compose $applicationArguments
    foreach ($service in @(
        'front', 'crawl', 'admin', 'alertmanager', 'prometheus', 'grafana', 'caddy'
    )) {
        Wait-ServiceHealthy $service
    }

    $visitCountBefore = Invoke-MySqlScalar `
        "SELECT visit_count FROM book WHERE id = 990000000000000001"

    & node (Join-Path $loadRoot 'load_test_scenario.js')
    $loadExitCode = $LASTEXITCODE
    if (-not (Test-Path -LiteralPath $reportPath)) {
        throw "Harness không tạo báo cáo JSON."
    }
    $report = Get-Content -Raw -LiteralPath $reportPath | ConvertFrom-Json

    $visitCountAfter = Invoke-MySqlScalar `
        "SELECT visit_count FROM book WHERE id = 990000000000000001"
    $visitDelta = $visitCountAfter - $visitCountBefore
    if ($visitDelta -ne [decimal]$report.categories.write.success) {
        throw "Số lượt ghi thành công ($($report.categories.write.success)) không khớp delta DB ($visitDelta)."
    }

    $zeroSumMismatch = Invoke-MySqlScalar @'
SELECT COUNT(*) FROM (
  SELECT ledger_transaction_id
  FROM wallet_entry
  GROUP BY ledger_transaction_id
  HAVING SUM(amount) <> 0
) mismatch
'@
    $walletProjectionMismatch = Invoke-MySqlScalar @'
SELECT COUNT(*)
FROM user u
JOIN wallet_account wa ON wa.owner_id = u.id
WHERE wa.owner_type = 'USER'
  AND wa.account_type = 'READER_XU'
  AND wa.available_balance <> u.account_balance
'@
    $monthlyTicketDrift = Invoke-MySqlScalar @'
SELECT COUNT(*) FROM (
  SELECT a.user_id
  FROM monthly_ticket_account a
  LEFT JOIN monthly_ticket_lot l ON l.user_id = a.user_id AND l.status = 'ACTIVE'
  GROUP BY a.user_id, a.available_balance
  HAVING a.available_balance <> COALESCE(SUM(l.remaining_amount), 0)
) drift
'@
    $readingTicketDrift = Invoke-MySqlScalar @'
SELECT COUNT(*) FROM (
  SELECT a.user_id
  FROM reading_ticket_account a
  LEFT JOIN reading_ticket_lot l ON l.user_id = a.user_id AND l.status = 'ACTIVE'
  GROUP BY a.user_id, a.available_balance
  HAVING a.available_balance <> COALESCE(SUM(l.remaining_amount), 0)
) drift
'@
    $duplicateAnalyticsId = Invoke-MySqlScalar @'
SELECT COUNT(*) FROM (
  SELECT client_event_id
  FROM reader_chapter_event
  GROUP BY client_event_id
  HAVING COUNT(*) > 1
) duplicate_event
'@

    Assert-Zero "Sai lệch zero-sum ledger" $zeroSumMismatch
    Assert-Zero "Sai lệch projection ví Xu" $walletProjectionMismatch
    Assert-Zero "Sai lệch projection Ngọn Đuốc" $monthlyTicketDrift
    Assert-Zero "Sai lệch projection Vé đọc" $readingTicketDrift
    Assert-Zero "Client event bị ghi trùng" $duplicateAnalyticsId

    # Chờ thêm hai chu kỳ scrape để Prometheus ghi nhận đỉnh trong giai đoạn cuối bài tải.
    Start-Sleep -Seconds 30
    $windowMinutes = [Math]::Max(3, [Math]::Ceiling(($RampSeconds + $DurationSeconds + 90) / 60))
    $peakActive = Invoke-PrometheusScalar `
        "max_over_time(hikaricp_connections_active{application=`"novel-front`"}[$($windowMinutes)m])"
    $poolMax = Invoke-PrometheusScalar `
        "max_over_time(hikaricp_connections_max{application=`"novel-front`"}[$($windowMinutes)m])"
    $peakPending = Invoke-PrometheusScalar `
        "max_over_time(hikaricp_connections_pending{application=`"novel-front`"}[$($windowMinutes)m])"
    $connectionTimeouts = Invoke-PrometheusScalar `
        "increase(hikaricp_connections_timeout_total{application=`"novel-front`"}[$($windowMinutes)m])"

    Assert-Zero "Số lần timeout chờ connection Hikari" $connectionTimeouts
    Assert-Zero "Đỉnh số request chờ connection Hikari" $peakPending
    if ($peakActive -gt $poolMax) {
        throw "Metric Hikari không hợp lệ: active=$peakActive lớn hơn max=$poolMax."
    }

    Write-Output "Load test: $VirtualUsers VU, read p95=$($report.categories.read.p95Ms) ms, discover p95=$($report.categories.discover.p95Ms) ms, write p95=$($report.categories.write.p95Ms) ms."
    Write-Output "Error rate=$([Math]::Round([double]$report.total.errorRate * 100, 4))%; workload=$([Math]::Round([double]$report.workloadMix.read * 100, 2))/$([Math]::Round([double]$report.workloadMix.discover * 100, 2))/$([Math]::Round([double]$report.workloadMix.write * 100, 2))."
    Write-Output "Hikari novel-front: peak active=$peakActive/$poolMax, peak pending=$peakPending, timeout=$connectionTimeouts."
    Write-Output "DB assertions: visit delta=$visitDelta; ledger/projection/idempotency đều không có sai lệch."
    Write-Output "Báo cáo: $reportPath"

    if ($loadExitCode -ne 0 -or -not $report.passed) {
        throw "Load test không đạt một hoặc nhiều SLO; xem $reportPath"
    }
}
catch {
    try {
        Invoke-Compose @('ps')
        Invoke-Compose @('logs', '--no-color', '--tail=300', 'front', 'mysql', 'caddy', 'prometheus')
    }
    catch {
        Write-Warning "Không thu thập được đầy đủ log load test: $($_.Exception.Message)"
    }
    throw
}
finally {
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        if (-not $KeepEnvironment) {
            & docker compose -p $ProjectName `
                -f "$repoRoot/compose.yaml" `
                -f "$repoRoot/compose.test.yaml" `
                -f "$repoRoot/compose.load.yaml" down -v --remove-orphans 2>&1 | Out-Null
            $cleanupExitCode = $LASTEXITCODE
        }
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    if (Test-Path -LiteralPath $resolvedSecret) {
        Remove-Item -LiteralPath $resolvedSecret -Recurse -Force
    }
    foreach ($name in $managedEnvironment) {
        [Environment]::SetEnvironmentVariable($name, $previousEnvironment[$name], 'Process')
    }
    if ($cleanupExitCode -ne 0) {
        throw "Docker Compose cleanup load test thất bại với exit code $cleanupExitCode."
    }
}
