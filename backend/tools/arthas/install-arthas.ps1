$ErrorActionPreference = 'Stop'

$toolDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$jarPath = Join-Path $toolDir 'arthas-boot.jar'
$downloadUrl = 'https://arthas.aliyun.com/arthas-boot.jar'

if (Test-Path $jarPath) {
    Write-Host "arthas-boot.jar already exists: $jarPath"
    exit 0
}

Write-Host "Downloading Arthas from $downloadUrl ..."
Invoke-WebRequest -Uri $downloadUrl -OutFile $jarPath
Write-Host "Downloaded to $jarPath"
