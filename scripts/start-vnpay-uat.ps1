[CmdletBinding()]
param(
    [string]$EnvFile = '.env.uat',
    [ValidateRange(60, 1800)]
    [int]$TimeoutSeconds = 900,
    [switch]$SkipImageBuild
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$resolvedEnvFile = if ([System.IO.Path]::IsPathRooted($EnvFile)) {
    [System.IO.Path]::GetFullPath($EnvFile)
} else {
    [System.IO.Path]::GetFullPath((Join-Path $repoRoot $EnvFile))
}
if (-not (Test-Path -LiteralPath $resolvedEnvFile -PathType Leaf)) {
    throw "Thiếu $resolvedEnvFile; chạy prepare-vnpay-uat.ps1 trước."
}

function Get-EnvValue([string]$Name) {
    $line = Get-Content -Encoding UTF8 $resolvedEnvFile |
        Where-Object { $_ -match "^$([regex]::Escape($Name))=" } |
        Select-Object -Last 1
    if (-not $line) { return $null }
    return ($line -split '=', 2)[1].Trim()
}

$projectName = Get-EnvValue 'COMPOSE_PROJECT_NAME'
$secretDirectory = Get-EnvValue 'SECRETS_DIR'
if ($projectName -notmatch '^[A-Za-z0-9_-]{3,40}$' -or -not $secretDirectory) {
    throw 'Cấu hình project/secret UAT không hợp lệ.'
}
$resolvedSecretDirectory = if ([System.IO.Path]::IsPathRooted($secretDirectory)) {
    [System.IO.Path]::GetFullPath($secretDirectory)
} else {
    [System.IO.Path]::GetFullPath((Join-Path $repoRoot $secretDirectory))
}
$rootSecretFile = Join-Path $resolvedSecretDirectory 'mysql_root_password'
if (-not (Test-Path -LiteralPath $rootSecretFile -PathType Leaf)) {
    throw 'Thiếu mysql_root_password của UAT.'
}

function Invoke-UatCompose([string[]]$Arguments) {
    & docker compose --env-file $resolvedEnvFile -p $projectName `
        -f "$repoRoot/compose.yaml" -f "$repoRoot/compose.e2e.yaml" @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Docker Compose UAT thất bại: $($Arguments -join ' ')" }
}

function Wait-Container([string]$Name, [string[]]$AcceptedStates) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $state = & docker inspect -f `
            '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}:{{.State.ExitCode}}{{end}}' `
            "$projectName-$Name-1" 2>$null
        if ($state -in $AcceptedStates) { return }
        if ($state -match '^(unhealthy|exited:[1-9]|dead)') { throw "$Name dừng với trạng thái $state" }
        Start-Sleep -Seconds 3
    } while ((Get-Date) -lt $deadline)
    throw "$Name chưa sẵn sàng sau $TimeoutSeconds giây."
}

$bootstrap = @('up', '-d')
if (-not $SkipImageBuild) { $bootstrap += '--build' }
$bootstrap += @('mysql', 'pushgateway', 'migrate', 'redis')
Invoke-UatCompose $bootstrap
Wait-Container 'mysql' @('healthy')
Wait-Container 'redis' @('healthy')
Wait-Container 'migrate' @('exited:0')

$rootPassword = [System.IO.File]::ReadAllText($rootSecretFile).Trim()
$fixtureCount = & docker exec -e "MYSQL_PWD=$rootPassword" "$projectName-mysql-1" `
    mysql --batch --skip-column-names -uroot novel_plus `
    -e "SELECT COUNT(*) FROM user WHERE id=991000000000000001"
if ($LASTEXITCODE -ne 0) { throw 'Không kiểm tra được fixture UAT.' }
if (($fixtureCount | Select-Object -Last 1).Trim() -eq '0') {
    & docker exec -e "MYSQL_PWD=$rootPassword" "$projectName-mysql-1" `
        mysql --default-character-set=utf8mb4 -uroot novel_plus -e 'source /e2e/fixtures/seed.sql'
    if ($LASTEXITCODE -ne 0) { throw 'Không nạp được fixture UAT.' }
}
$rootPassword = $null

$services = @('up', '-d')
if (-not $SkipImageBuild) { $services += '--build' }
$services += @('front', 'crawl', 'admin', 'alertmanager', 'prometheus', 'grafana', 'caddy')
Invoke-UatCompose $services
foreach ($service in @('front', 'crawl', 'admin', 'alertmanager', 'grafana', 'caddy')) {
    Wait-Container $service @('healthy', 'running:0')
}

& "$PSScriptRoot/preflight-vnpay-uat.ps1" -EnvFile $EnvFile -TimeoutSeconds $TimeoutSeconds
if ($LASTEXITCODE -ne 0) { throw 'Preflight URL VNPAY UAT thất bại.' }
