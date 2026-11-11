# steakOS package control script (Windows). Lives in dist bin/ after pack-dist.
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File bin/steakos.ps1 {start|stop|status|logs|version|env}
param(
    [Parameter(Position = 0)]
    [ValidateSet('start', 'stop', 'status', 'logs', 'version', 'env')]
    [string]$Command = 'status'
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$AppDir = Join-Path $Root 'app'
$CfgDir = Join-Path $Root 'config'
$RunDir = Join-Path $Root 'run'
$LogDir = Join-Path $Root 'logs'
$PidFile = Join-Path $RunDir 'steakos.pid'
$LogFile = Join-Path $LogDir 'steakos.log'
$EnvFile = Join-Path $CfgDir 'steakos.env'
$EnvExample = Join-Path $CfgDir 'steakos.env.example'
$Jar = Join-Path $AppDir 'steakOS.jar'

function Load-EnvFile {
    $file = if (Test-Path $EnvFile) { $EnvFile } elseif (Test-Path $EnvExample) {
        Write-Warning "no config/steakos.env - using example defaults"
        $EnvExample
    } else { $null }

    if (-not $file) { return }

    Get-Content $file | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith('#')) { return }
        if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$') {
            $name = $Matches[1]
            $val = $Matches[2].Trim().Trim('"').Trim("'")
            Set-Item -Path "Env:$name" -Value $val
        }
    }
}

function Get-RuntimeConfig {
    Load-EnvFile
    if (-not $env:GRAILS_ENV) { $env:GRAILS_ENV = 'production' }
    if (-not $env:SERVER_PORT) { $env:SERVER_PORT = '9090' }
    if (-not $env:SERVER_ADDRESS) { $env:SERVER_ADDRESS = '0.0.0.0' }
    if (-not $env:JAVA_OPTS) { $env:JAVA_OPTS = '-Xms256m -Xmx640m -XX:+ExitOnOutOfMemoryError' }
}

function Get-PidIfRunning {
    if (-not (Test-Path $PidFile)) { return $null }
    $procId = [int](Get-Content $PidFile -Raw).Trim()
    $p = Get-Process -Id $procId -ErrorAction SilentlyContinue
    if ($p) { return $procId }
    return $null
}

switch ($Command) {
    'version' {
        $ver = Join-Path $Root 'VERSION'
        if (Test-Path $ver) { Get-Content $ver } else { Write-Output 'VERSION file missing' }
    }
    'env' {
        Get-RuntimeConfig
        Write-Output "ENV_FILE=$EnvFile"
        Write-Output "GRAILS_ENV=$($env:GRAILS_ENV)"
        Write-Output "SERVER_PORT=$($env:SERVER_PORT)"
        Write-Output "SERVER_ADDRESS=$($env:SERVER_ADDRESS)"
        Write-Output "STEAKOS_DB_HOST=$($env:STEAKOS_DB_HOST)"
        Write-Output "JAR=$Jar"
    }
    'status' {
        Get-RuntimeConfig
        $procId = Get-PidIfRunning
        if ($procId) {
            Write-Output "running  pid=$procId  port=$($env:SERVER_PORT)  log=$LogFile"
            exit 0
        }
        Write-Output 'stopped'
        exit 1
    }
    'start' {
        Get-RuntimeConfig
        if (-not (Test-Path $Jar)) { throw "missing $Jar" }
        $java = Get-Command java -ErrorAction SilentlyContinue
        if (-not $java) { throw 'java not found (need JDK 21+)' }

        New-Item -ItemType Directory -Force -Path $RunDir, $LogDir | Out-Null
        $existing = Get-PidIfRunning
        if ($existing) {
            Write-Output "already running  pid=$existing"
            exit 0
        }

        $argList = @(
            $env:JAVA_OPTS.Split(' ', [System.StringSplitOptions]::RemoveEmptyEntries)
            "-Dgrails.env=$($env:GRAILS_ENV)"
            "-Dserver.port=$($env:SERVER_PORT)"
            "-Dserver.address=$($env:SERVER_ADDRESS)"
            '-jar'
            $Jar
        )

        $errLog = Join-Path $LogDir 'steakos.err.log'
        New-Item -ItemType Directory -Force -Path $RunDir, $LogDir | Out-Null
        if (Test-Path $LogFile) { Remove-Item $LogFile -Force -ErrorAction SilentlyContinue }
        if (Test-Path $errLog) { Remove-Item $errLog -Force -ErrorAction SilentlyContinue }

        Write-Output "starting steakOS  env=$($env:GRAILS_ENV)  port=$($env:SERVER_PORT)"
        # Windows PowerShell refuses to send stdout and stderr to the same file.
        $proc = Start-Process -FilePath 'java' -ArgumentList $argList `
            -RedirectStandardOutput $LogFile -RedirectStandardError $errLog `
            -PassThru -WindowStyle Hidden
        Set-Content -Path $PidFile -Value $proc.Id -Encoding ascii
        Start-Sleep -Seconds 2
        if (Get-Process -Id $proc.Id -ErrorAction SilentlyContinue) {
            Write-Output "started  pid=$($proc.Id)"
            Write-Output "logs     $LogFile"
            Write-Output "errors   $errLog"
            Write-Output "open     http://127.0.0.1:$($env:SERVER_PORT)/tour/"
            Write-Output "open     http://localhost:$($env:SERVER_PORT)/tour/"
            Write-Output "If localhost refuses, use 127.0.0.1. Not ready until tour and wizard are finished."
        } else {
            $hint = ''
            if (Test-Path $errLog) { $hint = (Get-Content $errLog -Tail 20 | Out-String) }
            throw "process exited immediately - see $errLog`n$hint"
        }
    }
    'stop' {
        $procId = Get-PidIfRunning
        if (-not $procId) {
            if (Test-Path $PidFile) { Remove-Item $PidFile -Force }
            Write-Output 'not running'
            exit 0
        }
        Write-Output "stopping pid=$procId"
        Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
        Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
        Write-Output 'stopped'
    }
    'logs' {
        New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
        if (-not (Test-Path $LogFile)) { New-Item -ItemType File -Path $LogFile | Out-Null }
        Get-Content $LogFile -Wait -Tail 100
    }
}
