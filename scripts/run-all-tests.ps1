# PowerShell Automated Regression Test Runner for Khoi-Thu
$ErrorActionPreference = "Stop"

Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host "       Khoi-Thu Automated Regression Test Suite          " -ForegroundColor Cyan
Write-Host "===========================================================" -ForegroundColor Cyan

$MvnBin = "C:\Users\tienl\.codex\tools\apache-maven-3.9.11\bin\mvn.cmd"
if (-not (Test-Path $MvnBin)) {
    $MvnBin = "mvn"
}

$env:JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF-8"
Write-Host "[INFO] Executing Maven test suite..." -ForegroundColor Yellow
& $MvnBin test -pl novel-common, novel-front

if ($LASTEXITCODE -eq 0) {
    Write-Host "===========================================================" -ForegroundColor Green
    Write-Host "[SUCCESS] All regression tests passed successfully!" -ForegroundColor Green
    Write-Host "===========================================================" -ForegroundColor Green
}
else {
    Write-Host "[ERROR] Regression tests failed with exit code $LASTEXITCODE" -ForegroundColor Red
    exit $LASTEXITCODE
}
