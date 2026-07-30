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

Push-Location $repository
try {
    if ($null -eq (Get-Command 'docker' -ErrorAction SilentlyContinue)) {
        throw 'Docker CLI was not found.'
    }

    Invoke-Native -FilePath 'docker' -Arguments @('compose', 'config', '--quiet')
    Write-Output '[OK] Docker Compose configuration is valid.'
    if ($ConfigOnly) {
        exit 0
    }

    Invoke-Native -FilePath 'docker' -Arguments @('info', '--format', '{{.ServerVersion}}')
    $database = Get-Setting -Name 'MYSQL_DATABASE' -DefaultValue 'novel_plus'
    $username = Get-Setting -Name 'MYSQL_USER' -DefaultValue 'novel'
    $password = Get-RequiredSetting -Name 'MYSQL_APP_PASSWORD'
    $hostPortText = Get-Setting -Name 'MYSQL_HOST_PORT' -DefaultValue '3307'
    $hostPort = 0
    if (-not [int]::TryParse($hostPortText, [ref]$hostPort) -or $hostPort -lt 1 -or $hostPort -gt 65535) {
        throw 'MYSQL_HOST_PORT must be an integer from 1 to 65535.'
    }

    Invoke-Native -FilePath 'docker' -Arguments @('compose', 'up', '-d', 'mysql')
    Invoke-Native -FilePath 'docker' -Arguments @('compose', 'run', '--rm', 'migrate')
    Invoke-Native -FilePath 'docker' -Arguments @('compose', 'run', '--rm', 'migrate')
    Write-Output '[OK] Migration succeeded twice.'

    New-Item -ItemType Directory -Force -Path $mavenTemp | Out-Null
    $env:TEMP = $mavenTemp
    $env:TMP = $mavenTemp
    $maven = Resolve-Maven
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
    $testNames = 'GamificationMySqlIntegrationTest,MonthlyTicketConcurrencyIT,MonthlySeasonConcurrencyIT,QuestCampaignConcurrencyIT,ReadingSubscriptionMySqlIntegrationTest,ReadingSubscriptionCheckoutMySqlIntegrationTest,ReadingSubscriptionPaidReviewConcurrencyIT,GiftCodeMySqlIntegrationTest,GiftCodeConcurrencyIT'
    $mavenArguments = @(
        '-q', '-pl', 'novel-front', '-am',
        "-Dtest=$testNames",
        '-Dsurefire.failIfNoSpecifiedTests=false',
        '-Dgamification.mysql.it=true',
        '-Dgamification.concurrency.it=true',
        '-Dreader.subscription.mysql.it=true',
        '-Dgift.code.mysql.it=true',
        '-Dgift.code.concurrency.it=true',
        'test'
    )
    Invoke-Native -FilePath $maven -Arguments $mavenArguments
    Write-Output '[OK] Gamification MySQL and concurrency tests passed.'
}
finally {
    if ($StopAfter -and -not $ConfigOnly) {
        & docker compose stop mysql
    }
    Pop-Location
}
