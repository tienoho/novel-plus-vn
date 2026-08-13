param(
    [string]$ProjectName = "khoi-thu-e2e",
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
$authDir = Join-Path $e2eRoot ".auth"
$adminSessionDir = Join-Path $authDir "admin-sessions"
$secretDir = Join-Path ([System.IO.Path]::GetTempPath()) "$ProjectName-secrets"
$resolvedTemp = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
$resolvedSecret = [System.IO.Path]::GetFullPath($secretDir)
$resolvedE2e = [System.IO.Path]::GetFullPath($e2eRoot).TrimEnd(
    [System.IO.Path]::DirectorySeparatorChar,
    [System.IO.Path]::AltDirectorySeparatorChar
) + [System.IO.Path]::DirectorySeparatorChar
$resolvedAuth = [System.IO.Path]::GetFullPath($authDir)

if (-not $resolvedSecret.StartsWith($resolvedTemp, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Thư mục secret E2E phải nằm trong thư mục tạm của hệ điều hành."
}
if (-not $resolvedAuth.StartsWith($resolvedE2e, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Thư mục auth E2E phải nằm trong thư mục e2e của repository."
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

function Resolve-Maven {
    if (-not [string]::IsNullOrWhiteSpace($env:MAVEN_CMD)) {
        return $env:MAVEN_CMD
    }
    $command = Get-Command "mvn.cmd" -ErrorAction SilentlyContinue
    if ($null -ne $command) {
        return $command.Source
    }
    throw "Không tìm thấy Maven để tạo session fixture Admin E2E; hãy đặt MAVEN_CMD."
}

function Initialize-AdminSessions {
    $maven = Resolve-Maven
    New-Item -ItemType Directory -Force -Path $adminSessionDir | Out-Null
    & $maven -q -pl novel-admin -am `
        "-Dtest=AdminE2eSessionFixtureTest" `
        "-Dsurefire.failIfNoSpecifiedTests=false" `
        "-Dadmin.e2e.session.fixture=true" `
        "-Dadmin.e2e.session.outputDir=$adminSessionDir" test
    if ($LASTEXITCODE -ne 0) {
        throw "Không thể tạo Shiro session fixture cho Admin E2E."
    }

    $sessionIds = @{}
    Get-Content -LiteralPath (Join-Path $adminSessionDir "sessions.properties") | ForEach-Object {
        $parts = $_ -split "=", 2
        if ($parts.Count -eq 2) {
            $sessionIds[$parts[0]] = $parts[1]
        }
    }
    if ([string]::IsNullOrWhiteSpace($sessionIds.maker) `
            -or [string]::IsNullOrWhiteSpace($sessionIds.checker)) {
        throw "Session fixture Admin E2E không trả đủ maker/checker."
    }

    $redisContainer = "$ProjectName-redis-1"
    $redisPassword = [System.IO.File]::ReadAllText(
        (Join-Path $secretDir "redis_password")
    ).Trim()
    foreach ($actor in @("maker", "checker")) {
        $containerFile = "/tmp/$actor.session"
        & docker cp (Join-Path $adminSessionDir "$actor.session") "${redisContainer}:$containerFile"
        if ($LASTEXITCODE -ne 0) {
            throw "Không thể copy session $actor vào Redis E2E."
        }
        $redisKey = "shiro_redis_session:$($sessionIds[$actor])"
        & docker exec -e "REDISCLI_AUTH=$redisPassword" $redisContainer sh -ec `
            'redis-cli -x SET "$1" < "$2" >/dev/null && redis-cli EXPIRE "$1" 1800 >/dev/null' `
            sh $redisKey $containerFile
        if ($LASTEXITCODE -ne 0) {
            throw "Không thể nạp session $actor vào Redis E2E."
        }
    }
    $env:E2E_ADMIN_MAKER_SESSION = $sessionIds.maker
    $env:E2E_ADMIN_CHECKER_SESSION = $sessionIds.checker
}

$secretNames = @(
    "mysql_root_password",
    "mysql_app_password",
    "redis_password",
    "jwt_secret",
    "cache_manager_password",
    "pii_encryption_key",
    "gamification_vote_ip_hash_salt",
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
    "NOVEL_THEME", "CRAWLER_ADMIN_USERNAME", "GAMIFICATION_CONFIG_SOURCE",
    "GAMIFICATION_VOTE_IP_HASH_KEY_ID", "READING_TICKET_ENABLED",
    "PLAYWRIGHT_BASE_URL", "PLAYWRIGHT_ADMIN_URL", "PLAYWRIGHT_CRAWL_URL",
    "E2E_CRAWLER_USERNAME", "E2E_CRAWLER_PASSWORD", "E2E_ADMIN_MAKER_SESSION",
    "E2E_ADMIN_CHECKER_SESSION", "E2E_THEME"
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
$env:GAMIFICATION_CONFIG_SOURCE = "DB"
$env:GAMIFICATION_VOTE_IP_HASH_KEY_ID = "v1"
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
    $applicationArguments += @("front", "crawl", "admin", "caddy")
    Invoke-Compose $applicationArguments
    foreach ($service in @("front", "crawl", "admin", "caddy")) {
        Wait-ServiceHealthy $service
    }
    Initialize-AdminSessions

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
    if (Test-Path -LiteralPath $resolvedAuth) {
        Remove-Item -LiteralPath $resolvedAuth -Recurse -Force
    }
    foreach ($name in $managedEnvironment) {
        [Environment]::SetEnvironmentVariable($name, $previousEnvironment[$name], "Process")
    }
    if ($cleanupExitCode -ne 0) {
        throw "Docker Compose cleanup E2E thất bại với exit code $cleanupExitCode."
    }
}
