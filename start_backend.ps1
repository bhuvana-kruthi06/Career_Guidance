$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$pythonExe = Join-Path $projectRoot 'venv\Scripts\python.exe'
$mavenName = if ($env:MAVEN_CMD) { $env:MAVEN_CMD } else { 'mvn' }
$mavenCommand = Get-Command -Name $mavenName -ErrorAction SilentlyContinue
$pomPath = Join-Path $projectRoot 'backend\pom.xml'

if (-not (Test-Path -LiteralPath $pythonExe)) {
    throw "Project virtual-environment Python was not found: $pythonExe"
}
if (-not $mavenCommand) {
    throw "Maven was not found. Add mvn to PATH or set MAVEN_CMD to the full path of mvn.cmd."
}
$mavenCmd = $mavenCommand.Source
if (-not $mavenCmd) { $mavenCmd = $mavenCommand.Name }

$env:PROJECT_ROOT = $projectRoot
$env:PYTHON_EXECUTABLE = $pythonExe
$env:SERVER_PORT = '8091'
$env:SERVER_ADDRESS = '127.0.0.1'

Write-Host "Starting the career matching API at http://127.0.0.1:8091 (Maven: $mavenCmd)"
Write-Host 'Keep this window open while using the frontend. Press Ctrl+C to stop it.'
& $mavenCmd -f $pomPath spring-boot:run
