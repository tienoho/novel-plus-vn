# PowerShell Database Backup Script for Novel-Plus MySQL Database
param (
    [string]$BackupDir = ".\backups",
    [string]$DbHost = "127.0.0.1",
    [int]$DbPort = 3306,
    [string]$DbUser = "root",
    [string]$DbPass = "root123456",
    [string]$DbName = "novel_plus"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $BackupDir)) {
    New-Item -ItemType Directory -Path $BackupDir | Out-Null
}

$Timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$BackupFile = Join-Path $BackupDir "novel_plus_backup_$Timestamp.sql"

Write-Host "[INFO] Starting database backup for '$DbName' at $Timestamp..." -ForegroundColor Green

$DockerRunning = $false
try {
    $dockerContainer = docker ps --format '{{.Names}}' | Select-String "novel-plus-mysql"
    if ($dockerContainer) {
        $DockerRunning = $true
    }
} catch {}

if ($DockerRunning) {
    Write-Host "[INFO] Dumping database via Docker container 'novel-plus-mysql'..." -ForegroundColor Cyan
    docker exec novel-plus-mysql mysqldump --default-character-set=utf8mb4 --routines --triggers --single-transaction -u"$DbUser" -p"$DbPass" "$DbName" | Set-Content -Path $BackupFile -Encoding UTF8
} else {
    Write-Host "[INFO] Dumping database via mysqldump command..." -ForegroundColor Cyan
    & mysqldump --host="$DbHost" --port=$DbPort --default-character-set=utf8mb4 --routines --triggers --single-transaction -u"$DbUser" -p"$DbPass" "$DbName" | Set-Content -Path $BackupFile -Encoding UTF8
}

Write-Host "[SUCCESS] Database backup completed. File saved at: $BackupFile" -ForegroundColor Green
