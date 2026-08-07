param(
    [string]$ProjectName = "novel-plus-e2e",
    [int]$TimeoutSeconds = 900,
    [int]$MySqlHostPort = 14317,
    [int]$CaddyHttpHostPort = 14480,
    [int]$CaddyHttpsHostPort = 14443,
    [ValidateSet("green", "orange", "dark", "blue")]
    [string[]]$Themes = @("green", "orange", "dark", "blue"),
    [switch]$SkipNpmInstall,
    [switch]$SkipBrowserInstall,
    [switch]$SkipImageBuild
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$e2eRoot = Join-Path $repoRoot "e2e"
$secretDir = Join-Path ([System.IO.Path]::GetTempPath()) "$ProjectName-secrets"
$resolvedTemp = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
$resolvedSecret = [System.IO.Path]::GetFullPath($secretDir)

if (-not $resolvedSecret.StartsWith($resolvedTemp, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Thư mục secret E2E phải nằm trong thư mục tạm của hệ điều hành."
}
if (-not (Test-Path -LiteralPath (Join-Path $e2eRoot "fixtures/seed.sql"))) {
    throw "Thiếu fixture SQL E2E."
}

function New-RandomSecret([int]$byteCount = 48) {
    $bytes = [byte[]]::new($byteCount)
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
        -f "$repoRoot/compose.e2e.yaml" @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose E2E thất bại: $($Arguments -join ' ')"
    }
}

function Wait-Migration {
    $container = "$ProjectName-migrate-1"
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $state = & docker inspect -f "{{.State.Status}}:{{.State.ExitCode}}" $container 2>$null
        if ($state -eq "exited:0") {
            return
        }
        if ($state -match '^exited:' -and $state -ne "exited:0") {
            Invoke-Compose @("logs", "--no-color", "--tail=250", "migrate")
            throw "Flyway E2E thất bại: $state"
        }
        Start-Sleep -Seconds 3
    } while ((Get-Date) -lt $deadline)
    throw "Flyway E2E chưa hoàn tất sau $TimeoutSeconds giây."
}

function Wait-ServiceHealthy([string]$Service) {
    $container = "$ProjectName-$Service-1"
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $state = & docker inspect -f `
            "{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}" `
            $container 2>$null
        # Template trả "running" chỉ khi service không khai báo State.Health; service có
        # healthcheck đang khởi động vẫn trả "starting", nên không bị chấp nhận sớm.
        if ($state -eq "healthy" -or $state -eq "running") {
            return
        }
        if ($state -eq "exited" -or $state -eq "dead") {
            Invoke-Compose @("logs", "--no-color", "--tail=250", $Service)
            throw "Service $Service dừng khi chạy E2E."
        }
        Start-Sleep -Seconds 3
    } while ((Get-Date) -lt $deadline)
    Invoke-Compose @("logs", "--no-color", "--tail=250", $Service)
    throw "Service $Service chưa healthy sau $TimeoutSeconds giây."
}

function Invoke-Npm([string[]]$Arguments) {
    Push-Location $e2eRoot
    try {
        & npm @Arguments
        if ($LASTEXITCODE -ne 0) {
            throw "npm thất bại: npm $($Arguments -join ' ')"
        }
    }
    finally {
        Pop-Location
    }
}

$secretNames = @(
    "mysql_root_password",
    "mysql_app_password",
    "redis_password",
    "jwt_secret",
    "cache_manager_password",
    "pii_encryption_key",
    "admin_bootstrap_password",
    "crawler_admin_password",
    "backup_encryption_password",
    "vnpay_hash_secret",
    "vnpay_recurring_password",
    "vnpay_recurring_client_secret",
    "vnpay_recurring_hash_secret",
    "vietqr_webhook_secret",
    "alertmanager_webhook_url",
    "grafana_admin_password"
)

$managedEnvironment = @(
    "SECRETS_DIR", "MYSQL_HOST_PORT", "CADDY_HTTP_PORT", "CADDY_HTTPS_PORT",
    "NOVEL_DOMAIN", "NOVEL_ADMIN_DOMAIN", "NOVEL_CRAWL_DOMAIN", "NOVEL_GRAFANA_DOMAIN",
    "ALERTMANAGER_ALLOW_HTTP", "CADDY_RATE_LIMIT_REQUESTS", "CADDY_WRITE_RATE_LIMIT_REQUESTS",
    "NOVEL_THEME", "CRAWLER_ADMIN_USERNAME",
    "GAMIFICATION_POLICY_VERSION", "GAMIFICATION_TICKET_ENABLED", "GAMIFICATION_VOTE_ENABLED",
    "GAMIFICATION_SEASON_ENABLED", "GAMIFICATION_VOTE_IP_HASH_SALT", "READING_TICKET_ENABLED",
    "PLAYWRIGHT_BASE_URL", "PLAYWRIGHT_ADMIN_URL", "PLAYWRIGHT_CRAWL_URL",
    "E2E_CRAWLER_USERNAME", "E2E_CRAWLER_PASSWORD", "E2E_THEME"
)
$previousEnvironment = @{}
foreach ($name in $managedEnvironment) {
    $previousEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, "Process")
}

New-Item -ItemType Directory -Force -Path $secretDir | Out-Null
foreach ($secretName in $secretNames) {
    $byteCount = if ($secretName -eq "pii_encryption_key") { 32 } else { 48 }
    [System.IO.File]::WriteAllText(
        (Join-Path $secretDir $secretName),
        (New-RandomSecret $byteCount),
        [System.Text.UTF8Encoding]::new($false)
    )
}
[System.IO.File]::WriteAllText(
    (Join-Path $secretDir "admin_bootstrap_password"),
    "E2eAdmin!2026Secure",
    [System.Text.UTF8Encoding]::new($false)
)
[System.IO.File]::WriteAllText(
    (Join-Path $secretDir "crawler_admin_password"),
    "E2eCrawler!2026Secure",
    [System.Text.UTF8Encoding]::new($false)
)
[System.IO.File]::WriteAllText(
    (Join-Path $secretDir "alertmanager_webhook_url"),
    "http://127.0.0.1:65535/novel-alerts",
    [System.Text.UTF8Encoding]::new($false)
)

$env:SECRETS_DIR = $secretDir
$env:MYSQL_HOST_PORT = [string]$MySqlHostPort
$env:CADDY_HTTP_PORT = [string]$CaddyHttpHostPort
$env:CADDY_HTTPS_PORT = [string]$CaddyHttpsHostPort
$env:NOVEL_DOMAIN = "localhost"
$env:NOVEL_ADMIN_DOMAIN = "admin.localhost"
$env:NOVEL_CRAWL_DOMAIN = "crawl.localhost"
$env:NOVEL_GRAFANA_DOMAIN = "grafana.localhost"
$env:ALERTMANAGER_ALLOW_HTTP = "true"
$env:CADDY_RATE_LIMIT_REQUESTS = "10000"
$env:CADDY_WRITE_RATE_LIMIT_REQUESTS = "2000"
$env:CRAWLER_ADMIN_USERNAME = "admin"
$env:GAMIFICATION_POLICY_VERSION = "v1"
$env:GAMIFICATION_TICKET_ENABLED = "true"
$env:GAMIFICATION_VOTE_ENABLED = "true"
$env:GAMIFICATION_SEASON_ENABLED = "true"
$env:GAMIFICATION_VOTE_IP_HASH_SALT = "e2e-vote-hash-salt-2026-at-least-32-characters"
$env:READING_TICKET_ENABLED = "true"
$env:PLAYWRIGHT_BASE_URL = "https://localhost:$CaddyHttpsHostPort"
$env:PLAYWRIGHT_ADMIN_URL = "https://admin.localhost:$CaddyHttpsHostPort"
$env:PLAYWRIGHT_CRAWL_URL = "https://crawl.localhost:$CaddyHttpsHostPort"
$env:E2E_CRAWLER_USERNAME = "admin"
$env:E2E_CRAWLER_PASSWORD = "E2eCrawler!2026Secure"

$cleanupExitCode = 0
try {
    if (-not $SkipNpmInstall) {
        Invoke-Npm @("ci")
    }
    if (-not $SkipBrowserInstall) {
        Invoke-Npm @("exec", "playwright", "install", "chromium")
    }

    $bootstrapArguments = @("up", "-d")
    if (-not $SkipImageBuild) {
        $bootstrapArguments += "--build"
    }
    $bootstrapArguments += @("mysql", "pushgateway", "migrate", "redis")
    Invoke-Compose $bootstrapArguments
    Wait-Migration
    Wait-ServiceHealthy "mysql"
    Wait-ServiceHealthy "redis"

    $mysqlContainer = "$ProjectName-mysql-1"
    $mysqlRootPassword = [System.IO.File]::ReadAllText(
        (Join-Path $secretDir "mysql_root_password")
    ).Trim()
    $mysqlDatabase = if ($env:MYSQL_DATABASE) { $env:MYSQL_DATABASE } else { "novel_plus" }
    if ($mysqlDatabase -notmatch '^[A-Za-z0-9_]{1,64}$') {
        throw "Tên database E2E không hợp lệ."
    }
    & docker exec -e "MYSQL_PWD=$mysqlRootPassword" $mysqlContainer `
        mysql --default-character-set=utf8mb4 -uroot $mysqlDatabase `
        -e "source /e2e/fixtures/seed.sql"
    if ($LASTEXITCODE -ne 0) {
        throw "Không thể nạp fixture SQL E2E."
    }
    Write-Output "Fixture E2E đã được nạp sau Flyway và trước khi ứng dụng khởi động."

    $env:NOVEL_THEME = $Themes[0]
    $applicationArguments = @("up", "-d")
    if (-not $SkipImageBuild) {
        $applicationArguments += "--build"
    }
    $applicationArguments += @("front", "crawl", "admin", "alertmanager", "prometheus", "grafana", "caddy")
    Invoke-Compose $applicationArguments
    foreach ($service in @("front", "crawl", "admin", "alertmanager", "prometheus", "grafana", "caddy")) {
        Wait-ServiceHealthy $service
    }

    foreach ($theme in $Themes) {
        $env:NOVEL_THEME = $theme
        $env:E2E_THEME = $theme
        Invoke-Compose @("up", "-d", "--no-deps", "--force-recreate", "front")
        Wait-ServiceHealthy "front"

        $frontContainer = "$ProjectName-front-1"
        $actualTheme = & docker inspect -f `
            '{{range .Config.Env}}{{println .}}{{end}}' $frontContainer | Where-Object { $_ -like 'TEMPLATES_NAME=*' }
        if ($actualTheme -ne "TEMPLATES_NAME=$theme") {
            throw "Front không chạy đúng theme $theme; giá trị thực tế: $actualTheme"
        }

        Write-Output "Chạy Playwright cho theme $theme trên desktop và mobile."
        Invoke-Npm @("exec", "playwright", "test")
    }

    Write-Output "Playwright E2E đạt trên các theme đã chọn ($($Themes -join ', ')), desktop và mobile."
}
catch {
    try {
        Invoke-Compose @("ps")
        Invoke-Compose @("logs", "--no-color", "--tail=300", "front", "admin", "crawl", "caddy")
    }
    catch {
        Write-Warning "Không thu thập được đầy đủ log E2E: $($_.Exception.Message)"
    }
    throw
}
finally {
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & docker compose -p $ProjectName `
            -f "$repoRoot/compose.yaml" `
            -f "$repoRoot/compose.test.yaml" `
            -f "$repoRoot/compose.e2e.yaml" down -v --remove-orphans 2>&1 | Out-Null
        $cleanupExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    if (Test-Path -LiteralPath $resolvedSecret) {
        Remove-Item -LiteralPath $resolvedSecret -Recurse -Force
    }
    foreach ($name in $managedEnvironment) {
        [Environment]::SetEnvironmentVariable($name, $previousEnvironment[$name], "Process")
    }
    if ($cleanupExitCode -ne 0) {
        throw "Docker Compose cleanup E2E thất bại với exit code $cleanupExitCode."
    }
}
