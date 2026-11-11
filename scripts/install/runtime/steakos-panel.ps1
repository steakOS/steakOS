# steakOS local control panel. Stays up when Grails is stopped.
# Bind: http://127.0.0.1:9070/  (this PC only)
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File bin/steakos-panel.ps1
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$RunDir = Join-Path $Root 'run'
$LogDir = Join-Path $Root 'logs'
$Ctl = Join-Path $Root 'bin\steakos.ps1'
$EnvFile = Join-Path $Root 'config\steakos.env'
$TokenFile = Join-Path $RunDir 'panel.token'
$PidFile = Join-Path $RunDir 'panel.pid'
$PanelPort = 9070
New-Item -ItemType Directory -Force -Path $RunDir, $LogDir | Out-Null

function Read-EnvPort {
    $port = '9090'
    if (Test-Path $EnvFile) {
        foreach ($line in Get-Content $EnvFile) {
            if ($line -match '^\s*SERVER_PORT\s*=\s*(\d+)') { $port = $Matches[1] }
        }
    }
    return [int]$port
}

function Test-Tcp([int]$Port) {
    try {
        $c = New-Object System.Net.Sockets.TcpClient
        $iar = $c.BeginConnect('127.0.0.1', $Port, $null, $null)
        $ok = $iar.AsyncWaitHandle.WaitOne(400, $false) -and $c.Connected
        $c.Close()
        return [bool]$ok
    } catch { return $false }
}

function Get-Token {
    if (Test-Path $TokenFile) { return (Get-Content $TokenFile -Raw).Trim() }
    $t = [guid]::NewGuid().ToString('n')
    Set-Content -Path $TokenFile -Value $t -Encoding ascii
    return $t
}

function Invoke-Steakos([string]$Command) {
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $Ctl $Command
    return $LASTEXITCODE
}

function Invoke-Postgres([string]$Command) {
    $svc = Get-Service -Name '*postgresql*' -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $svc) { return 'PostgreSQL service not found' }
    try {
        if ($Command -eq 'start') { Start-Service $svc.Name }
        else { Stop-Service $svc.Name -Force }
        return 'ok'
    } catch {
        return $_.Exception.Message
    }
}

function Get-Status {
    $appPort = Read-EnvPort
    $pg = Test-Tcp 5432
    $app = Test-Tcp $appPort
    $vite = Test-Tcp 9001
    $webMode = 'included'
    $webUp = $app
    $webPort = $appPort
    $webDetail = 'Served by steakOS on the same port'
    if ($vite) {
        $webMode = 'vite'
        $webUp = $true
        $webPort = 9001
        $webDetail = 'Vite dev server'
    }
    return @{
        postgres = @{ up = $pg; port = 5432; detail = $(if ($pg) { 'Accepting connections' } else { 'Not accepting connections' }) }
        steakos  = @{ up = $app; port = $appPort; detail = $(if ($app) { 'Listening' } else { 'Stopped' }) }
        web      = @{ up = $webUp; port = $webPort; mode = $webMode; detail = $webDetail }
    }
}

function Send-Json($Res, $Obj, [int]$Code = 200) {
    $json = $Obj | ConvertTo-Json -Compress -Depth 6
    $bytes = [Text.Encoding]::UTF8.GetBytes($json)
    $Res.StatusCode = $Code
    $Res.ContentType = 'application/json; charset=utf-8'
    $Res.OutputStream.Write($bytes, 0, $bytes.Length)
}

function Send-Html($Res) {
    $token = Get-Token
    $html = @"
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>steakOS power</title>
<style>
  :root { color-scheme: dark; }
  body { margin: 0; font: 16px/1.4 system-ui, sans-serif; background: #14120e; color: #f4efe6; }
  main { max-width: 640px; margin: 0 auto; padding: 32px 20px 64px; }
  h1 { font-size: 1.6rem; margin: 0 0 4px; }
  p.lead { color: #b7aa98; margin: 0 0 28px; }
  .master { display: flex; align-items: center; justify-content: space-between; gap: 16px;
            padding: 18px 20px; border: 1px solid #3a3228; border-radius: 16px; margin-bottom: 18px; background: #1c1914; }
  button.power { width: 72px; height: 72px; border-radius: 50%; border: 3px solid #e8a04a; background: #2a2118;
                 color: #e8a04a; font-size: 28px; cursor: pointer; }
  button.power.on { background: #e8a04a; color: #1a1208; }
  button.power:disabled { opacity: 0.5; cursor: wait; }
  .row { display: flex; align-items: center; justify-content: space-between; gap: 12px;
         padding: 14px 16px; border-bottom: 1px solid #2c261f; }
  .name { font-weight: 650; }
  .detail { color: #b7aa98; font-size: 0.85rem; }
  .dot { width: 10px; height: 10px; border-radius: 50%; display: inline-block; margin-right: 8px; background: #6b6256; }
  .dot.on { background: #7dcea0; }
  button.mini { border: 1px solid #5c5144; background: transparent; color: inherit; border-radius: 999px; padding: 6px 12px; cursor: pointer; }
  a { color: #e8a04a; }
  #msg { min-height: 1.2em; color: #e8a04a; }
</style>
</head>
<body>
<main>
  <h1>steakOS</h1>
  <p class="lead">This page stays here when the application is off. It only listens on this computer.</p>
  <div class="master">
    <div>
      <div class="name">Power</div>
      <div class="detail" id="master-detail">steakOS and the web app</div>
    </div>
    <button class="power" id="master" title="Start or stop steakOS">⏻</button>
  </div>
  <section id="rows"></section>
  <p id="msg"></p>
  <p class="detail">Tour <a href="http://127.0.0.1:9090/tour/index.html">/tour</a> · Wizard <a href="http://127.0.0.1:9090/setup">/setup</a></p>
</main>
<script>
const token = '$token';
const rows = document.getElementById('rows');
const master = document.getElementById('master');
const msg = document.getElementById('msg');
function dot(on) { return '<span class="dot' + (on ? ' on' : '') + '"></span>'; }
function draw(s) {
  const items = [
    ['postgres', 'PostgreSQL', s.postgres],
    ['steakos', 'steakOS server', s.steakos],
    ['web', 'Web app', s.web]
  ];
  rows.innerHTML = items.map(([id, label, st]) =>
    '<div class="row"><div>' + dot(st.up) + '<span class="name">' + label + '</span>' +
    '<div class="detail">port ' + st.port + ' · ' + st.detail + '</div></div>' +
    '<button class="mini" data-id="' + id + '">' + (st.up ? 'Stop' : 'Start') + '</button></div>'
  ).join('');
  const appOn = s.steakos.up;
  master.classList.toggle('on', appOn);
  master.setAttribute('aria-pressed', appOn ? 'true' : 'false');
  document.querySelectorAll('button.mini').forEach(b => b.onclick = () => act(b.dataset.id, b.textContent === 'Stop' ? 'stop' : 'start'));
}
async function load() {
  const r = await fetch('/api/status');
  draw(await r.json());
}
async function act(target, op) {
  if (target === 'postgres' && op === 'stop' && !confirm('Stop PostgreSQL? steakOS will lose its database until you start it again.')) return;
  msg.textContent = op + ' ' + target + '…';
  master.disabled = true;
  const r = await fetch('/' + op + '?target=' + encodeURIComponent(target) + '&token=' + encodeURIComponent(token), { method: 'POST' });
  const body = await r.json();
  msg.textContent = body.message || '';
  master.disabled = false;
  load();
}
master.onclick = () => act('steakos', master.classList.contains('on') ? 'stop' : 'start');
load();
setInterval(load, 3000);
</script>
</body>
</html>
"@
    $bytes = [Text.Encoding]::UTF8.GetBytes($html)
    $Res.StatusCode = 200
    $Res.ContentType = 'text/html; charset=utf-8'
    $Res.OutputStream.Write($bytes, 0, $bytes.Length)
}

if (Test-Path $PidFile) {
    $old = [int](Get-Content $PidFile -Raw).Trim()
    if (Get-Process -Id $old -ErrorAction SilentlyContinue) {
        Start-Process "http://127.0.0.1:$PanelPort/"
        exit 0
    }
}
Set-Content -Path $PidFile -Value $PID -Encoding ascii

$listener = [System.Net.HttpListener]::new()
$listener.Prefixes.Add("http://127.0.0.1:${PanelPort}/")
try {
    $listener.Start()
} catch {
    Write-Error "Could not listen on 127.0.0.1:${PanelPort} — $($_.Exception.Message)"
    exit 1
}
Start-Process "http://127.0.0.1:$PanelPort/"

while ($listener.IsListening) {
    $ctx = $listener.GetContext()
    $req = $ctx.Request
    $res = $ctx.Response
    try {
        $path = $req.Url.AbsolutePath
        $q = $req.QueryString
        if ($path -eq '/' -or $path -eq '/status') { Send-Html $res }
        elseif ($path -eq '/api/status') { Send-Json $res (Get-Status) }
        elseif ($path -eq '/start' -or $path -eq '/stop') {
            $token = Get-Token
            if ($q['token'] -ne $token) { Send-Json $res @{ ok = $false; message = 'Missing token' } 403 }
            else {
                $target = $q['target']
                $op = $path.TrimStart('/')
                $message = 'ok'
                if ($target -eq 'steakos' -or $target -eq 'all') {
                    Invoke-Steakos $(if ($op -eq 'start') { 'start' } else { 'stop' }) | Out-Null
                    $message = "steakOS $op requested"
                } elseif ($target -eq 'postgres') {
                    $message = Invoke-Postgres $(if ($op -eq 'start') { 'start' } else { 'stop' })
                } elseif ($target -eq 'web') {
                    $message = 'The web app is part of steakOS. Use the steakOS power button.'
                } else { $message = 'Unknown target' }
                Send-Json $res @{ ok = $true; message = $message }
            }
        } else { $res.StatusCode = 404 }
    } catch {
        try { Send-Json $res @{ ok = $false; message = $_.Exception.Message } 500 } catch {}
    } finally { $res.Close() }
}
