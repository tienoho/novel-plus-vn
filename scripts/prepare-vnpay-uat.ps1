[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Domain,
    [Parameter(Mandatory = $true)]
    [string]$TmnCode,
    [string]$HashSecretFile = $env:VNPAY_SANDBOX_HASH_SECRET_FILE,
    [string]$AcmeEmail = 'ops@example.com',
    [string]$EnvFile = '.env.uat',
    [string]$SecretDirectory = 'secrets/uat',
    [string]$RegistrationFile = '.vnpay-uat-registration.txt'
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot

if ($Domain -notmatch '^(?=.{4,253}$)(?!-)(?:[A-Za-z0-9-]+\.)+[A-Za-z]{2,63}$' -or `
    $Domain -match '(^|\.)(localhost|example\.(com|net|org)|invalid)$') {
    throw 'Domain UAT phải là hostname công khai hợp lệ và không được là localhost/example.'
}
if ($TmnCode -notmatch '^[A-Za-z0-9]{8}$') {
    throw 'TmnCode Sandbox phải gồm đúng 8 ký tự chữ/số.'
}
if ($AcmeEmail -notmatch '^[^@\s]+@[^@\s]+\.[^@\s]+$') {
    throw 'Email ACME không hợp lệ.'
}

$resolvedEnvFile = if ([System.IO.Path]::IsPathRooted($EnvFile)) {
    [System.IO.Path]::GetFullPath($EnvFile)
} else {
    [System.IO.Path]::GetFullPath((Join-Path $repoRoot $EnvFile))
}
$resolvedSecretDirectory = if ([System.IO.Path]::IsPathRooted($SecretDirectory)) {
    [System.IO.Path]::GetFullPath($SecretDirectory)
} else {
    [System.IO.Path]::GetFullPath((Join-Path $repoRoot $SecretDirectory))
}
$resolvedRegistrationFile = if ([System.IO.Path]::IsPathRooted($RegistrationFile)) {
    [System.IO.Path]::GetFullPath($RegistrationFile)
} else {
    [System.IO.Path]::GetFullPath((Join-Path $repoRoot $RegistrationFile))
}
if (Test-Path -LiteralPath $resolvedEnvFile) {
    throw "Đã có $resolvedEnvFile; không ghi đè cấu hình UAT hiện hữu."
}
if (Test-Path -LiteralPath $resolvedSecretDirectory) {
    throw "Đã có $resolvedSecretDirectory; không ghi đè secret UAT hiện hữu."
}
if (Test-Path -LiteralPath $resolvedRegistrationFile) {
    throw "Đã có $resolvedRegistrationFile; không ghi đè nội dung đăng ký UAT hiện hữu."
}

if ($HashSecretFile -and (Test-Path -LiteralPath $HashSecretFile -PathType Leaf)) {
    $hashSecret = [System.IO.File]::ReadAllText((Resolve-Path $HashSecretFile)).Trim()
}
elseif ($env:VNPAY_SANDBOX_HASH_SECRET) {
    $hashSecret = $env:VNPAY_SANDBOX_HASH_SECRET.Trim()
}
else {
    throw 'Cần VNPAY_SANDBOX_HASH_SECRET_FILE hoặc VNPAY_SANDBOX_HASH_SECRET cho tiến trình chuẩn bị.'
}
if ($hashSecret.Length -lt 32 -or $hashSecret -match '\s') {
    throw 'HashSecret Sandbox không hợp lệ.'
}

function New-RandomSecret([int]$ByteCount = 48) {
    $bytes = [byte[]]::new($ByteCount)
    $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $generator.GetBytes($bytes) } finally { $generator.Dispose() }
    return [Convert]::ToBase64String($bytes)
}

$secretNames = @(
    'mysql_root_password', 'mysql_app_password', 'redis_password', 'jwt_secret',
    'cache_manager_password', 'pii_encryption_key', 'admin_bootstrap_password',
    'crawler_admin_password', 'backup_encryption_password', 'vnpay_hash_secret',
    'vnpay_recurring_password', 'vnpay_recurring_client_secret',
    'vnpay_recurring_hash_secret', 'vietqr_webhook_secret',
    'alertmanager_webhook_url', 'grafana_admin_password'
)

New-Item -ItemType Directory -Path $resolvedSecretDirectory | Out-Null
try {
    foreach ($name in $secretNames) {
        $value = switch ($name) {
            'pii_encryption_key' { New-RandomSecret 32 }
            'vnpay_hash_secret' { $hashSecret }
            'vnpay_recurring_password' { New-RandomSecret }
            'vnpay_recurring_client_secret' { New-RandomSecret }
            'vnpay_recurring_hash_secret' { New-RandomSecret }
            'vietqr_webhook_secret' { New-RandomSecret }
            'alertmanager_webhook_url' { 'https://example.invalid/novel-plus-uat-alerts' }
            default { New-RandomSecret }
        }
        [System.IO.File]::WriteAllText((Join-Path $resolvedSecretDirectory $name), $value,
            [System.Text.UTF8Encoding]::new($false))
    }
    $hashSecret = $null

    if ([System.IO.Path]::IsPathRooted($SecretDirectory)) {
        $envSecretDirectory = $resolvedSecretDirectory.Replace('\', '/')
    }
    else {
        $normalizedSecretDirectory = $SecretDirectory.Replace('\', '/').TrimStart([char[]]@('.', '/'))
        $envSecretDirectory = './' + $normalizedSecretDirectory
    }
    $envContent = @"
COMPOSE_PROJECT_NAME=novel-plus-uat
IMAGE_TAG=uat
TZ=Asia/Ho_Chi_Minh
MYSQL_DATABASE=novel_plus
MYSQL_USER=novel
SECRETS_DIR=$envSecretDirectory
NOVEL_DOMAIN=$Domain
NOVEL_ADMIN_DOMAIN=admin.$Domain
NOVEL_CRAWL_DOMAIN=crawl.$Domain
NOVEL_GRAFANA_DOMAIN=grafana.$Domain
CADDY_ACME_EMAIL=$AcmeEmail
CADDY_HTTP_PORT=80
CADDY_HTTPS_PORT=443
NOVEL_THEME=green
CRAWLER_ADMIN_USERNAME=admin
ADMIN_BOOTSTRAP_USERNAME=admin
VNPAY_ENABLED=true
VNPAY_TMN_CODE=$TmnCode
VNPAY_PAY_URL=https://sandbox.vnpayment.vn/paymentv2/vpcpay.html
VNPAY_RETURN_URL=https://$Domain/pay/vnpay/return
VNPAY_QUERY_URL=https://sandbox.vnpayment.vn/merchant_webapi/api/transaction
VNPAY_XU_PER_1000_VND=100
VNPAY_ALLOWED_AMOUNTS_VND=10000,30000,50000,100000,200000,500000
VNPAY_RECONCILIATION_ENABLED=false
VNPAY_RECURRING_ENABLED=false
VIETQR_ENABLED=false
AUTHOR_PAYOUT_ENABLED=false
ALERTMANAGER_ALLOW_HTTP=false
BACKUP_DIR=./backups/uat
"@
    [System.IO.File]::WriteAllText($resolvedEnvFile, $envContent.TrimStart(),
        [System.Text.UTF8Encoding]::new($false))
    $registrationContent = @"
Kính gửi VNPAY,

Merchant đề nghị cấu hình callback trên môi trường Sandbox:

- TmnCode: $TmnCode
- IPN URL: https://$Domain/pay/vnpay/ipn
- Return URL: https://$Domain/pay/vnpay/return
- Phương thức IPN: GET

Hai URL đã có HTTPS công khai, không yêu cầu đăng nhập/CAPTCHA và giữ nguyên query string.
Vui lòng xác nhận sau khi cấu hình để Merchant thực hiện SIT.
"@
    [System.IO.File]::WriteAllText($resolvedRegistrationFile, $registrationContent.TrimStart(),
        [System.Text.UTF8Encoding]::new($false))
}
catch {
    if (Test-Path -LiteralPath $resolvedEnvFile) {
        Remove-Item -LiteralPath $resolvedEnvFile -Force
    }
    if (Test-Path -LiteralPath $resolvedSecretDirectory) {
        Remove-Item -LiteralPath $resolvedSecretDirectory -Recurse -Force
    }
    if (Test-Path -LiteralPath $resolvedRegistrationFile) {
        Remove-Item -LiteralPath $resolvedRegistrationFile -Force
    }
    throw
}

Write-Output 'Đã chuẩn bị cấu hình UAT ngoài Git.'
Write-Output "IPN URL cần gửi VNPAY: https://$Domain/pay/vnpay/ipn"
Write-Output "Return URL: https://$Domain/pay/vnpay/return"
Write-Output "Nội dung gửi VNPAY: $resolvedRegistrationFile"
Write-Output 'Bước tiếp theo: trỏ DNS về máy UAT rồi chạy scripts/start-vnpay-uat.ps1.'
