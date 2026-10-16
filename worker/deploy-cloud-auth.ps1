# CraftMate Cloud Auth - one-time Worker deployment for Windows PowerShell
#
# Purpose:
# - Deploy the Worker version that exposes POST /v1/auth/register.
# - The endpoint creates Supabase Auth users from the trusted Worker with
#   email_confirm=true, so beta registration does not use Supabase's built-in
#   2-emails/hour sender.
#
# Safety:
# - This script never asks for or prints the Supabase service_role key.
# - It only checks whether a secret NAMED SUPABASE_SERVICE_ROLE_KEY exists.
# - If that secret is missing, run fix-connector.ps1 first and enter the
#   rotated key only at its hidden prompt.

[CmdletBinding()]
param(
    [string]$WorkerUrl = "https://craftmate-connector-api.craftmate-api-sayed.workers.dev",
    [string]$AccountId = "dfb3ce8dfd37faecb0c1845cfb4c3f02"
)

$ErrorActionPreference = "Stop"
Set-Location -Path $PSScriptRoot
$WorkerUrl = $WorkerUrl.TrimEnd('/')

function Require-Command([string]$Name) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "$Name was not found. Install Node.js LTS first: https://nodejs.org/en/download"
    }
}

function Protect-Text([string]$Text) {
    if ([string]::IsNullOrEmpty($Text)) { return "" }
    $safe = $Text
    $safe = $safe -replace 'eyJ[A-Za-z0-9_\-\.]{20,}', '[REDACTED_JWT]'
    $safe = $safe -replace 'cfut_[A-Za-z0-9_\-]{10,}', '[REDACTED_CLOUDFLARE_TOKEN]'
    $safe = $safe -replace 'sb_secret_[A-Za-z0-9_\-\.]{10,}', '[REDACTED_SUPABASE_SECRET]'
    return $safe
}

function Invoke-NpxCapture([string]$Arguments) {
    $outFile = [System.IO.Path]::GetTempFileName()
    $errFile = [System.IO.Path]::GetTempFileName()
    try {
        $cmdLine = "npx $Arguments 1>""$outFile"" 2>""$errFile"""
        & cmd.exe /d /c $cmdLine | Out-Null
        $code = $LASTEXITCODE
        $out = ""
        $err = ""
        if (Test-Path $outFile) { $out = Get-Content -LiteralPath $outFile -Raw -ErrorAction SilentlyContinue }
        if (Test-Path $errFile) { $err = Get-Content -LiteralPath $errFile -Raw -ErrorAction SilentlyContinue }
        return [pscustomobject]@{ Code = $code; Text = (($out, $err) -join "`n") }
    } finally {
        Remove-Item -LiteralPath $outFile, $errFile -Force -ErrorAction SilentlyContinue
    }
}

if (Test-Path Env:CLOUDFLARE_API_TOKEN) {
    Remove-Item Env:CLOUDFLARE_API_TOKEN -ErrorAction SilentlyContinue
    Write-Host "Cleared CLOUDFLARE_API_TOKEN from this PowerShell session." -ForegroundColor Yellow
}
if (-not [string]::IsNullOrWhiteSpace($AccountId)) {
    $env:CLOUDFLARE_ACCOUNT_ID = $AccountId
}

Require-Command node
Require-Command npm
Require-Command npx
Require-Command cmd

Write-Host "[1/5] Checking Cloudflare login..." -ForegroundColor Cyan
$who = Invoke-NpxCapture "wrangler whoami"
$whoSafe = Protect-Text $who.Text
if ($who.Code -ne 0 -or $whoSafe -match "not logged in|not authenticated|Missing credentials|Unable to retrieve email|no account") {
    Write-Host "Cloudflare browser login is required. A browser window will open..." -ForegroundColor Yellow
    & npx wrangler login
    if ($LASTEXITCODE -ne 0) { throw "Wrangler login failed. Send only the sanitized error text; never send keys." }
}

Write-Host "[2/5] Checking that the Worker already has the Supabase secret name..." -ForegroundColor Cyan
$list = Invoke-NpxCapture "wrangler secret list"
if ($list.Code -ne 0) {
    Write-Host (Protect-Text $list.Text) -ForegroundColor Red
    throw "Could not list Worker secret names. Send only the sanitized red error above."
}
$names = @()
try {
    $parsed = $list.Text | ConvertFrom-Json
    foreach ($item in $parsed) { if ($item.name) { $names += [string]$item.name } }
} catch {
    $names = [regex]::Matches($list.Text, '"name"\s*:\s*"([^"]+)"') | ForEach-Object { $_.Groups[1].Value }
}
if ($names -notcontains "SUPABASE_SERVICE_ROLE_KEY") {
    throw "SUPABASE_SERVICE_ROLE_KEY is missing. Run worker/fix-connector.ps1 first and enter the ROTATED key only at the hidden prompt."
}
Write-Host "Secret name found. Its value was not read or printed." -ForegroundColor Green

Write-Host "[3/5] Deploying CraftMate Worker v0.4.0..." -ForegroundColor Cyan
& npx wrangler deploy
if ($LASTEXITCODE -ne 0) { throw "Wrangler deploy failed." }

Write-Host "[4/5] Waiting for /health to report auth_registration:true..." -ForegroundColor Cyan
for ($i = 1; $i -le 20; $i++) {
    try {
        $health = Invoke-RestMethod -Uri "$WorkerUrl/health" -Method Get -TimeoutSec 15
        if ($health.ok -eq $true -and $health.configured -eq $true -and $health.auth_registration -eq $true) {
            Write-Host ($health | ConvertTo-Json -Compress) -ForegroundColor Green
            Write-Host ""
            Write-Host "[5/5] SUCCESS: cloud registration endpoint is deployed." -ForegroundColor Green
            Write-Host "Open https://sayed1135.github.io/CraftMate-AI/app.html and create a new cloud account."
            Write-Host "Use a new email for the first acceptance test; do not reuse an unconfirmed legacy signup."
            exit 0
        }
        Write-Host "Health does not show the new endpoint yet. Waiting... ($i/20)"
    } catch {
        Write-Host "Waiting for Cloudflare to update... ($i/20)"
    }
    Start-Sleep -Seconds 4
}

throw "The Worker still does not report auth_registration:true. Do not test registration yet; send only the sanitized error text."
