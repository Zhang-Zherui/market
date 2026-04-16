$ErrorActionPreference = 'Stop'
$env:BASE_URL = "http://host.docker.internal:8081"
$env:VOUCHER_ID = "7"
$env:AUTH_TOKENS_FILE = "/scripts/generated/auth_tokens.txt"

$backendDir = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
Push-Location $backendDir
try {
    docker compose --profile loadtest run --rm k6 run `
        -o experimental-prometheus-rw `
        --env LOAD_SCENARIO_MODE=ramping-arrival-rate `
        --env LOAD_START_RATE=300 `
        --env LOAD_PRE_ALLOCATED_VUS=500 `
        --env LOAD_MAX_VUS=5000 `
        --env LOAD_STAGES="15s:500,15s:1200,30s:2500,30s:4000,20s:0" `
        --env LOAD_SLEEP_MS=0 `
        /scripts/seckill.js
}
finally {
    Pop-Location
}
