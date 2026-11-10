# One command to build and run the whole IT-ILMS stack:  .\start.ps1
# Use  .\start.ps1 -SkipBuild  to reuse existing jars (faster).
param([switch]$SkipBuild)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$envFile = 'deploy/docker/.env'
if (-not (Test-Path $envFile)) {
    Copy-Item 'deploy/docker/.env.example' $envFile
    Write-Host "Created $envFile - fill in every CHANGE_ME, then run .\start.ps1 again." -ForegroundColor Yellow
    notepad $envFile
    exit 1
}
if (Select-String -Path $envFile -Pattern 'CHANGE_ME' -Quiet) {
    Write-Host "$envFile still contains CHANGE_ME values. Fix them and run again." -ForegroundColor Yellow
    exit 1
}

if (-not $SkipBuild) {
    # A stale JAVA_HOME breaks mvn; fall back to the newest installed JDK.
    if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\javac.exe")) {
        $jdk = Get-ChildItem 'C:\Program Files\Java' -Directory -Filter 'jdk-*' -ErrorAction SilentlyContinue |
            Where-Object { Test-Path "$($_.FullName)\bin\javac.exe" } | Sort-Object Name -Descending | Select-Object -First 1
        if ($jdk) { $env:JAVA_HOME = $jdk.FullName }
    }
    # clean: jars compiled by an IDE can contain "Unresolved compilation problem" classes.
    mvn -DskipTests clean package     
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

docker compose -f deploy/docker/docker-compose.yml --profile piston up -d --build
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "`nStarted. Wait until all rows are 'healthy':" -ForegroundColor Green
Write-Host "  docker compose -f deploy/docker/docker-compose.yml ps"
Write-Host "App: http://localhost:5173   Swagger: http://localhost:8080/swagger-ui.html"
