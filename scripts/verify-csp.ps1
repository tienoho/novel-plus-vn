param(
    [switch]$FailOnBlocker
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repository = Split-Path -Parent $PSScriptRoot
$templateRoots = @(
    'novel-front/src/main/resources/templates',
    'novel-admin/src/main/resources/templates',
    'novel-crawl/src/main/resources/templates',
    'templates/green/html',
    'templates/orange/html',
    'templates/dark/html',
    'templates/blue/html'
)
$scriptRoots = @(
    'novel-front/src/main/resources/static',
    'novel-admin/src/main/resources/static/js/appjs',
    'novel-crawl/src/main/resources/static',
    'templates/green/static',
    'templates/orange/static',
    'templates/dark/static',
    'templates/blue/static'
)
$vendorSegments = @(
    '\jquery', '\layui\', '\wangEditor\', '\bootstrap\', '\lib\',
    '\plugin\', '\plugins\', '\summernote\'
)

function Get-FirstPartyFiles {
    param([string[]]$Roots, [string]$Extension)

    foreach ($root in $Roots) {
        $absolute = Join-Path $repository $root
        if (-not (Test-Path -LiteralPath $absolute)) {
            continue
        }
        Get-ChildItem -LiteralPath $absolute -Recurse -File -Filter "*.$Extension" |
            Where-Object {
                $path = $_.FullName
                -not ($vendorSegments | Where-Object { $path -like "*$_*" })
            }
    }
}

function Find-Matches {
    param(
        [System.IO.FileInfo[]]$Files,
        [string]$Pattern,
        [string]$Rule
    )

    $findings = @()
    foreach ($file in $Files) {
        foreach ($match in Select-String -LiteralPath $file.FullName -Pattern $Pattern -AllMatches) {
            $findings += [pscustomobject]@{
                Rule = $Rule
                Path = $file.FullName.Substring($repository.Length + 1)
                Line = $match.LineNumber
                Text = $match.Line.Trim()
            }
        }
    }
    return $findings
}

function Find-InlineScriptBlocks {
    param([System.IO.FileInfo[]]$Files)

    $findings = @()
    $pattern = [regex]::new('<script\b([^>]*)>([\s\S]*?)</script>',
        [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
    foreach ($file in $Files) {
        $source = [System.IO.File]::ReadAllText($file.FullName)
        foreach ($match in $pattern.Matches($source)) {
            $attributes = $match.Groups[1].Value
            $body = $match.Groups[2].Value
            if ($attributes -match '\bsrc\s*=' `
                -or $attributes -match 'application/(?:ld\+json|json)' `
                -or [string]::IsNullOrWhiteSpace($body)) {
                continue
            }
            $line = 1 + ([regex]::Matches($source.Substring(0, $match.Index), "`n")).Count
            $findings += [pscustomobject]@{
                Rule = 'CSP-NONCE-COVERED'
                Path = $file.FullName.Substring($repository.Length + 1)
                Line = $line
                Text = $match.Value.Substring(0, [Math]::Min(160, $match.Value.Length)).Replace("`r", ' ').Replace("`n", ' ').Trim()
            }
        }
    }
    return $findings
}

$htmlFiles = @(Get-FirstPartyFiles -Roots $templateRoots -Extension 'html')
$jsTemplateFiles = @(Get-FirstPartyFiles -Roots @('novel-admin/src/main/resources/templates') -Extension 'js.vm')
$jsFiles = @((Get-FirstPartyFiles -Roots $scriptRoots -Extension 'js') + $jsTemplateFiles)
$clientFiles = @($htmlFiles + $jsFiles)
$nonceCovered = @(Find-InlineScriptBlocks -Files $htmlFiles)
$findings = @()
$findings += Find-Matches -Files $htmlFiles -Rule 'CSP-INLINE-HANDLER' `
    -Pattern '\son(?:click|submit|change|load|error|input|keyup|keydown|mouseover|mouseout|focus|blur)\s*='
$findings += Find-Matches -Files $htmlFiles -Rule 'CSP-JAVASCRIPT-URL' `
    -Pattern 'javascript\s*:'
$findings += Find-Matches -Files $htmlFiles -Rule 'CSP-REMOTE-SCRIPT' `
    -Pattern '<script\b[^>]*\bsrc\s*=\s*["'']https?://'
$findings += Find-Matches -Files $htmlFiles -Rule 'CSP-REMOTE-FRAME' `
    -Pattern '<iframe\b[^>]*\bsrc\s*=\s*["'']https?://(?!sandbox\.vnpayment\.vn(?:/|["'']))'
$findings += Find-Matches -Files $htmlFiles -Rule 'CSP-DOCUMENT-WRITE' `
    -Pattern 'document\.write(?:ln)?\s*\('
$findings += Find-Matches -Files $clientFiles -Rule 'CSP-DYNAMIC-SCRIPT' `
    -Pattern '(?:document\.)?createElement\s*\(\s*["'']script["'']\s*\)'
$findings += Find-Matches -Files $jsFiles -Rule 'CSP-DYNAMIC-HANDLER-MARKUP' `
    -Pattern '\bon(?:click|submit|change|load|error|input|keyup|keydown|mouseover|mouseout|focus|blur)\s*=\s*\\?["'']'
$findings += Find-Matches -Files $jsFiles -Rule 'CSP-JAVASCRIPT-URL-MARKUP' `
    -Pattern 'javascript\s*:'
$findings += Find-Matches -Files $jsFiles -Rule 'CSP-DYNAMIC-CODE' `
    -Pattern 'eval\s*\(|new\s+Function\s*\(|\.getScript\s*\(|dataType\s*:\s*["'']script["'']|jsonp\s*:'
$authFindings = @()
$authFindings += Find-Matches -Files $clientFiles -Rule 'AUTH-CLIENT-TOKEN-STORAGE' `
    -Pattern '(?:localStorage|sessionStorage)\s*\.\s*(?:getItem|setItem|removeItem)\s*\(\s*["''](?:token|jwt|access_?token|refresh_?token)["'']'
$authFindings += Find-Matches -Files $clientFiles -Rule 'AUTH-CLIENT-TOKEN-QUERY' `
    -Pattern '(?:[?&](?:token|access_token|refresh_token)=|/book/search\?token=)'
$authFindings += Find-Matches -Files $clientFiles -Rule 'AUTH-LEGACY-ENDPOINT' `
    -Pattern '/user/(?:loginOrRegist|addToCollect|cancelToCollect)\b'
$findings += $authFindings

$grouped = $findings | Group-Object Rule | Sort-Object Name
Write-Output ("CSP-NONCE-COVERED: {0}" -f $nonceCovered.Count)
foreach ($group in $grouped) {
    Write-Output ("{0}: {1}" -f $group.Name, $group.Count)
}
if ($findings.Count -eq 0) {
    Write-Output 'CSP_READY: no first-party executable blocker; Thymeleaf inline scripts are nonce-covered at render time.'
    exit 0
}

$findings | Sort-Object Rule, Path, Line | Format-Table Rule, Path, Line, Text -AutoSize
Write-Output ("CSP_NOT_READY: còn {0} blocker executable first-party; giữ CSP_ENFORCE=false." -f $findings.Count)
if ($authFindings.Count -gt 0) {
    Write-Output ("AUTH_CLIENT_NOT_READY: còn {0} vị trí token/endpoint auth legacy phía client." -f $authFindings.Count)
    exit 1
}
if ($FailOnBlocker) {
    exit 1
}
