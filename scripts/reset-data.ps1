# Resets HKUST Marketplace's saved data to the demo data (Windows).
#
# Usage, from the repository root (the folder with pom.xml):
#   scripts\reset-data.cmd                 reset the saved data (marketplace.dat)
#   scripts\reset-data.cmd -NoBackup       erase instead of keeping a backup
#
# How it works: the app re-creates the demo data whenever its save file is missing. So this
# script moves the save file (and its audit snapshots and listing photos) out of the way, into
# marketplace-data\backups\<date-time>\, and the next start of the app begins from the demo
# data. With -NoBackup the files are deleted instead.
#
# The app must be closed: a running app keeps its data in memory and would save it back.

[CmdletBinding(PositionalBinding = $false)]   # a stray word, such as the old "dev", is an error
param(
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

# 2. Find the save file and what belongs to it: a leftover .tmp, any .unreadable-<date> copies,
#    and the audit and photo folders
$items = @()
if (Test-Path $dataFolder) {
    $items = @(Get-ChildItem -LiteralPath $dataFolder -Force |
        Where-Object { $_.Name -eq 'marketplace.dat' -or $_.Name -like 'marketplace.dat.*' -or
                       $_.Name -eq 'marketplace-audit' -or $_.Name -eq 'marketplace-photos' })
}
if ($items.Count -eq 0) {
    Write-Host "Nothing to reset: there is no saved data, so the app already starts from the demo data."
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

Write-Host "Done. The next start of the app creates fresh demo data." -ForegroundColor Green
