param(
    [ValidateSet('guest', 'account')]
    [string]$Mode = 'guest'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot

Write-Host 'Pathfinder demo checklist' -ForegroundColor Cyan
Write-Host "Mode: $Mode"
Write-Host 'Prerequisite: install Python, Java, Maven, Node.js; generate data/processed using the README pipeline.'

if (!(Test-Path (Join-Path $projectRoot 'data/processed/career_skill_map.csv'))) {
    throw 'Career data is missing. Follow README.md > Prepare the local data before the demo.'
}

if ($Mode -eq 'account' -and $env:AUTH_ENABLED -ne 'true') {
    throw 'Account demo needs AUTH_ENABLED=true, MySQL settings, and a private JWT_SECRET in the backend environment.'
}

Write-Host '1. Start the API in a separate terminal: .\start_backend.ps1'
if ($Mode -eq 'account') { Write-Host '2. Start MySQL first, register, then sign in and open Account.' }
else { Write-Host '2. Start the frontend in frontend/: npm run dev' }
Write-Host '3. Create a profile, choose interests, rate strengths, and review explained career matches.'
Write-Host '4. Open Career explorer, inspect one detail, save a career, and compare up to three.'
Write-Host '5. Show the resource-backed roadmap and adjust one to three ratings in the what-if simulator.'
if ($Mode -eq 'account') { Write-Host '6. Re-run a match and review the saved history chart; demonstrate local-data import only with disposable data.' }
Write-Host 'Scores are profile similarity measures, not hiring probabilities.'
