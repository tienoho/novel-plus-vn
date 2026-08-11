param(
    [switch]$ConfigOnly,
    [switch]$StopAfter
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repository = Split-Path -Parent $scriptDir
$dotEnvPath = Join-Path $repository '.env'
$tempRoot = Join-Path $repository 'tmp'
$mavenTemp = Join-Path $tempRoot 'maven-gamification'
$composeSecretTemp = Join-Path $tempRoot 'compose-secrets-gamification'
$verifyProjectName = if ([string]::IsNullOrWhiteSpace($env:VERIFY_COMPOSE_PROJECT_NAME)) {
    'novel-plus-verify'
} else {
    $env:VERIFY_COMPOSE_PROJECT_NAME
}
if ($verifyProjectName -notmatch '^[a-z0-9][a-z0-9_-]*$') {
    throw 'VERIFY_COMPOSE_PROJECT_NAME chỉ được chứa chữ thường, chữ số, dấu gạch ngang và gạch dưới.'
}
$composeArguments = @(
    'compose', '--project-name', $verifyProjectName,
    '-f', 'compose.yaml', '-f', 'compose.test.yaml'
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

function Get-Setting {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [string]$DefaultValue = ''
    )

    $value = [Environment]::GetEnvironmentVariable($Name)
    if ([string]::IsNullOrWhiteSpace($value) -and (Test-Path -LiteralPath $dotEnvPath)) {
        $pattern = '^\s*' + [regex]::Escape($Name) + '\s*=\s*(.*)\s*$'
        $match = Get-Content -LiteralPath $dotEnvPath |
            Select-String -Pattern $pattern |
            Select-Object -Last 1
        if ($null -ne $match) {
            $value = $match.Matches[0].Groups[1].Value.Trim()
            if ($value.Length -ge 2) {
                $first = $value.Substring(0, 1)
                $last = $value.Substring($value.Length - 1, 1)
                if (($first -eq '"' -and $last -eq '"') -or ($first -eq "'" -and $last -eq "'")) {
                    $value = $value.Substring(1, $value.Length - 2)
                }
            }
        }
    }
    if ([string]::IsNullOrWhiteSpace($value)) {
        $value = $DefaultValue
    }
    return $value
}

function Get-RequiredSetting {
    param([Parameter(Mandatory = $true)][string]$Name)

    $value = Get-Setting -Name $Name
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "Missing required setting: $Name"
    }
    return $value
}

function Get-SecretSetting {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$FileName,
        [string]$DefaultValue = ''
    )

    $value = Get-Setting -Name $Name
    if ([string]::IsNullOrWhiteSpace($value)) {
        $sourceDirectory = Get-Setting -Name 'SECRETS_DIR' -DefaultValue (Join-Path $repository 'secrets')
        if (-not [System.IO.Path]::IsPathRooted($sourceDirectory)) {
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
        throw "Missing required secret: $Name or $FileName"
    }
    return $value
}

function Initialize-ComposeSecrets {
    New-Item -ItemType Directory -Force -Path $composeSecretTemp | Out-Null
    $utf8NoBom = [System.Text.UTF8Encoding]::new($false)
    $secretValues = [ordered]@{
        mysql_root_password = Get-SecretSetting -Name 'MYSQL_ROOT_PASSWORD' -FileName 'mysql_root_password'
        mysql_app_password = Get-SecretSetting -Name 'MYSQL_APP_PASSWORD' -FileName 'mysql_app_password'
        redis_password = Get-SecretSetting -Name 'REDIS_PASSWORD' -FileName 'redis_password'
        jwt_secret = Get-SecretSetting -Name 'JWT_SECRET' -FileName 'jwt_secret'
        cache_manager_password = Get-SecretSetting -Name 'CACHE_MANAGER_PASSWORD' -FileName 'cache_manager_password'
        pii_encryption_key = Get-SecretSetting -Name 'PII_ENCRYPTION_KEY' -FileName 'pii_encryption_key'
        gamification_vote_ip_hash_salt = Get-SecretSetting -Name 'GAMIFICATION_VOTE_IP_HASH_SALT' `
            -FileName 'gamification_vote_ip_hash_salt' `
            -DefaultValue 'integration-gamification-vote-ip-hash-salt-2026'
        admin_bootstrap_password = Get-SecretSetting -Name 'ADMIN_BOOTSTRAP_PASSWORD' -FileName 'admin_bootstrap_password'
        crawler_admin_password = Get-SecretSetting -Name 'CRAWLER_ADMIN_PASSWORD' -FileName 'crawler_admin_password'
        backup_encryption_password = Get-SecretSetting -Name 'BACKUP_ENCRYPTION_PASSWORD' `
            -FileName 'backup_encryption_password' -DefaultValue 'integration-backup-encryption-password'
        vnpay_hash_secret = Get-SecretSetting -Name 'VNPAY_HASH_SECRET' `
            -FileName 'vnpay_hash_secret' -DefaultValue 'disabled'
        vnpay_recurring_password = Get-SecretSetting -Name 'VNPAY_RECURRING_PASSWORD' `
            -FileName 'vnpay_recurring_password' -DefaultValue 'disabled'
        vnpay_recurring_client_secret = Get-SecretSetting -Name 'VNPAY_RECURRING_CLIENT_SECRET' `
            -FileName 'vnpay_recurring_client_secret' -DefaultValue 'disabled'
        vnpay_recurring_hash_secret = Get-SecretSetting -Name 'VNPAY_RECURRING_HASH_SECRET' `
            -FileName 'vnpay_recurring_hash_secret' -DefaultValue 'disabled'
        vietqr_webhook_secret = Get-SecretSetting -Name 'VIETQR_WEBHOOK_SECRET' `
            -FileName 'vietqr_webhook_secret' -DefaultValue 'disabled'
    }
    foreach ($entry in $secretValues.GetEnumerator()) {
        [System.IO.File]::WriteAllText((Join-Path $composeSecretTemp $entry.Key), $entry.Value, $utf8NoBom)
    }
    $env:SECRETS_DIR = $composeSecretTemp
    return $secretValues
}

function Resolve-Maven {
    $configured = [Environment]::GetEnvironmentVariable('MAVEN_CMD')
    if (-not [string]::IsNullOrWhiteSpace($configured)) {
        return $configured
    }

    $fromPath = Get-Command 'mvn.cmd' -ErrorAction SilentlyContinue
    if ($null -ne $fromPath) {
        return $fromPath.Source
    }

    $bundled = 'D:\SDK\apache-maven-3.9.16\bin\mvn.cmd'
    if (Test-Path -LiteralPath $bundled) {
        return $bundled
    }
    throw 'Maven was not found. Set MAVEN_CMD or install Maven.'
}

function Assert-MavenJava21 {
    param([Parameter(Mandatory = $true)][string]$Maven)

    $versionOutput = @(& $Maven '-version' 2>&1)
    $versionExitCode = $LASTEXITCODE
    if ($versionExitCode -ne 0) {
        throw "Unable to inspect the Maven Java runtime (exit code $versionExitCode)."
    }
    $javaVersionLine = $versionOutput |
        Where-Object { $_.ToString() -match 'Java version:\s*\d+' } |
        Select-Object -First 1
    if ($null -eq $javaVersionLine) {
        throw 'Unable to determine the Java version used by Maven.'
    }
    $javaVersionMatch = [regex]::Match($javaVersionLine.ToString(), 'Java version:\s*(?<major>\d+)')
    $javaMajorVersion = [int]$javaVersionMatch.Groups['major'].Value
    if ($javaMajorVersion -ne 21) {
        throw "Maven must use Java 21; detected Java $javaMajorVersion. Set JAVA_HOME to a JDK 21 installation."
    }
    Write-Output '[OK] Maven is using Java 21.'
}

Push-Location $repository
try {
    if ($null -eq (Get-Command 'docker' -ErrorAction SilentlyContinue)) {
        throw 'Docker CLI was not found.'
    }

    $secrets = Initialize-ComposeSecrets
    Invoke-Native -FilePath 'docker' -Arguments ($composeArguments + @('config', '--quiet'))
    Write-Output '[OK] Docker Compose configuration is valid.'
    if ($ConfigOnly) {
        exit 0
    }

    $maven = Resolve-Maven
    Assert-MavenJava21 -Maven $maven
    Invoke-Native -FilePath 'docker' -Arguments @('info', '--format', '{{.ServerVersion}}')
    $database = Get-Setting -Name 'MYSQL_DATABASE' -DefaultValue 'novel_plus'
    $username = Get-Setting -Name 'MYSQL_USER' -DefaultValue 'novel'
    $password = $secrets.mysql_app_password
    $hostPortText = Get-Setting -Name 'MYSQL_HOST_PORT' -DefaultValue '3307'
    $hostPort = 0
    if (-not [int]::TryParse($hostPortText, [ref]$hostPort) -or $hostPort -lt 1 -or $hostPort -gt 65535) {
        throw 'MYSQL_HOST_PORT must be an integer from 1 to 65535.'
    }

    Invoke-Native -FilePath 'docker' -Arguments ($composeArguments + @('build', 'migrate'))
    Invoke-Native -FilePath 'docker' -Arguments ($composeArguments + @('up', '-d', 'mysql'))
    Invoke-Native -FilePath 'docker' -Arguments ($composeArguments + @('run', '--rm', 'migrate'))
    Invoke-Native -FilePath 'docker' -Arguments ($composeArguments + @('run', '--rm', 'migrate'))
    Write-Output '[OK] Migration succeeded twice.'

    New-Item -ItemType Directory -Force -Path $mavenTemp | Out-Null
    $env:TEMP = $mavenTemp
    $env:TMP = $mavenTemp
    $jdbcUrl = "jdbc:mysql://127.0.0.1:$hostPort/$database" +
        '?allowPublicKeyRetrieval=true&useUnicode=true&characterEncoding=utf-8&useSSL=false' +
        '&serverTimezone=Asia/Ho_Chi_Minh'
    $env:SPRING_DATASOURCE_DRIVER_CLASS_NAME = 'com.mysql.cj.jdbc.Driver'
    $env:SPRING_DATASOURCE_URL = $jdbcUrl
    $env:SPRING_DATASOURCE_USERNAME = $username
    $env:SPRING_DATASOURCE_PASSWORD = $password
    $env:GIFT_CODE_ENABLED = 'true'
    $env:GIFT_CODE_HMAC_KEY_ID = 'legacy-v1'
    $env:GIFT_CODE_HMAC_SECRET = 'gift-code-integration-secret-at-least-32-characters'
    $env:GIFT_CODE_HMAC_VERIFICATION_KEYS = ''
    $integrationTestFiles = Get-ChildItem -LiteralPath (Join-Path $repository 'novel-front/src/test/java') `
        -Recurse -File | Where-Object {
            $_.Name -like '*MySqlIntegrationTest.java' -or $_.Name -like '*ConcurrencyIT.java'
        }
    if ($integrationTestFiles.Count -eq 0) {
        throw 'No MySQL integration or concurrency tests were found.'
    }

    $enabledPropertyPattern = '@EnabledIfSystemProperty\(named\s*=\s*"([^"]+)"\s*,\s*matches\s*=\s*"true"\)'
    $testProperties = New-Object 'System.Collections.Generic.HashSet[string]' ([System.StringComparer]::Ordinal)
    foreach ($testFile in $integrationTestFiles) {
        $source = Get-Content -LiteralPath $testFile.FullName -Raw
        $propertyMatch = [regex]::Match($source, $enabledPropertyPattern)
        if (-not $propertyMatch.Success) {
            throw "Integration test $($testFile.Name) does not declare an EnabledIfSystemProperty gate."
        }
        [void]$testProperties.Add($propertyMatch.Groups[1].Value)
    }

    $testNames = (($integrationTestFiles.BaseName | Sort-Object -Unique) -join ',')
    $mavenArguments = @(
        '-q', '-pl', 'novel-front', '-am',
        "-Dtest=$testNames",
        '-Dsurefire.failIfNoSpecifiedTests=false'
    )
    $mavenArguments += ($testProperties | Sort-Object | ForEach-Object { "-D$_=true" })
    $mavenArguments += 'test'
    Invoke-Native -FilePath $maven -Arguments $mavenArguments
    Write-Output "[OK] All $($integrationTestFiles.Count) MySQL integration and concurrency test classes passed."
}
finally {
    if ($StopAfter -and -not $ConfigOnly) {
        $downArguments = $composeArguments + @('down', '--volumes', '--remove-orphans')
        & docker @downArguments
    }
    if (Test-Path -LiteralPath $composeSecretTemp) {
        $resolvedTempRoot = [System.IO.Path]::GetFullPath($tempRoot)
        $resolvedSecretTemp = [System.IO.Path]::GetFullPath($composeSecretTemp)
        if ($resolvedSecretTemp.StartsWith($resolvedTempRoot + [System.IO.Path]::DirectorySeparatorChar,
                [System.StringComparison]::OrdinalIgnoreCase)) {
            Remove-Item -LiteralPath $resolvedSecretTemp -Recurse -Force
        }
    }
    Pop-Location
}
