param(
    [string]$ProjectName = "khoi-thu-admin-smoke",
    [int]$TimeoutSeconds = 600,
    [int]$MySqlHostPort = 13317,
    [int]$CaddyHttpHostPort = 13480,
    [int]$CaddyHttpsHostPort = 13443,
    [switch]$SkipImageBuild,
    [switch]$VerifyBackupRestore,
    [string]$ReleaseEnvFile = ""
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$secretDir = Join-Path ([System.IO.Path]::GetTempPath()) $ProjectName
$resolvedTemp = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
$resolvedSecret = [System.IO.Path]::GetFullPath($secretDir)
$backupDir = Join-Path ([System.IO.Path]::GetTempPath()) "$ProjectName-backups"
$resolvedBackup = [System.IO.Path]::GetFullPath($backupDir)
$composeGlobalArguments = @()

if (-not [string]::IsNullOrWhiteSpace($ReleaseEnvFile)) {
    $releaseEnvPath = $ReleaseEnvFile
    if (-not [System.IO.Path]::IsPathRooted($releaseEnvPath)) {
        $releaseEnvPath = Join-Path $repoRoot $releaseEnvPath
    }
    $releaseEnvPath = [System.IO.Path]::GetFullPath($releaseEnvPath)
    if (-not (Test-Path -LiteralPath $releaseEnvPath -PathType Leaf)) {
        throw "Release env file does not exist: $releaseEnvPath"
    }
    $composeGlobalArguments += @("--env-file", $releaseEnvPath)
}
$composeGlobalArguments += @(
    "-p", $ProjectName,
    "-f", "$repoRoot/compose.yaml",
    "-f", "$repoRoot/compose.test.yaml"
)

if (-not $resolvedSecret.StartsWith($resolvedTemp, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Thư mục secret smoke phải nằm trong thư mục tạm của hệ điều hành."
}
if (-not $resolvedBackup.StartsWith($resolvedTemp, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Backup smoke directory must stay inside the operating system temp directory."
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
    & docker compose @composeGlobalArguments @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose thất bại: $($Arguments -join ' ')"
    }
}

function Get-ContainerHttpResponse {
    param(
        [string]$Container,
        [string]$Url,
        [switch]$CrawlerBasicAuth
    )

    if ($CrawlerBasicAuth) {
        $raw = & docker exec $Container sh -ec `
            'password=$(cat /run/secrets/crawler_admin_password); exec curl --silent --show-error --include --user "$CRAWLER_ADMIN_USERNAME:$password" "$1"' `
            sh $Url
    }
    else {
        $raw = & docker exec $Container curl --silent --show-error --include $Url
    }
    if ($LASTEXITCODE -ne 0) {
        throw "HTTP request failed for $Container at $Url."
    }
    return ($raw -join "`n")
}

function Assert-EnforcedCsp {
    param(
        [string]$Container,
        [string]$Url,
        [switch]$CrawlerBasicAuth
    )

    $response = Get-ContainerHttpResponse -Container $Container -Url $Url -CrawlerBasicAuth:$CrawlerBasicAuth
    if ($response -notmatch '(?im)^HTTP/\S+\s+200\b') {
        throw "Expected HTTP 200 from $Container at $Url."
    }
    if ($response -match '(?im)^Content-Security-Policy-Report-Only\s*:') {
        throw "Report-only CSP is still active for $Container at $Url."
    }
    $csp = [regex]::Match($response, '(?im)^Content-Security-Policy\s*:\s*([^\r\n]+)$')
    if (-not $csp.Success) {
        throw "Missing enforced CSP header for $Container at $Url."
    }
    $headerNonce = [regex]::Match($csp.Groups[1].Value, "'nonce-([A-Za-z0-9_-]{20,128})'")
    if (-not $headerNonce.Success) {
        throw "Missing valid script nonce in CSP header for $Container at $Url."
    }

    $scriptTags = [regex]::Matches($response, '(?is)<script\b[^>]*>')
    if ($scriptTags.Count -eq 0) {
        throw "Rendered response has no script element to verify for $Container at $Url."
    }
    foreach ($scriptTag in $scriptTags) {
        $elementNonce = [regex]::Match($scriptTag.Value, '\bnonce=["'']([A-Za-z0-9_-]{20,128})["'']')
        if (-not $elementNonce.Success -or $elementNonce.Groups[1].Value -ne $headerNonce.Groups[1].Value) {
            throw "Rendered script nonce does not match CSP header for $Container at $Url."
        }
    }

    Write-Output "CSP enforce verified for $Container at $Url with $($scriptTags.Count) nonce-covered script elements."
}

function Assert-CrawlerCsrf {
    param([string]$Container)

    $loginResponse = Get-ContainerHttpResponse -Container $Container -Url "http://127.0.0.1:8081/login.html"
    if ($loginResponse -notmatch '(?im)^HTTP/\S+\s+200\b') {
        throw "Crawler login page did not return HTTP 200."
    }
    $csrfCookie = [regex]::Match($loginResponse, '(?im)^Set-Cookie\s*:\s*XSRF-TOKEN=([^;]+);([^\r\n]+)$')
    if (-not $csrfCookie.Success) {
        throw "Crawler login page did not issue XSRF-TOKEN cookie."
    }
    $cookieAttributes = $csrfCookie.Groups[2].Value
    if ($cookieAttributes -notmatch '(?i)(?:^|;)\s*Path=/' `
            -or $cookieAttributes -notmatch '(?i)(?:^|;)\s*Secure(?:;|$)' `
            -or $cookieAttributes -notmatch '(?i)(?:^|;)\s*SameSite=Lax(?:;|$)' `
            -or $cookieAttributes -match '(?i)(?:^|;)\s*HttpOnly(?:;|$)') {
        throw "Crawler CSRF cookie attributes are not browser-compatible and production-safe."
    }
    $csrfField = [regex]::Match($loginResponse, '(?is)<input\b(?=[^>]*\bname=["'']_csrf["''])[^>]*\bvalue=["'']([^"'']+)["''][^>]*>')
    if (-not $csrfField.Success) {
        throw "Crawler login form is missing rendered CSRF field."
    }

    $sessionCsrfStatus = & docker exec $Container sh -ec `
        'set -eu; cookie_jar=/tmp/novel-crawl-csrf-smoke.cookies; password=$(cat /run/secrets/crawler_admin_password); login_status=$(curl --silent --show-error --output /dev/null --write-out "%{http_code}" --cookie "XSRF-TOKEN=$2" --cookie-jar "$cookie_jar" --data-urlencode "username=$CRAWLER_ADMIN_USERNAME" --data-urlencode "password=$password" --data-urlencode "_csrf=$3" "$1"); write_status=$(curl --silent --show-error --output /dev/null --write-out "%{http_code}" --cookie "$cookie_jar" --request POST "$4"); rm -f "$cookie_jar"; printf "%s,%s" "$login_status" "$write_status"' `
        sh "http://127.0.0.1:8081/login" $csrfCookie.Groups[1].Value $csrfField.Groups[1].Value "http://127.0.0.1:8081/crawl/openOrCloseCrawl"
    if ($LASTEXITCODE -ne 0 -or $sessionCsrfStatus -ne '302,403') {
        throw "Crawler session CSRF flow failed; expected login/write status 302,403, got $sessionCsrfStatus."
    }

    Write-Output "Crawler CSRF verified: secure readable token cookie, rendered form token and authenticated tokenless write rejection."
}

function Assert-Observability {
    param([string]$ProbeContainer)

    foreach ($endpoint in @(
            "http://front:8083/actuator/prometheus",
            "http://admin:80/actuator/prometheus",
            "http://crawl:8081/actuator/prometheus"
        )) {
        & docker exec $ProbeContainer curl --fail --silent --show-error $endpoint | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw "Prometheus endpoint không truy cập được: $endpoint"
        }
    }

    $expectedJobs = @("novel-front", "novel-admin", "novel-crawl", "pushgateway")
    $targetDeadline = (Get-Date).AddSeconds(60)
    do {
        $targetsRaw = & docker exec $ProbeContainer curl --fail --silent --show-error `
            "http://prometheus:9090/api/v1/targets"
        if ($LASTEXITCODE -ne 0) {
            throw "Không đọc được Prometheus targets API."
        }
        $targets = $targetsRaw | ConvertFrom-Json
        $notUp = @($expectedJobs | Where-Object {
                $job = $_
                @($targets.data.activeTargets | Where-Object {
                        $_.labels.job -eq $job -and $_.health -eq "up"
                    }).Count -ne 1
            })
        if ($notUp.Count -eq 0) { break }
        Start-Sleep -Seconds 5
    } while ((Get-Date) -lt $targetDeadline)

    if ($notUp.Count -gt 0) {
        $states = $targets.data.activeTargets | ForEach-Object {
            "$($_.labels.job)=$($_.health):$($_.lastError)"
        }
        throw "Prometheus targets chưa up: $($notUp -join ', '). Trạng thái: $($states -join '; ')"
    }

    $migrationRaw = & docker exec $ProbeContainer curl --fail --silent --show-error `
        "http://prometheus:9090/api/v1/query?query=novel_migration_success"
    $migration = $migrationRaw | ConvertFrom-Json
    if ($LASTEXITCODE -ne 0 -or $migration.data.result.Count -ne 1 `
            -or $migration.data.result[0].value[1] -ne "1") {
        throw "Prometheus không nhận được novel_migration_success=1."
    }

    $grafanaUser = if ($env:GRAFANA_ADMIN_USER) { $env:GRAFANA_ADMIN_USER } else { "admin" }
    $grafanaPassword = [IO.File]::ReadAllText((Join-Path $secretDir "grafana_admin_password")).Trim()
    $dashboardRaw = & docker exec $ProbeContainer curl --fail --silent --show-error `
        --user "${grafanaUser}:$grafanaPassword" `
        "http://grafana:3000/api/search?query=Novel%20Plus"
    if ($LASTEXITCODE -ne 0 -or $dashboardRaw -notmatch 'khoi-thu-overview') {
        throw "Grafana chưa provision dashboard Khởi Thư."
    }

    Write-Output "Observability verified: 4 Prometheus targets up, Flyway metric=1 and Grafana dashboard provisioned."
}

function Assert-CaddyBoundary {
    param([string]$ProbeContainer)

    foreach ($hostName in @("localhost", "admin.localhost", "crawl.localhost")) {
        $status = & docker exec $ProbeContainer curl --insecure --silent --show-error `
            --output /dev/null --write-out "%{http_code}" `
            --connect-to "${hostName}:443:caddy:443" "https://${hostName}/actuator/prometheus"
        if ($LASTEXITCODE -ne 0 -or $status -ne "404") {
            throw "Caddy không chặn actuator cho $hostName; HTTP=$status."
        }
    }

    $grafanaStatus = & docker exec $ProbeContainer curl --insecure --silent --show-error `
        --output /dev/null --write-out "%{http_code}" `
        --connect-to "grafana.localhost:443:caddy:443" "https://grafana.localhost/api/health"
    if ($LASTEXITCODE -ne 0 -or $grafanaStatus -ne "200") {
        throw "Caddy không proxy được Grafana health; HTTP=$grafanaStatus."
    }

    Write-Output "Caddy boundary verified: actuator blocked on 3 app domains and Grafana proxy healthy."
}

function Assert-BackupRestore {
    Invoke-Compose @("run", "--rm", "backup")
    $archives = @(Get-ChildItem -LiteralPath $resolvedBackup -Filter "*.tar.gz.gpg" -File)
    if ($archives.Count -ne 1) {
        throw "Backup smoke must create exactly one encrypted archive; actual=$($archives.Count)."
    }
    $env:BACKUP_ARCHIVE = $archives[0].Name
    Invoke-Compose @("run", "--rm", "restore-drill")
    Write-Output "Backup image verified: encrypted archive and restore drill passed."
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

New-Item -ItemType Directory -Force -Path $secretDir | Out-Null
if ($VerifyBackupRestore) {
    if (Test-Path -LiteralPath $resolvedBackup) {
        Remove-Item -LiteralPath $resolvedBackup -Recurse -Force
    }
    New-Item -ItemType Directory -Force -Path $resolvedBackup | Out-Null
}
foreach ($secretName in $secretNames) {
    $byteCount = if ($secretName -eq "pii_encryption_key") { 32 } else { 48 }
    [System.IO.File]::WriteAllText(
        (Join-Path $secretDir $secretName),
        (New-RandomSecret $byteCount),
        [System.Text.UTF8Encoding]::new($false)
    )
}
[System.IO.File]::WriteAllText(
    (Join-Path $secretDir "alertmanager_webhook_url"),
    "http://127.0.0.1:65535/novel-alerts",
    [System.Text.UTF8Encoding]::new($false)
)

$previousSecretsDir = $env:SECRETS_DIR
$previousMySqlHostPort = $env:MYSQL_HOST_PORT
$previousAlertmanagerAllowHttp = $env:ALERTMANAGER_ALLOW_HTTP
$previousCaddyHttpPort = $env:CADDY_HTTP_PORT
$previousCaddyHttpsPort = $env:CADDY_HTTPS_PORT
$previousNovelDomain = $env:NOVEL_DOMAIN
$previousAdminDomain = $env:NOVEL_ADMIN_DOMAIN
$previousCrawlDomain = $env:NOVEL_CRAWL_DOMAIN
$previousGrafanaDomain = $env:NOVEL_GRAFANA_DOMAIN
$previousBackupDir = $env:BACKUP_DIR
$previousBackupArchive = $env:BACKUP_ARCHIVE
$env:SECRETS_DIR = $secretDir
$env:MYSQL_HOST_PORT = [string]$MySqlHostPort
$env:ALERTMANAGER_ALLOW_HTTP = "true"
$env:CADDY_HTTP_PORT = [string]$CaddyHttpHostPort
$env:CADDY_HTTPS_PORT = [string]$CaddyHttpsHostPort
$env:NOVEL_DOMAIN = "localhost"
$env:NOVEL_ADMIN_DOMAIN = "admin.localhost"
$env:NOVEL_CRAWL_DOMAIN = "crawl.localhost"
$env:NOVEL_GRAFANA_DOMAIN = "grafana.localhost"
if ($VerifyBackupRestore) {
    $env:BACKUP_DIR = $resolvedBackup
    $env:BACKUP_ARCHIVE = ""
}

try {
    Invoke-Compose @("config", "--quiet")
    $upArguments = @("up", "-d")
    if ($SkipImageBuild) {
        $upArguments += "--no-build"
    }
    else {
        $upArguments += "--build"
    }
    $upArguments += @("mysql", "pushgateway", "migrate", "redis", "front", "crawl", "admin", `
            "alertmanager", "prometheus", "grafana", "caddy")
    Invoke-Compose $upArguments

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $migrateContainer = "$ProjectName-migrate-1"
    $adminContainer = "$ProjectName-admin-1"
    $frontContainer = "$ProjectName-front-1"
    $crawlContainer = "$ProjectName-crawl-1"
    $grafanaContainer = "$ProjectName-grafana-1"
    $caddyContainer = "$ProjectName-caddy-1"
    $appContainers = [ordered]@{
        admin        = $adminContainer
        front        = $frontContainer
        crawl        = $crawlContainer
        grafana      = $grafanaContainer
        alertmanager = "$ProjectName-alertmanager-1"
        pushgateway  = "$ProjectName-pushgateway-1"
        caddy        = $caddyContainer
    }

    while ((Get-Date) -lt $deadline) {
        $migrateState = & docker inspect -f "{{.State.Status}}:{{.State.ExitCode}}" $migrateContainer 2>$null
        $health = [ordered]@{}
        foreach ($entry in $appContainers.GetEnumerator()) {
            $health[$entry.Key] = & docker inspect -f "{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}" $entry.Value 2>$null
        }
        $healthSummary = ($health.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" }) -join ' '
        Write-Output "migrate=$migrateState $healthSummary"

        $allHealthy = @($health.Values | Where-Object { $_ -ne 'healthy' }).Count -eq 0
        if ($migrateState -eq "exited:0" -and $allHealthy) {
            break
        }
        if ($migrateState -match "^exited:" -and $migrateState -ne "exited:0") {
            Invoke-Compose @("logs", "--no-color", "--tail=200", "migrate")
            throw "Flyway smoke thất bại."
        }
        Start-Sleep -Seconds 5
    }

    $unhealthyServices = @()
    foreach ($entry in $appContainers.GetEnumerator()) {
        $finalHealth = & docker inspect -f "{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}" $entry.Value
        if ($finalHealth -ne "healthy") {
            $unhealthyServices += $entry.Key
        }
    }
    if ($unhealthyServices.Count -gt 0) {
        Invoke-Compose @("logs", "--no-color", "--tail=250")
        throw "Unhealthy services after $TimeoutSeconds seconds: $($unhealthyServices -join ', ')."
    }

    Assert-EnforcedCsp -Container $adminContainer -Url "http://127.0.0.1/login"
    Assert-EnforcedCsp -Container $frontContainer -Url "http://127.0.0.1:8083/"
    Assert-EnforcedCsp -Container $crawlContainer -Url "http://127.0.0.1:8081/" -CrawlerBasicAuth
    Assert-CrawlerCsrf -Container $crawlContainer
    Assert-Observability -ProbeContainer $frontContainer
    Assert-CaddyBoundary -ProbeContainer $frontContainer
    if ($VerifyBackupRestore) {
        Assert-BackupRestore
    }

    Write-Output "Compose production smoke passed: Flyway, CSP, CSRF, observability, Caddy and optional backup checks are healthy."
}
finally {
    $previousCleanupErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & docker compose @composeGlobalArguments down -v --remove-orphans 2>&1 | Out-Null
        $cleanupExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousCleanupErrorActionPreference
    }
    if (Test-Path -LiteralPath $resolvedSecret) {
        Remove-Item -LiteralPath $resolvedSecret -Recurse -Force
    }
    if ($VerifyBackupRestore -and (Test-Path -LiteralPath $resolvedBackup)) {
        Remove-Item -LiteralPath $resolvedBackup -Recurse -Force
    }
    $env:SECRETS_DIR = $previousSecretsDir
    $env:MYSQL_HOST_PORT = $previousMySqlHostPort
    $env:ALERTMANAGER_ALLOW_HTTP = $previousAlertmanagerAllowHttp
    $env:CADDY_HTTP_PORT = $previousCaddyHttpPort
    $env:CADDY_HTTPS_PORT = $previousCaddyHttpsPort
    $env:NOVEL_DOMAIN = $previousNovelDomain
    $env:NOVEL_ADMIN_DOMAIN = $previousAdminDomain
    $env:NOVEL_CRAWL_DOMAIN = $previousCrawlDomain
    $env:NOVEL_GRAFANA_DOMAIN = $previousGrafanaDomain
    $env:BACKUP_DIR = $previousBackupDir
    $env:BACKUP_ARCHIVE = $previousBackupArchive
    if ($cleanupExitCode -ne 0) {
        throw "Docker Compose cleanup thất bại với exit code $cleanupExitCode."
    }
}
