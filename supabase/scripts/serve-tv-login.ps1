# Serve the TV QR approve page with a real text/html Content-Type.
# Supabase Storage and Edge Functions rewrite HTML to text/plain (+ nosniff),
# so phones show raw source. This script bakes supabase/web/tv-login.html with
# your public anon key, serves it locally, and prints a Cloudflare quick-tunnel URL.
#
# Usage (PowerShell, from repo root):
#   .\supabase\scripts\serve-tv-login.ps1
# Then set TV_LOGIN_WEB_BASE_URL to the printed .../tv-login.html URL and rebuild.

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

if ([string]::IsNullOrWhiteSpace($supabaseUrl) -or [string]::IsNullOrWhiteSpace($anonKey)) {
    throw "Missing NUVIO_SUPABASE_URL / NUVIO_SUPABASE_ANON_KEY (env or local*.properties)."
}

$port = 8788
if ($env:TV_LOGIN_SERVE_PORT) { $port = [int]$env:TV_LOGIN_SERVE_PORT }

$hostDir = Join-Path $root "supabase\web\hosted"
New-Item -ItemType Directory -Force -Path $hostDir | Out-Null
$htmlPath = Join-Path $root "supabase\web\tv-login.html"
$html = Get-Content -Raw -Path $htmlPath
$html = $html.Replace("__SUPABASE_URL__", $supabaseUrl.TrimEnd("/"))
$html = $html.Replace("__SUPABASE_ANON_KEY__", $anonKey)
[System.IO.File]::WriteAllText((Join-Path $hostDir "tv-login.html"), $html)

Write-Host "Starting local server on 127.0.0.1:$port ..."
$python = Start-Process -FilePath "python" -ArgumentList @("-m", "http.server", "$port", "--bind", "127.0.0.1") -WorkingDirectory $hostDir -PassThru -WindowStyle Hidden

$npx = (Get-Command npx.cmd -ErrorAction Stop).Source
$log = Join-Path $env:TEMP "cf-tv-login.log"
$err = Join-Path $env:TEMP "cf-tv-login.err"
Remove-Item $log, $err -Force -ErrorAction SilentlyContinue

Write-Host "Opening Cloudflare quick tunnel (keeps running until you Ctrl+C in this session's stop) ..."
$cf = Start-Process -FilePath $npx -ArgumentList @("--yes", "cloudflared", "tunnel", "--url", "http://127.0.0.1:$port") -RedirectStandardOutput $log -RedirectStandardError $err -PassThru -WindowStyle Hidden

try {
    $public = $null
    for ($i = 0; $i -lt 60; $i++) {
        Start-Sleep -Seconds 1
        $combined = ""
        foreach ($f in @($log, $err)) {
            if (Test-Path $f) { $combined += (Get-Content -Raw $f -ErrorAction SilentlyContinue) }
        }
        if ($combined -match 'https://[a-z0-9-]+\.trycloudflare\.com') {
            $public = $Matches[0]
            break
        }
    }
    if (-not $public) {
        throw "Could not find trycloudflare.com URL. See $log / $err"
    }

    $page = "$public/tv-login.html"
    Write-Host ""
    Write-Host "OK. Point TV_LOGIN_WEB_BASE_URL at:"
    Write-Host "  $page"
    Write-Host ""
    Write-Host "Then rebuild/install the app and reopen Sign in with phone."
    Write-Host "Leave this script running while you test (Ctrl+C stops the tunnel)."
    Write-Host "For a permanent host, upload supabase/web/hosted/tv-login.html to any static HTTPS host"
    Write-Host "(Cloudflare Pages, Netlify, GitHub Pages) that serves Content-Type: text/html."

    while ($true) {
        Start-Sleep -Seconds 30
        if ($python.HasExited -or $cf.HasExited) {
            throw "Server or tunnel exited unexpectedly."
        }
    }
}
finally {
    if ($cf -and -not $cf.HasExited) { Stop-Process -Id $cf.Id -Force -ErrorAction SilentlyContinue }
    if ($python -and -not $python.HasExited) { Stop-Process -Id $python.Id -Force -ErrorAction SilentlyContinue }
}
