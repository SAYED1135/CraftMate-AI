# CraftMate Cloud Auth bootstrap (Windows PowerShell)
#
# Downloads the current CraftMate-AI main branch to a temporary folder and
# runs worker/deploy-cloud-auth.ps1. It does not ask for, read, or print any
# Supabase/Cloudflare secret value. Wrangler may open a browser for the
# owner's own Cloudflare login.

$ErrorActionPreference = "Stop"

$repoZip = "https://github.com/SAYED1135/CraftMate-AI/archive/refs/heads/main.zip"
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$base = Join-Path $env:TEMP "CraftMate-cloud-auth-$stamp"
$zipPath = Join-Path $env:TEMP "CraftMate-AI-main-$stamp.zip"

Write-Host "Downloading the latest CraftMate-AI release files..." -ForegroundColor Cyan
New-Item -ItemType Directory -Path $base -Force | Out-Null
Invoke-WebRequest -Uri $repoZip -OutFile $zipPath -UseBasicParsing
Expand-Archive -LiteralPath $zipPath -DestinationPath $base -Force

$deploy = Get-ChildItem -LiteralPath $base -Recurse -Filter "deploy-cloud-auth.ps1" | Select-Object -First 1
if (-not $deploy) {
    throw "deploy-cloud-auth.ps1 was not found in the downloaded repository. Files kept at: $base"
}

Write-Host "Starting the one-time Cloudflare Worker deployment..." -ForegroundColor Cyan
Write-Host "Files: $base" -ForegroundColor DarkGray
& powershell -NoProfile -ExecutionPolicy Bypass -File $deploy.FullName
$code = $LASTEXITCODE

if ($code -eq 0) {
    Write-Host ""
    Write-Host "Done. Open https://sayed1135.github.io/CraftMate-AI/app.html and register with a NEW email." -ForegroundColor Green
} else {
    Write-Host ""
    Write-Host "Deployment did not finish. Send only the sanitized error text shown above; never send secret keys." -ForegroundColor Yellow
}

exit $code
