# Re-upload tv-login.html to Supabase Storage with Content-Type: text/html.
# Storage currently serves it as text/plain + nosniff, so phones show raw HTML source.
#
# Usage (PowerShell):
#   $env:SUPABASE_SERVICE_ROLE_KEY = "<service_role or sb_secret_...>"
#   .\supabase\scripts\upload-tv-login.ps1
#
# Optional overrides:
#   $env:NUVIO_SUPABASE_URL / $env:NUVIO_SUPABASE_ANON_KEY
#   or values are read from local.dev.properties / local.properties

$ErrorActionPreference = "Stop"
$root = Resolve-Path (Join-Path $PSScriptRoot "..\..")
Set-Location $root

function Read-Prop([string]$path, [string]$key) {
    if (-not (Test-Path $path)) { return $null }
    $line = Get-Content $path | Where-Object { $_ -match ("^\s*" + [regex]::Escape($key) + "\s*=") } | Select-Object -First 1
    if (-not $line) { return $null }
    return ($line -replace ("^\s*" + [regex]::Escape($key) + "\s*="), "").Trim()
}

$supabaseUrl = $env:NUVIO_SUPABASE_URL
if ([string]::IsNullOrWhiteSpace($supabaseUrl)) {
    $supabaseUrl = Read-Prop "local.dev.properties" "NUVIO_SUPABASE_URL"
}
if ([string]::IsNullOrWhiteSpace($supabaseUrl)) {
    $supabaseUrl = Read-Prop "local.properties" "NUVIO_SUPABASE_URL"
}

$anonKey = $env:NUVIO_SUPABASE_ANON_KEY
if ([string]::IsNullOrWhiteSpace($anonKey)) {
    $anonKey = Read-Prop "local.dev.properties" "NUVIO_SUPABASE_ANON_KEY"
}
if ([string]::IsNullOrWhiteSpace($anonKey)) {
    $anonKey = Read-Prop "local.properties" "NUVIO_SUPABASE_ANON_KEY"
}

$serviceRole = $env:SUPABASE_SERVICE_ROLE_KEY
if ([string]::IsNullOrWhiteSpace($serviceRole)) {
    $serviceRole = Read-Prop "local.dev.properties" "SUPABASE_SERVICE_ROLE_KEY"
}
if ([string]::IsNullOrWhiteSpace($serviceRole)) {
    $serviceRole = Read-Prop "local.properties" "SUPABASE_SERVICE_ROLE_KEY"
}

if ([string]::IsNullOrWhiteSpace($supabaseUrl) -or [string]::IsNullOrWhiteSpace($anonKey)) {
    throw "Missing NUVIO_SUPABASE_URL / NUVIO_SUPABASE_ANON_KEY (env or local*.properties)."
}
if ([string]::IsNullOrWhiteSpace($serviceRole)) {
    throw "Missing SUPABASE_SERVICE_ROLE_KEY. Set it in the environment (preferred) or local*.properties (do not commit)."
}

$htmlPath = Join-Path $root "supabase\web\tv-login.html"
$html = Get-Content -Raw -Path $htmlPath
$html = $html.Replace("__SUPABASE_URL__", $supabaseUrl.TrimEnd("/"))
$html = $html.Replace("__SUPABASE_ANON_KEY__", $anonKey)

$objectUrl = "$($supabaseUrl.TrimEnd('/'))/storage/v1/object/web/tv-login.html"
$publicUrl = "$($supabaseUrl.TrimEnd('/'))/storage/v1/object/public/web/tv-login.html"

Write-Host "Uploading to $objectUrl with Content-Type: text/html ..."
$bytes = [System.Text.Encoding]::UTF8.GetBytes($html)
Invoke-RestMethod -Method Post -Uri $objectUrl -Headers @{
    "Authorization" = "Bearer $serviceRole"
    "apikey" = $serviceRole
    "x-upsert" = "true"
    "Content-Type" = "text/html"
    "cache-control" = "no-cache"
} -Body $bytes | Out-Null

Write-Host "Verifying public Content-Type..."
$head = curl.exe -sI $publicUrl
$contentType = ($head | Select-String -Pattern "^Content-Type:" -CaseSensitive:$false | Select-Object -First 1).ToString()
Write-Host $contentType
if ($contentType -notmatch "text/html") {
    throw "Upload succeeded but Content-Type is still not text/html. Check bucket policies / CDN cache."
}

Write-Host ""
Write-Host "OK. Point TV_LOGIN_WEB_BASE_URL at:"
Write-Host "  $publicUrl"
Write-Host "Then regenerate the QR on the TV (refresh / reopen Sign in with phone)."
