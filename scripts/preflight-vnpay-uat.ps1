[CmdletBinding()]
param(
    [string]$EnvFile = '.env.uat',
    [ValidateRange(10, 1800)]
    [int]$TimeoutSeconds = 120
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$resolvedEnvFile = if ([System.IO.Path]::IsPathRooted($EnvFile)) {
    [System.IO.Path]::GetFullPath($EnvFile)
} else {
    [System.IO.Path]::GetFullPath((Join-Path $repoRoot $EnvFile))
}
if (-not (Test-Path -LiteralPath $resolvedEnvFile -PathType Leaf)) {
    throw "Thiếu $resolvedEnvFile."
}

function Get-EnvValue([string]$Name) {
    $line = Get-Content -Encoding UTF8 $resolvedEnvFile |
        Where-Object { $_ -match "^$([regex]::Escape($Name))=" } |
        Select-Object -Last 1
    if (-not $line) { return $null }
    return ($line -split '=', 2)[1].Trim()
}

$domain = Get-EnvValue 'NOVEL_DOMAIN'
if ($domain -notmatch '^(?:[A-Za-z0-9-]+\.)+[A-Za-z]{2,63}$') {
    throw 'NOVEL_DOMAIN trong cấu hình UAT không hợp lệ.'
}
$baseUrl = "https://$domain"
$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
do {
    try {
        $channels = Invoke-RestMethod -Uri "$baseUrl/pay/channels" -TimeoutSec 10
        $ipn = Invoke-RestMethod -Uri "$baseUrl/pay/vnpay/ipn?vnp_SecureHash=invalid" -TimeoutSec 10
        if (@($channels | Where-Object { $_.name -eq 'VNPAY' -and $_.enabled }).Count -eq 1 -and `
            $ipn.RspCode -eq '97') {
            break
        }
    }
    catch {
        Start-Sleep -Seconds 3
    }
} while ((Get-Date) -lt $deadline)

if (-not $channels -or -not $ipn -or $ipn.RspCode -ne '97') {
    throw 'HTTPS/IPN UAT chưa truy cập công khai hoặc VNPAY chưa được bật đúng.'
}
Write-Output 'Preflight UAT đạt: TLS/website/VNPAY channel/IPN checksum fail-closed.'
Write-Output "Đăng ký IPN URL với VNPAY: $baseUrl/pay/vnpay/ipn"
Write-Output "Return URL: $baseUrl/pay/vnpay/return"
Write-Output 'Sau khi VNPAY xác nhận đăng ký, chạy scripts/test-vnpay-uat.ps1.'
