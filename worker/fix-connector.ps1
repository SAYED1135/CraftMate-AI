# CraftMate Connector API - one-click repair for Windows PowerShell (v2)
#
# Fixes the case where the Supabase key was pasted as the secret NAME.
# It never writes any key to disk, and it sanitizes Wrangler errors before
# printing them so a secret name/value is not shown in terminal output.

[CmdletBinding()]
param(
    [string]$WorkerUrl = "https://craftmate-connector-api.craftmate-api-sayed.workers.dev",
    [string]$AccountId = "dfb3ce8dfd37faecb0c1845cfb4c3f02"
)

$ErrorActionPreference = "Continue"
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

# Never reuse the Cloudflare token that appeared in chat/screenshots.
if (Test-Path Env:CLOUDFLARE_API_TOKEN) {
    Remove-Item Env:CLOUDFLARE_API_TOKEN -ErrorAction SilentlyContinue
    Write-Host "Cleared CLOUDFLARE_API_TOKEN from this PowerShell session." -ForegroundColor Yellow
}

# Account ID is an identifier, not a secret. Setting it avoids Wrangler
# failing to infer the account after browser OAuth login.
if (-not [string]::IsNullOrWhiteSpace($AccountId)) {
    $env:CLOUDFLARE_ACCOUNT_ID = $AccountId
}

Require-Command node
Require-Command npm
Require-Command npx
Require-Command cmd

Write-Host "[1/7] Checking Wrangler version..." -ForegroundColor Cyan
$version = Invoke-NpxCapture "wrangler --version"
if ($version.Code -eq 0) { Write-Host (Protect-Text $version.Text).Trim() }

Write-Host "[2/7] Checking Cloudflare login..." -ForegroundColor Cyan
$who = Invoke-NpxCapture "wrangler whoami"
$whoSafe = Protect-Text $who.Text
if ($who.Code -ne 0 -or $whoSafe -match "not logged in|not authenticated|Missing credentials|Unable to retrieve email|no account") {
    Write-Host "Cloudflare browser login is required. A browser window will open..." -ForegroundColor Yellow
    & npx wrangler login
    if ($LASTEXITCODE -ne 0) {
        Write-Host (Protect-Text $whoSafe) -ForegroundColor Red
        throw "Wrangler login failed. Send only the red error text above; never send keys or secret-list output."
    }
    $who = Invoke-NpxCapture "wrangler whoami"
    if ($who.Code -ne 0) {
        Write-Host (Protect-Text $who.Text) -ForegroundColor Red
        throw "Still not logged in after browser login. Send only the red error text above."
    }
}

Write-Host "[3/7] Listing Worker secrets safely..." -ForegroundColor Cyan
$list = Invoke-NpxCapture "wrangler secret list"
if ($list.Code -ne 0) {
    $safeError = Protect-Text $list.Text
    if ($safeError -match "not logged in|not authenticated|Missing credentials|Authentication error|Invalid access token") {
        Write-Host "Login session was not usable. Opening browser login and retrying once..." -ForegroundColor Yellow
        & npx wrangler login
        $list = Invoke-NpxCapture "wrangler secret list"
    }
}
if ($list.Code -ne 0) {
    Write-Host "----- Wrangler error (sanitized) -----" -ForegroundColor Red
    Write-Host (Protect-Text $list.Text) -ForegroundColor Red
    Write-Host "--------------------------------------" -ForegroundColor Red
    throw "Could not list Worker secrets. Send only the sanitized red error above. Do NOT send raw secret-list output."
}

$names = @()
try {
    $parsed = $list.Text | ConvertFrom-Json
    foreach ($item in $parsed) {
        if ($item.name) { $names += [string]$item.name }
    }
} catch {
    $names = [regex]::Matches($list.Text, '"name"\s*:\s*"([^"]+)"') | ForEach-Object { $_.Groups[1].Value }
}

Write-Host "[4/7] Removing the wrongly-named secret if present..." -ForegroundColor Cyan
foreach ($name in $names) {
    $isWrongJwtName = $name.StartsWith("eyJ") -or $name.Length -gt 80
    if ($name -ne "SUPABASE_SERVICE_ROLE_KEY" -and $isWrongJwtName) {
        Write-Host "Deleting one wrong secret entry (its name and Wrangler output stay hidden)." -ForegroundColor Yellow
        $delete = Invoke-NpxCapture "wrangler secret delete $name"
        if ($delete.Code -ne 0) {
            Write-Host (Protect-Text $delete.Text) -ForegroundColor Red
            throw "Failed to delete the wrong secret. Send only the sanitized error above."
        }
        Write-Host "Wrong secret entry deleted." -ForegroundColor Green
    }
}

Write-Host "[5/7] Enter the NEW rotated Supabase service_role key." -ForegroundColor Cyan
Write-Host "If the key was ever pasted into chat, rotate it in Supabase first." -ForegroundColor Yellow
$secure = Read-Host "Paste NEW Supabase service_role key here (input stays hidden)" -AsSecureString
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try {
    $plain = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    if ([string]::IsNullOrWhiteSpace($plain)) { throw "The key was empty." }

    $plain | & npx wrangler secret put SUPABASE_SERVICE_ROLE_KEY
    if ($LASTEXITCODE -ne 0) { throw "Failed to save SUPABASE_SERVICE_ROLE_KEY." }
} finally {
    if ($ptr -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
    $plain = $null
    $secure = $null
}

Write-Host "[6/7] Redeploying the Worker..." -ForegroundColor Cyan
& npx wrangler deploy
if ($LASTEXITCODE -ne 0) { throw "Wrangler deploy failed." }

Write-Host "[7/7] Checking $WorkerUrl/health ..." -ForegroundColor Cyan
for ($i = 1; $i -le 15; $i++) {
    try {
        $health = Invoke-RestMethod -Uri "$WorkerUrl/health" -Method Get -TimeoutSec 15
        $health | ConvertTo-Json -Compress | Write-Host
        if ($health.ok -eq $true -and $health.configured -eq $true) {
            Write-Host ""
            Write-Host "SUCCESS: configured:true" -ForegroundColor Green
            Write-Host "Set this in plugins/CraftMateConnector/config.yml:" -ForegroundColor Green
            Write-Host "  api-url: `"$WorkerUrl`""
            Write-Host ""
            Write-Host "Security reminder: delete the Cloudflare API token that was shown in chat/screenshots:" -ForegroundColor Yellow
            Write-Host "https://dash.cloudflare.com/profile/api-tokens"
            exit 0
        }
    } catch {
        Write-Host "Waiting for Cloudflare to update... ($i/15)"
    }
    Start-Sleep -Seconds 4
}

throw "The Worker still does not report configured:true. Do not run /craftmate link yet."
