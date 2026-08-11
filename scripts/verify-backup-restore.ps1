param(
    [switch]$KeepArtifacts
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repository = Split-Path -Parent $scriptDir
$tempRoot = Join-Path $repository 'tmp'
$secretTemp = Join-Path $tempRoot 'compose-secrets-backup-verify'
$backupTemp = Join-Path $tempRoot 'backup-restore-verify'
$projectName = if ([string]::IsNullOrWhiteSpace($env:BACKUP_VERIFY_PROJECT_NAME)) {
    'novel-plus-backup-verify'
} else {
    $env:BACKUP_VERIFY_PROJECT_NAME
}
if ($projectName -notmatch '^[a-z0-9][a-z0-9_-]*$') {
    throw 'BACKUP_VERIFY_PROJECT_NAME chỉ được chứa chữ thường, chữ số, gạch ngang và gạch dưới.'
}
$composeArguments = @(
    'compose', '--project-name', $projectName,
    '-f', 'compose.yaml', '--profile', 'tools'
)

function Invoke-Native {
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [Parameter(Mandatory = $true)][string[]]$Arguments
    )
    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed with exit code $LASTEXITCODE`: $FilePath"
    }
}

function Get-SecretValue {
    param(
        [Parameter(Mandatory = $true)][string]$EnvironmentName,
        [Parameter(Mandatory = $true)][string]$FileName,
        [string]$DefaultValue = ''
    )

    $value = [Environment]::GetEnvironmentVariable($EnvironmentName)
    if ([string]::IsNullOrWhiteSpace($value)) {
        $sourceDirectory = [Environment]::GetEnvironmentVariable('SECRETS_DIR')
        if ([string]::IsNullOrWhiteSpace($sourceDirectory)) {
            $sourceDirectory = Join-Path $repository 'secrets'
        } elseif (-not [System.IO.Path]::IsPathRooted($sourceDirectory)) {
            $sourceDirectory = Join-Path $repository $sourceDirectory
        }
        $sourceFile = Join-Path $sourceDirectory $FileName
        if (Test-Path -LiteralPath $sourceFile) {
            $value = [System.IO.File]::ReadAllText($sourceFile).TrimEnd("`r", "`n")
        }
    }
    if ([string]::IsNullOrWhiteSpace($value)) {
        $value = $DefaultValue
    }
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "Thiếu secret $EnvironmentName hoặc file $FileName."
    }
    return $value
}

function Remove-TestDirectory {
    param([Parameter(Mandatory = $true)][string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        return
    }
    $resolvedRoot = [System.IO.Path]::GetFullPath($tempRoot)
    $resolvedPath = [System.IO.Path]::GetFullPath($Path)
    if (-not $resolvedPath.StartsWith($resolvedRoot + [System.IO.Path]::DirectorySeparatorChar,
            [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Từ chối xóa đường dẫn ngoài thư mục test: $resolvedPath"
    }
    Remove-Item -LiteralPath $resolvedPath -Recurse -Force
}

Push-Location $repository
try {
    if ($null -eq (Get-Command 'docker' -ErrorAction SilentlyContinue)) {
        throw 'Không tìm thấy Docker CLI.'
    }

    Remove-TestDirectory -Path $secretTemp
    Remove-TestDirectory -Path $backupTemp
    New-Item -ItemType Directory -Force -Path $secretTemp, $backupTemp | Out-Null

    $utf8NoBom = [System.Text.UTF8Encoding]::new($false)
    $secretValues = [ordered]@{
        mysql_root_password = Get-SecretValue -EnvironmentName 'MYSQL_ROOT_PASSWORD' -FileName 'mysql_root_password'
        mysql_app_password = Get-SecretValue -EnvironmentName 'MYSQL_APP_PASSWORD' -FileName 'mysql_app_password'
        redis_password = Get-SecretValue -EnvironmentName 'REDIS_PASSWORD' -FileName 'redis_password'
        jwt_secret = Get-SecretValue -EnvironmentName 'JWT_SECRET' -FileName 'jwt_secret'
        cache_manager_password = Get-SecretValue -EnvironmentName 'CACHE_MANAGER_PASSWORD' -FileName 'cache_manager_password'
        pii_encryption_key = Get-SecretValue -EnvironmentName 'PII_ENCRYPTION_KEY' -FileName 'pii_encryption_key'
        gamification_vote_ip_hash_salt = 'integration-gamification-vote-ip-hash-salt-2026'
        admin_bootstrap_password = Get-SecretValue -EnvironmentName 'ADMIN_BOOTSTRAP_PASSWORD' -FileName 'admin_bootstrap_password'
        crawler_admin_password = Get-SecretValue -EnvironmentName 'CRAWLER_ADMIN_PASSWORD' -FileName 'crawler_admin_password'
        backup_encryption_password = Get-SecretValue -EnvironmentName 'BACKUP_ENCRYPTION_PASSWORD' `
            -FileName 'backup_encryption_password' -DefaultValue 'integration-backup-encryption-password'
        vnpay_hash_secret = 'disabled'
        vnpay_recurring_password = 'disabled'
        vnpay_recurring_client_secret = 'disabled'
        vnpay_recurring_hash_secret = 'disabled'
        vietqr_webhook_secret = 'disabled'
    }
    foreach ($entry in $secretValues.GetEnumerator()) {
        [System.IO.File]::WriteAllText((Join-Path $secretTemp $entry.Key), $entry.Value, $utf8NoBom)
    }

    $env:SECRETS_DIR = $secretTemp
    $env:BACKUP_DIR = $backupTemp
    Invoke-Native -FilePath 'docker' -Arguments ($composeArguments + @('config', '--quiet'))
    Invoke-Native -FilePath 'docker' -Arguments ($composeArguments + @('build', 'migrate', 'backup'))
    Invoke-Native -FilePath 'docker' -Arguments ($composeArguments + @('up', '-d', 'mysql'))
    Invoke-Native -FilePath 'docker' -Arguments ($composeArguments + @('run', '--rm', 'migrate'))
    Invoke-Native -FilePath 'docker' -Arguments ($composeArguments + @('run', '--rm', 'backup'))

    $archives = @(Get-ChildItem -LiteralPath $backupTemp -Filter '*.tar.gz.gpg' -File)
    if ($archives.Count -ne 1) {
        throw "Bài kiểm tra cần đúng một archive, thực tế có $($archives.Count)."
    }
    $env:BACKUP_ARCHIVE = $archives[0].Name
    Invoke-Native -FilePath 'docker' -Arguments ($composeArguments + @('run', '--rm', 'restore-drill'))
    Write-Output "[OK] Backup mã hóa và restore drill thành công: $($archives[0].Name)"
}
finally {
    $downArguments = $composeArguments + @('down', '--volumes', '--remove-orphans')
    & docker @downArguments
    Remove-TestDirectory -Path $secretTemp
    if (-not $KeepArtifacts) {
        Remove-TestDirectory -Path $backupTemp
    }
    Pop-Location
}
