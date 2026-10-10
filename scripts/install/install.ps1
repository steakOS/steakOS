# steakOS one-command installer (Windows native PowerShell).
#
# Usage:
#   irm http://steakos.lan:8088/install.ps1 | iex
#   # or with parameters (download then invoke):
#   & ([scriptblock]::Create((irm http://steakos.lan:8088/install.ps1))) -BaseUrl http://steakos.lan:8088
#   .\install.ps1 -BaseUrl http://192.168.1.10:8088 -Prefix "$env:LOCALAPPDATA\steakOS"
#   .\install.ps1 -OfflineZip C:\path\steakOS-V0.1.6-….zip
#   .\install.ps1 -BaseUrl … -NoStart
#
# Environment:
#   STEAKOS_BASE_URL, STEAKOS_PREFIX, STEAKOS_VERSION
param(
    [string]$BaseUrl = $env:STEAKOS_BASE_URL,
    [string]$Prefix = $(if ($env:STEAKOS_PREFIX) { $env:STEAKOS_PREFIX } else { Join-Path $env:LOCALAPPDATA 'steakOS' }),
    [string]$OfflineZip = '',
    [string]$Version = $env:STEAKOS_VERSION,
    [switch]$NoStart
)

$ErrorActionPreference = 'Stop'

function Write-Info([string]$msg) { Write-Host "==> $msg" }

function Assert-Java {
    $java = Get-Command java -ErrorAction SilentlyContinue
    if (-not $java) {
        throw 'java not found. Install a JRE/JDK 17+ (e.g. Eclipse Temurin) and re-open PowerShell.'
    }
    Write-Info "Java: $(& java -version 2>&1 | Select-Object -First 1)"
}

$tmp = Join-Path ([System.IO.Path]::GetTempPath()) ("steakos-install-" + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $tmp | Out-Null
try {
    $zipPath = $null

    if ($OfflineZip) {
        if (-not (Test-Path $OfflineZip)) { throw "offline zip not found: $OfflineZip" }
        $zipPath = $OfflineZip
        Write-Info "Using offline package $zipPath"
    } else {
        if (-not $BaseUrl) {
            throw 'Set -BaseUrl or $env:STEAKOS_BASE_URL (or use -OfflineZip)'
        }
        $BaseUrl = $BaseUrl.TrimEnd('/')

        $metaUrl = "$BaseUrl/latest.json"
        $zipName = 'latest.zip'
        Write-Info "Fetching $metaUrl"
        try {
            $meta = Invoke-RestMethod -Uri $metaUrl -UseBasicParsing
            if ($meta.zip) { $zipName = [string]$meta.zip }
            if ($Version -and $meta.softwareVersion -and ($Version -ne $meta.softwareVersion)) {
                throw "host latest is softwareVersion=$($meta.softwareVersion) but -Version $Version was requested"
            }
        } catch {
            Write-Info "latest.json not available — trying latest.zip ($($_.Exception.Message))"
        }

        $artUrl = "$BaseUrl/$zipName"
        $zipPath = Join-Path $tmp 'pkg.zip'
        Write-Info "Downloading $artUrl"
        Invoke-WebRequest -Uri $artUrl -OutFile $zipPath -UseBasicParsing

        # optional checksums (best-effort)
        try {
            $sumUrl = "$BaseUrl/checksums.sha256"
            $sumFile = Join-Path $tmp 'checksums.sha256'
            Invoke-WebRequest -Uri $sumUrl -OutFile $sumFile -UseBasicParsing
            $line = Get-Content $sumFile | Where-Object { $_ -match [regex]::Escape($zipName) } | Select-Object -First 1
            if ($line) {
                $expected = ($line -split '\s+')[0].Trim().ToLowerInvariant()
                $hash = (Get-FileHash -Path $zipPath -Algorithm SHA256).Hash.ToLowerInvariant()
                if ($expected -ne $hash) {
                    throw "checksum mismatch for $zipName"
                }
                Write-Info 'Checksum OK'
            } else {
                Write-Info "checksums.sha256 has no entry for $zipName — skipping"
            }
        } catch {
            Write-Info "Checksum verify skipped: $($_.Exception.Message)"
        }
    }

    Assert-Java

    $extract = Join-Path $tmp 'extract'
    New-Item -ItemType Directory -Path $extract | Out-Null
    Write-Info "Extracting package"
    Expand-Archive -Path $zipPath -DestinationPath $extract -Force

    $pkgDir = Get-ChildItem -Path $extract -Directory | Select-Object -First 1
    if (-not $pkgDir) { throw 'package root not found inside archive' }
    $pkgPath = $pkgDir.FullName
    if (-not (Test-Path (Join-Path $pkgPath 'bin\steakos.ps1'))) {
        throw "unexpected package layout in $pkgPath"
    }

    Write-Info "Installing to $Prefix"
    New-Item -ItemType Directory -Force -Path $Prefix | Out-Null

    $preserveEnv = Join-Path $Prefix 'config\steakos.env'
    $envBackup = $null
    if (Test-Path $preserveEnv) {
        $envBackup = Join-Path $tmp 'steakos.env.bak'
        Copy-Item $preserveEnv $envBackup -Force
    }

    foreach ($name in @('app', 'web', 'bin', 'db')) {
        $dest = Join-Path $Prefix $name
        if (Test-Path $dest) { Remove-Item $dest -Recurse -Force }
    }

    Copy-Item -Path (Join-Path $pkgPath '*') -Destination $Prefix -Recurse -Force

    if ($envBackup -and (Test-Path $envBackup)) {
        $cfgDir = Join-Path $Prefix 'config'
        New-Item -ItemType Directory -Force -Path $cfgDir | Out-Null
        Copy-Item $envBackup (Join-Path $cfgDir 'steakos.env') -Force
    } elseif (-not (Test-Path (Join-Path $Prefix 'config\steakos.env'))) {
        Copy-Item (Join-Path $Prefix 'config\steakos.env.example') (Join-Path $Prefix 'config\steakos.env')
        Write-Info "Wrote config\steakos.env from example — edit DB password before production use"
    }

    if (-not $NoStart) {
        Write-Info 'Starting steakOS'
        $ctl = Join-Path $Prefix 'bin\steakos.ps1'
        try {
            & $ctl start
        } catch {
            Write-Warning "start failed — check PostgreSQL and logs under $Prefix\logs"
            Write-Warning "DB bootstrap: run db\setup-postgresql.sql as superuser"
        }
    } else {
        Write-Info "Skipping start (-NoStart). Run: powershell -File `"$Prefix\bin\steakos.ps1`" start"
    }

    Write-Host ''
    Write-Host 'steakOS installed'
    Write-Host "  prefix : $Prefix"
    $verFile = Join-Path $Prefix 'VERSION'
    if (Test-Path $verFile) {
        Write-Host '  version:'
        Get-Content $verFile | ForEach-Object { Write-Host "    $_" }
    }
    Write-Host "  control: powershell -File `"$Prefix\bin\steakos.ps1`" {start|stop|status|logs|version}"
    Write-Host "  config : $Prefix\config\steakos.env"
    Write-Host ''
} finally {
    Remove-Item -Path $tmp -Recurse -Force -ErrorAction SilentlyContinue
}
