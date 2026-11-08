# SPDX-License-Identifier: Apache-2.0
# Copyright 2026 The steakOS Authors
#
# steakOS bootstrap (Windows 11) - one-line install P3.
# Ensures Java 21 (Temurin) + PostgreSQL 16 + DB roles, then runs install.ps1.
#
# Usage (Admin PowerShell only when deps are missing):
#   irm https://raw.githubusercontent.com/steakOS/steakOS/main/scripts/install/bootstrap.ps1 | iex
#   .\bootstrap.ps1 -Yes
#   .\bootstrap.ps1 -SkipDeps -BaseUrl https://github.com/steakOS/steakOS/releases/latest/download
#
# Environment: STEAKOS_CHANNEL (github|forgejo), STEAKOS_BASE_URL, STEAKOS_RAW_BASE,
#   STEAKOS_PREFIX, STEAKOS_ASSUME_YES, STEAKOS_HUB_* (Forgejo override)

[CmdletBinding()]
param(
    [string] $BaseUrl = $env:STEAKOS_BASE_URL,
    [string] $Prefix = $(if ($env:STEAKOS_PREFIX) { $env:STEAKOS_PREFIX } else { Join-Path $HOME 'steakOS' }),
    [string] $OfflineZip = '',
    [string] $Version = $env:STEAKOS_VERSION,
    [switch] $NoStart,
    [switch] $SkipDeps,
    [switch] $NoDb,
    [switch] $Yes
)

$ErrorActionPreference = 'Stop'
# Default install channel: https://github.com/steakOS/steakOS
$Org = if ($env:STEAKOS_GITHUB_ORG) { $env:STEAKOS_GITHUB_ORG } else { 'steakOS' }
$Repo = if ($env:STEAKOS_GITHUB_REPO) { $env:STEAKOS_GITHUB_REPO } else { 'steakOS' }
$Channel = if ($env:STEAKOS_CHANNEL) { $env:STEAKOS_CHANNEL } else { 'github' }
$HubHost = if ($env:STEAKOS_HUB_HOST) { $env:STEAKOS_HUB_HOST } else { 'http://umbrel.local:8101' }
$HubOwner = if ($env:STEAKOS_HUB_OWNER) { $env:STEAKOS_HUB_OWNER } else { 'hermesbro' }
$HubRepo = if ($env:STEAKOS_HUB_REPO) { $env:STEAKOS_HUB_REPO } else { 'steakOS' }
if ($env:STEAKOS_RAW_BASE) {
    $RawBase = $env:STEAKOS_RAW_BASE
} elseif ($Channel -eq 'forgejo') {
    $RawBase = "$HubHost/$HubOwner/$HubRepo/raw/branch/main"
} else {
    $RawBase = "https://raw.githubusercontent.com/$Org/$Repo/main"
}
if ($env:STEAKOS_ASSUME_YES -eq '1') { $Yes = $true }

function Resolve-ReleaseBase {
    if ($env:STEAKOS_BASE_URL) { return $env:STEAKOS_BASE_URL.TrimEnd('/') }
    if ($BaseUrl) { return $BaseUrl.TrimEnd('/') }
    if ($Channel -ne 'forgejo') {
        return "https://github.com/$Org/$Repo/releases/latest/download"
    }
    $meta = Invoke-RestMethod -UseBasicParsing -Uri "$HubHost/api/v1/repos/$HubOwner/$HubRepo/releases/latest"
    if (-not $meta.tag_name) { Die 'latest release has no tag_name' }
    return "$HubHost/$HubOwner/$HubRepo/releases/download/$($meta.tag_name)"
}

function Write-Info([string] $Msg) { Write-Host "==> $Msg" }
function Die([string] $Msg) { throw "error: $Msg" }

function Test-IsAdmin {
    $id = [Security.Principal.WindowsIdentity]::GetCurrent()
    $p = New-Object Security.Principal.WindowsPrincipal($id)
    return $p.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

function Get-JavaMajor {
    # java -version writes to stderr. Under ErrorActionPreference=Stop that
    # becomes a terminating error and looks like "Java not found" (major 0).
    $old = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $out = & cmd.exe /d /c "java -version 2>&1"
        $line = @($out) | Select-Object -First 1 | Out-String
        if ($line -match 'version "1\.(\d+)') { return [int]$Matches[1] }
        if ($line -match 'version "(\d+)') { return [int]$Matches[1] }
    } catch { }
    finally {
        $ErrorActionPreference = $old
    }
    return 0
}

function Test-NeedJava21 {
    if (-not (Get-Command java -ErrorAction SilentlyContinue)) { return $true }
    return ((Get-JavaMajor) -lt 21)
}

function Test-PostgresReady {
    try {
        $tcp = New-Object System.Net.Sockets.TcpClient
        $iar = $tcp.BeginConnect('127.0.0.1', 5432, $null, $null)
        $ok = $iar.AsyncWaitHandle.WaitOne(800)
        if ($ok -and $tcp.Connected) { $tcp.Close(); return $true }
        $tcp.Close()
    } catch { }
    return $false
}

function Add-PostgresBinToPath {
    $roots = @(
        "${env:ProgramFiles}\PostgreSQL",
        "${env:ProgramFiles(x86)}\PostgreSQL"
    )
    foreach ($root in $roots) {
        if (-not (Test-Path $root)) { continue }
        $pgBin = Get-ChildItem -Path $root -Filter psql.exe -Recurse -ErrorAction SilentlyContinue |
            Sort-Object FullName -Descending |
            Select-Object -First 1
        if ($pgBin) {
            $env:Path = "$($pgBin.DirectoryName);$env:Path"
            return $pgBin.FullName
        }
    }
    return $null
}

function Confirm-Step([string] $Prompt) {
    if ($Yes) { return $true }
    $ans = Read-Host "$Prompt [y/N]"
    return ($ans -eq 'y' -or $ans -eq 'Y' -or $ans -eq 'yes')
}

function Install-Java21 {
    Write-Info 'Installing Eclipse Temurin JDK 21 (winget)'
    if (-not (Get-Command winget -ErrorAction SilentlyContinue)) {
        Die 'winget not found. Install Temurin 21 from https://adoptium.net and re-open PowerShell, or install App Installer.'
    }
    winget install -e --id EclipseAdoptium.Temurin.21.JDK --accept-package-agreements --accept-source-agreements
    # Refresh PATH for current session (common Adoptium location)
    $candidates = @(
        "${env:ProgramFiles}\Eclipse Adoptium\jdk-21*\bin",
        "${env:ProgramFiles}\Microsoft\jdk-21*\bin"
    )
    foreach ($pat in $candidates) {
        $hit = Get-Item $pat -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($hit) {
            $env:Path = "$($hit.FullName);$env:Path"
            break
        }
    }
}

function Install-Postgres16 {
    Write-Info 'Installing PostgreSQL 16 (winget)'
    if (-not (Get-Command winget -ErrorAction SilentlyContinue)) {
        Die 'winget not found. Install PostgreSQL 16 from https://www.postgresql.org/download/windows/ then re-run.'
    }
    $ids = @(
        'PostgreSQL.PostgreSQL.16',
        'PostgreSQL.PostgreSQL'
    )
    $ok = $false
    foreach ($id in $ids) {
        try {
            winget install -e --id $id --accept-package-agreements --accept-source-agreements
            $ok = $true
            break
        } catch {
            Write-Info "winget id $id failed; trying next"
        }
    }
    if (-not $ok) {
        Die 'Could not install PostgreSQL via winget. Use the EDB installer, then re-run with -SkipDeps after PATH includes psql.'
    }
    # Try start service
    Get-Service -Name '*postgresql*' -ErrorAction SilentlyContinue | ForEach-Object {
        if ($_.Status -ne 'Running') {
            try { Start-Service $_.Name } catch { }
        }
    }
    $pgBin = Get-ChildItem "${env:ProgramFiles}\PostgreSQL\*\bin\psql.exe" -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending | Select-Object -First 1
    if ($pgBin) {
        $env:Path = "$($pgBin.DirectoryName);$env:Path"
    }
}

function Invoke-DbSetup([string] $SqlPath) {
    if (-not (Test-Path $SqlPath)) { Die "DB setup SQL missing: $SqlPath" }
    Write-Info 'Creating steakOS databases (role + steakos_*)'
    $found = Add-PostgresBinToPath
    $psql = Get-Command psql -ErrorAction SilentlyContinue
    if (-not $psql) {
        if ($found) { Write-Warning "psql found at $found but could not be invoked" }
        else { Write-Warning 'psql not on PATH - run db\setup-postgresql.sql as postgres superuser manually' }
        return
    }
    $env:PGPASSWORD = if ($env:PGPASSWORD) { $env:PGPASSWORD } else { 'postgres' }
    & psql -U postgres -v ON_ERROR_STOP=1 -f $SqlPath
    foreach ($db in @('steakos_dev', 'steakos_test', 'steakos_prod')) {
        & psql -U postgres -d $db -v ON_ERROR_STOP=1 -c 'GRANT ALL ON SCHEMA public TO steakos; ALTER SCHEMA public OWNER TO steakos;' 2>$null
    }
}

# --- deps ---------------------------------------------------------------------
Write-Info "Windows bootstrap -> prefix $Prefix"

$needJava = Test-NeedJava21
$needPg = -not (Test-PostgresReady)
$needElev = ($needJava -or $needPg) -and (-not $SkipDeps)

if ($needElev -and -not (Test-IsAdmin)) {
    Die 'Administrator PowerShell required to install Java/PostgreSQL. Re-run elevated, or install deps manually and use -SkipDeps.'
}

if (-not $SkipDeps) {
    if ($needJava) { Install-Java21 } else { Write-Info "Java $(Get-JavaMajor)+ on PATH - skipping JDK install" }
    if ($needPg) { Install-Postgres16 } else { Write-Info 'PostgreSQL responding on 5432 - skipping install' }
} else {
    Write-Info 'Skipping dependency install (-SkipDeps)'
}

if (-not (Get-Command java -ErrorAction SilentlyContinue)) { Die 'java not found after install - open a new PowerShell and re-run' }
if ((Get-JavaMajor) -lt 21) { Die "Java 21+ required (found $(Get-JavaMajor))" }

# --- package installer --------------------------------------------------------
# iex (one-liner) has no script path. A saved file on Windows PowerShell 5.1
# also needs ExecutionPolicy Bypass. Always run install.ps1 as a file.
$here = $PSScriptRoot
if (-not $here) {
    try { $here = Split-Path -Parent $MyInvocation.MyCommand.Path } catch { $here = $null }
}
$installer = $null
if ($here) {
    $candidate = Join-Path $here 'install.ps1'
    if (Test-Path $candidate) { $installer = $candidate }
}
if (-not $installer) {
    Write-Info "Fetching install.ps1 from $RawBase"
    $installer = Join-Path $env:TEMP 'steakos-install.ps1'
    Invoke-WebRequest -UseBasicParsing "$RawBase/scripts/install/install.ps1" -OutFile $installer
}

$env:STEAKOS_REQUIRE_CHECKSUM = '1'
$env:GRAILS_ENV = 'production'
$psArgs = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $installer, '-Prefix', $Prefix, '-NoStart')
if ($OfflineZip) {
    $psArgs += @('-OfflineZip', $OfflineZip)
} else {
    $resolved = Resolve-ReleaseBase
    Write-Info "Release package host: $resolved"
    $psArgs += @('-BaseUrl', $resolved)
}
if ($Version) { $psArgs += @('-Version', $Version) }

Write-Info 'Installing steakOS package (production)'
& powershell.exe @psArgs

$sql = Join-Path $Prefix 'db\setup-postgresql.sql'
if (-not $NoDb) {
    try { Invoke-DbSetup $sql } catch { Write-Warning $_.Exception.Message }
}

if (-not $NoStart) {
    $ctl = Join-Path $Prefix 'bin\steakos.ps1'
    if (Test-Path $ctl) {
        Write-Info 'Starting steakOS'
        & $ctl start
    } else {
        Write-Warning "control script missing: $ctl"
    }
}

Write-Host ''
Write-Host 'steakOS bootstrap finished'
Write-Host "  prefix : $Prefix"
Write-Host "  control: $Prefix\bin\steakos.ps1 {start|stop|status|logs|version}"
Write-Host "  config : $Prefix\config\steakos.env"
Write-Host ''
Write-Host 'Open http://127.0.0.1:9090/tour/ for the introduction, then http://127.0.0.1:9090/setup for the configuration wizard.'
Write-Host 'This install runs in production. It does not ship a development password.'
