# Deploy supabase/web/tv-login.html to the linked project's public `web` bucket.
# Usage (from repo root, PowerShell):
#   .\supabase\web\deploy-tv-login.ps1

$ErrorActionPreference = "Stop"
$root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
if (-not (Test-Path (Join-Path $root "local.dev.properties"))) {
  $root = (Get-Location).Path
}

$props = @{}
Get-Content (Join-Path $root "local.dev.properties") | ForEach-Object {
  if ($_ -match '^\s*([^#=]+)=(.*)$') {
    $props[$matches[1].Trim()] = $matches[2].Trim()
  }
}

$url = $props["NUVIO_SUPABASE_URL"]
$anon = $props["NUVIO_SUPABASE_ANON_KEY"]
if ([string]::IsNullOrWhiteSpace($url) -or [string]::IsNullOrWhiteSpace($anon)) {
  throw "NUVIO_SUPABASE_URL and NUVIO_SUPABASE_ANON_KEY are required in local.dev.properties"
}

$template = Get-Content -Raw (Join-Path $root "supabase\web\tv-login.html")
$html = $template.Replace("__SUPABASE_URL__", $url).Replace("__SUPABASE_ANON_KEY__", $anon)
$out = Join-Path $env:TEMP "nuvio-tv-login.html"
[System.IO.File]::WriteAllText($out, $html)

Write-Host "Generated: $out"
Write-Host "Upload target: $url/storage/v1/object/public/web/tv-login.html"
Write-Host ""
Write-Host "Set SUPABASE_SERVICE_ROLE_KEY in the environment, then this script uploads automatically."
Write-Host "Or Dashboard -> Storage -> web -> Upload tv-login.html (upsert)."

$service = $env:SUPABASE_SERVICE_ROLE_KEY
if ([string]::IsNullOrWhiteSpace($service)) {
  Write-Host "SUPABASE_SERVICE_ROLE_KEY not set; skipping upload."
  exit 0
}

$endpoint = "$url/storage/v1/object/web/tv-login.html"
curl.exe -sS -f -X POST $endpoint `
  -H "Authorization: Bearer $service" `
  -H "apikey: $service" `
  -H "Content-Type: text/html; charset=utf-8" `
  -H "x-upsert: true" `
  --data-binary "@$out" | Out-Host

Write-Host "Uploaded. Public URL:"
Write-Host "  $url/storage/v1/object/public/web/tv-login.html"
