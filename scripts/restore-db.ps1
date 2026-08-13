# PowerShell Database Restore Script for Khoi-Thu MySQL Database
param (
    [Parameter(Mandatory = $true)]
    [string]$BackupFile,
    [string]$DbHost = "127.0.0.1",
    [int]$DbPort = 3306,
    [string]$DbUser = "root",
    [string]$DbPass = "root123456",
    [string]$DbName = "novel_plus"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $BackupFile)) {
    Write-Error "[ERROR] Backup file does not exist: $BackupFile"
    exit 1
}

Write-Host "[INFO] Restoring database '$DbName' from '$BackupFile'..." -ForegroundColor Green

$DockerRunning = $false
try {
    $dockerContainer = docker ps --format '{{.Names}}' | Select-String "khoi-thu-mysql"
    if ($dockerContainer) {
        $DockerRunning = $true
    }
}
catch {}

if ($DockerRunning) {
    Get-Content $BackupFile | docker exec -i khoi-thu-mysql mysql --default-character-set=utf8mb4 -u"$DbUser" -p"$DbPass" "$DbName"
}
else {
    Get-Content $BackupFile | & mysql --host="$DbHost" --port=$DbPort --default-character-set=utf8mb4 -u"$DbUser" -p"$DbPass" "$DbName"
}

Write-Host "[SUCCESS] Database restoration completed successfully." -ForegroundColor Green
