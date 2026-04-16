$ErrorActionPreference = 'Stop'
$env:BASE_URL = "http://host.docker.internal:8081"
$env:VOUCHER_ID = "7"
$env:AUTH_TOKENS_FILE = "/scripts/generated/auth_tokens.txt"

$backendDir = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
Push-Location $backendDir
try {
    docker compose --profile loadtest run --rm k6 run -o experimental-prometheus-rw /scripts/seckill.js
}
finally {
    Pop-Location
}
