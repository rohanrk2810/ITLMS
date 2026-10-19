# Stops the whole IT-ILMS stack (data is kept).  .\stop.ps1
# Add -WipeData to also delete the databases and volumes.
param([switch]$WipeData)
Set-Location $PSScriptRoot
$flags = if ($WipeData) { @('-v') } else { @() }
docker compose -f deploy/docker/docker-compose.yml --profile piston down @flags
