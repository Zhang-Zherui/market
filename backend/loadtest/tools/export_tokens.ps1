$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$envName = 'market-loadtest'
$envFile = Join-Path $scriptDir 'environment.yml'
$condaExe = 'C:\ProgramData\miniconda3\Scripts\conda.exe'

if (Test-Path $condaExe) {
    $envListJson = & $condaExe env list --json
    if ($LASTEXITCODE -ne 0) {
        throw 'Failed to query conda environments.'
    }

    $envList = $envListJson | ConvertFrom-Json
    $envExists = $false
    foreach ($envPath in $envList.envs) {
        if ((Split-Path $envPath -Leaf) -eq $envName) {
            $envExists = $true
            break
        }
    }

    if (-not $envExists) {
        Write-Host "Creating conda environment '$envName'..."
        & $condaExe env create -f $envFile
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to create conda environment '$envName'."
        }
    }

    & $condaExe run --no-capture-output -n $envName python (Join-Path $scriptDir 'export_tokens.py') @Args
    exit $LASTEXITCODE
}

$python = Join-Path $env:CONDA_PREFIX 'python.exe'
if (-not (Test-Path $python)) {
    $python = 'python'
}

& $python (Join-Path $scriptDir 'export_tokens.py') @Args
