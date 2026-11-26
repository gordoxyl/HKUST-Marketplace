# Adds practice listings and deals to HKUST Marketplace's saved data (Windows), so the buyer's
# screens can be tried by hand without setting deals up through the seller's screens first:
# deals that are accepted (Mark Complete), borrowed (Return Item), countered (Agree to Counter),
# pending and completed, and listings that are available, Reserved, Lent and Claim Pending.
#
# Usage, from the repository root (the folder with pom.xml):
#   scripts\practice-data.cmd                  add the practice data to the saved data (marketplace.dat)
#   scripts\practice-data.cmd -Buyer bob       give the deals to this buyer (default: alice)
#   scripts\practice-data.cmd -Force           add another set although one is already there
#
# How it works: it builds the project and runs testsupport.PracticeData, which opens the save
# file the same way the app does and adds the records through the app's own rules. The save
# file is copied to marketplace-data\backups\<date-time>\ first. To start over, run
# scripts\reset-data.cmd.
#
# The app must be closed: a running app keeps its data in memory and would save it back.

[CmdletBinding(PositionalBinding = $false)]   # -Buyer must be named: a stray word is an error
param(
    [string]$Buyer,
    [switch]$Force
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$dataFolder = Join-Path $repoRoot 'marketplace-data'

# 1. Refuse while the app is running
$running = Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" |
    Where-Object { $_.CommandLine -like '*com.hkust.marketplace.MarketplaceApp*' }
if ($running) {
    Write-Host "The HKUST Marketplace app is running (process $($running.ProcessId -join ', '))." -ForegroundColor Red
    Write-Host "Close it first: a running app would save its current data straight back."
    exit 1
}

# 2. The save file; without one yet, PracticeData creates it with the demo data first
$file = Join-Path $dataFolder 'marketplace.dat'

# 3. Build the project and collect its classpath
Push-Location $repoRoot
try {
    Write-Host "Building the project (this takes a moment)..."
    & .\mvnw.cmd -q test-compile dependency:build-classpath "-Dmdep.outputFile=target\practice-classpath.txt"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "The project did not build, so nothing was changed." -ForegroundColor Red
        exit 1
    }
    $classpath = "target\classes;target\test-classes;" + (Get-Content 'target\practice-classpath.txt' -Raw).Trim()
    $java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java' }

    # 4. Back up the save file, then add the practice data to it
    if (Test-Path $file) {
        $backup = Join-Path (Join-Path $dataFolder 'backups') (Get-Date -Format 'yyyyMMdd-HHmmss')
        New-Item -ItemType Directory -Force -Path $backup | Out-Null
        Copy-Item -LiteralPath $file -Destination $backup
        Write-Host "Backup: $(Join-Path $backup (Split-Path -Leaf $file))"
    }
    $javaArgs = @('-cp', $classpath, 'com.hkust.marketplace.testsupport.PracticeData')
    if ($Buyer) { $javaArgs += $Buyer }
    if ($Force) { $javaArgs += '--force' }
    & $java @javaArgs
    $result = $LASTEXITCODE   # 0 added, 1 failed, 2 already there (not a failure)
} finally {
    Pop-Location
}

Write-Host ""
if ($result -eq 1) {
    Write-Host "Something went wrong; see the messages above." -ForegroundColor Red
    exit 1
}
if ($result -eq 0) {
    Write-Host "Done. Start the app and log in as the buyer to try the deals." -ForegroundColor Green
} else {
    Write-Host "Nothing was added."
}
