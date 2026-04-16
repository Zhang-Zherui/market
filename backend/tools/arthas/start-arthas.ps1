$ErrorActionPreference = 'Stop'

$toolDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$jarPath = Join-Path $toolDir 'arthas-boot.jar'

if (-not (Test-Path $jarPath)) {
    Write-Host "arthas-boot.jar not found, downloading first..."
    & (Join-Path $toolDir 'install-arthas.ps1')
}

Write-Host "Starting Arthas..."
java -jar $jarPath
