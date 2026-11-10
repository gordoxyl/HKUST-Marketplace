# Resets HKUST Marketplace's saved data to the demo data (Windows).
#
# Usage, from the repository root (the folder with pom.xml):
#   scripts\reset-data.cmd                 reset both the normal and the dev data
#   scripts\reset-data.cmd normal          reset only the normal data (marketplace.dat)
#   scripts\reset-data.cmd dev             reset only the dev-mode data (marketplace-dev.dat)
#   scripts\reset-data.cmd -NoBackup       erase instead of keeping a backup
#
# How it works: the app re-creates the demo data whenever its save file is missing. So this
# script moves the save files (and their audit snapshots and listing photos) out of the way, into
# marketplace-data\backups\<date-time>\, and the next start of the app begins from the demo
# data. With -NoBackup the files are deleted instead.
#
# The app must be closed: a running app keeps its data in memory and would save it back.

param(
    [ValidateSet('all', 'normal', 'dev')]
    [string]$Which = 'all',
    [switch]$NoBackup
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

# 2. Find the files that belong to the chosen save files
$baseNames = switch ($Which) {
    'normal' { @('marketplace') }
    'dev'    { @('marketplace-dev') }
    default  { @('marketplace', 'marketplace-dev') }
}
$label = switch ($Which) {
    'normal' { 'normal' }
    'dev'    { 'dev-mode' }
    default  { 'normal and dev-mode' }
}
$items = @()
if (Test-Path $dataFolder) {
    foreach ($base in $baseNames) {
        # The save file, a leftover .tmp, any .unreadable-<date> copies, and the audit and photo folders
        $items += Get-ChildItem -LiteralPath $dataFolder -Force |
            Where-Object { $_.Name -eq "$base.dat" -or $_.Name -like "$base.dat.*" -or
                           $_.Name -eq "$base-audit" -or $_.Name -eq "$base-photos" }
    }
}
if ($items.Count -eq 0) {
    Write-Host "Nothing to reset: there is no saved $label data, so the app already starts from the demo data."
    exit 0
}

# 3. Move them to a backup, or delete them
if ($NoBackup) {
    foreach ($item in $items) {
        Remove-Item -LiteralPath $item.FullName -Recurse -Force
        Write-Host "Deleted   $($item.Name)"
    }
} else {
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $backup = Join-Path (Join-Path $dataFolder 'backups') $stamp
    New-Item -ItemType Directory -Force -Path $backup | Out-Null
    foreach ($item in $items) {
        Move-Item -LiteralPath $item.FullName -Destination $backup
        Write-Host "Backed up $($item.Name)"
    }
    Write-Host "Backup: $backup"
}

Write-Host "Done. The next start of the app creates fresh demo data ($label)." -ForegroundColor Green
