# steakOS one-command installer (Windows native PowerShell).
#
# Usage:
#   irm http://steakos.lan:8088/install.ps1 | iex
#   # or with parameters (download then invoke):
#   & ([scriptblock]::Create((irm http://steakos.lan:8088/install.ps1))) -BaseUrl http://steakos.lan:8088
#   .\install.ps1 -BaseUrl http://192.168.1.10:8088 -Prefix "$env:LOCALAPPDATA\steakOS"
#   .\install.ps1 -OfflineZip C:\path\steakOS-V0.1.6-....zip
#   .\install.ps1 -BaseUrl ... -NoStart
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

# Windows PowerShell 5.1 Invoke-WebRequest does not follow GitHub's
# release-asset redirect (github.com -> release-assets.githubusercontent.com)
# reliably. The body comes back empty or as bytes, so latest.json looks like
# it has no "zip" field. curl.exe (Windows 10+) follows that chain.
function Save-HttpFile([string]$Url, [string]$Dest) {
    $curl = Join-Path $env:SystemRoot 'System32\curl.exe'
    if (-not (Test-Path $curl)) {
        $cmd = Get-Command curl.exe -ErrorAction SilentlyContinue
        if ($cmd) { $curl = $cmd.Source }
    }
    if (-not $curl -or -not (Test-Path $curl)) {
        throw "curl.exe not found. Windows 10 or later is required to download the steakOS package."
    }
    & $curl -fL --retry 3 --retry-delay 2 -A 'steakOS-install' -o $Dest $Url
    if ($LASTEXITCODE -ne 0) {
        throw "download failed (curl exit $LASTEXITCODE): $Url"
    }
    if (-not (Test-Path $Dest) -or ((Get-Item $Dest).Length -lt 1)) {
        throw "download was empty: $Url"
    }
}

function Assert-Java {
    $java = Get-Command java -ErrorAction SilentlyContinue
    if (-not $java) {
        throw 'java not found. Install Eclipse Temurin JDK 21 and re-open PowerShell.'
    }
    $old = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $line = (& cmd.exe /d /c "java -version 2>&1" | Select-Object -First 1 | Out-String).Trim()
    $ErrorActionPreference = $old
    if ($line -notmatch 'version "1\.(\d+)|version "(\d+)') {
        throw "Java 21+ required (could not read version: $line)"
    }
    $major = 0
    if ($line -match 'version "1\.(\d+)') { $major = [int]$Matches[1] }
    elseif ($line -match 'version "(\d+)') { $major = [int]$Matches[1] }
    if ($major -lt 21) { throw "Java 21+ required (found $major)" }
    Write-Info "Java: $line"
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

        # GitHub /releases/latest/download/<file> is two redirects onto a short-lived
        # CDN URL. Resolve the release with the JSON API, then download the zip asset
        # with curl.exe. A git tag named "latest" does not change this.
        $artUrl = $null
        $zipName = $null
        $sumUrl = $null
        $publicHost = ($BaseUrl -match 'github\.com') -or ($env:STEAKOS_REQUIRE_CHECKSUM -eq '1')
        # Matches .../releases/latest/download and .../releases/download/vX.Y.Z
        if ($BaseUrl -match 'https://github\.com/([^/]+)/([^/]+)/releases(?:/latest(?:/download)?|/download/([^/]+))?') {
            $ghOrg = $Matches[1]
            $ghRepo = $Matches[2]
            $ghTag = $Matches[3]
            if (-not $ghTag -or $ghTag -eq 'latest') {
                $api = "https://api.github.com/repos/$ghOrg/$ghRepo/releases/latest"
            } else {
                $api = "https://api.github.com/repos/$ghOrg/$ghRepo/releases/tags/$ghTag"
            }
            Write-Info "Resolving GitHub release $api"
            $relFile = Join-Path $tmp 'release.json'
            Save-HttpFile $api $relFile
            $rel = [IO.File]::ReadAllText($relFile) | ConvertFrom-Json
            if ($Version -and $rel.tag_name -and ($rel.tag_name -ne "v$Version") -and ($rel.tag_name -ne $Version)) {
                throw "GitHub latest is $($rel.tag_name) but -Version $Version was requested"
            }
            $zipAsset = @($rel.assets | Where-Object { $_.name -like 'steakOS-*.zip' } | Select-Object -First 1)
            if (-not $zipAsset -or -not $zipAsset[0].browser_download_url) {
                throw "GitHub release $($rel.tag_name) has no steakOS-*.zip asset"
            }
            $zipName = [string]$zipAsset[0].name
            $artUrl = [string]$zipAsset[0].browser_download_url
            $sumAsset = @($rel.assets | Where-Object { $_.name -eq 'checksums.sha256' } | Select-Object -First 1)
            if ($sumAsset -and $sumAsset[0].browser_download_url) {
                $sumUrl = [string]$sumAsset[0].browser_download_url
            }
            Write-Info "Release $($rel.tag_name) zip $zipName"
        } else {
            $metaUrl = "$BaseUrl/latest.json"
            Write-Info "Fetching $metaUrl"
            $metaFile = Join-Path $tmp 'latest.json'
            try {
                Save-HttpFile $metaUrl $metaFile
                $meta = [IO.File]::ReadAllText($metaFile) | ConvertFrom-Json
                if ($meta.zip) { $zipName = [string]$meta.zip }
                if ($Version -and $meta.softwareVersion -and ($Version -ne [string]$meta.softwareVersion)) {
                    throw "host latest is softwareVersion=$($meta.softwareVersion) but -Version $Version was requested"
                }
            } catch {
                if ($publicHost) { throw }
                Write-Info "latest.json not available - trying latest.zip ($($_.Exception.Message))"
                $zipName = 'latest.zip'
            }
            if (-not $zipName) {
                throw "latest.json at $metaUrl has no zip name. Not guessing a filename."
            }
            $artUrl = "$BaseUrl/$zipName"
            $sumUrl = "$BaseUrl/checksums.sha256"
        }

        $zipPath = Join-Path $tmp 'pkg.zip'
        Write-Info "Downloading $artUrl"
        Save-HttpFile $artUrl $zipPath

        # checksums - required for GitHub / STEAKOS_REQUIRE_CHECKSUM=1
        $requireSum = ($env:STEAKOS_REQUIRE_CHECKSUM -eq '1') -or ($BaseUrl -match 'github\.com')
        try {
            if (-not $sumUrl) { throw 'no checksums.sha256 asset' }
            $sumFile = Join-Path $tmp 'checksums.sha256'
            Save-HttpFile $sumUrl $sumFile
            $line = Get-Content $sumFile | Where-Object { $_ -match [regex]::Escape($zipName) } | Select-Object -First 1
            if ($line) {
                $expected = ($line -split '\s+')[0].Trim().ToLowerInvariant()
                $hash = (Get-FileHash -Path $zipPath -Algorithm SHA256).Hash.ToLowerInvariant()
                if ($expected -ne $hash) {
                    throw "checksum mismatch for $zipName"
                }
                Write-Info 'Checksum OK'
            } else {
                if ($requireSum) { throw "checksums.sha256 has no entry for $zipName (required on public host)" }
                Write-Info "checksums.sha256 has no entry for $zipName - skipping"
            }
        } catch {
            if ($requireSum) { throw }
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
        Write-Info "Wrote config\steakos.env from example - edit DB password before production use"
    }

    if (-not $NoStart) {
        Write-Info 'Starting steakOS'
        $ctl = Join-Path $Prefix 'bin\steakos.ps1'
        try {
            & $ctl start
        } catch {
            Write-Warning "start failed - check PostgreSQL and logs under $Prefix\logs"
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
