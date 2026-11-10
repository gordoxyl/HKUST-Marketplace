#!/usr/bin/env bash
# Resets HKUST Marketplace's saved data to the demo data (macOS / Linux).
#
# Usage, from the repository root (the folder with pom.xml):
#   sh scripts/reset-data.sh                 reset both the normal and the dev data
#   sh scripts/reset-data.sh normal          reset only the normal data (marketplace.dat)
#   sh scripts/reset-data.sh dev             reset only the dev-mode data (marketplace-dev.dat)
#   sh scripts/reset-data.sh --no-backup     erase instead of keeping a backup
#
# How it works: the app re-creates the demo data whenever its save file is missing. So this
# script moves the save files (and their audit snapshots and listing photos) out of the way, into
# marketplace-data/backups/<date-time>/, and the next start of the app begins from the demo
# data. With --no-backup the files are deleted instead.
#
# The app must be closed: a running app keeps its data in memory and would save it back.

set -eu

which_data="all"
backup="yes"
for arg in "$@"; do
  case "$arg" in
    all|normal|dev) which_data="$arg" ;;
    --no-backup) backup="no" ;;
    *) echo "Unknown option: $arg (use all, normal, dev or --no-backup)"; exit 2 ;;
  esac
done

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
data_folder="$repo_root/marketplace-data"

# 1. Refuse while the app is running
if command -v pgrep >/dev/null 2>&1; then
  if pgrep -f 'com\.hkust\.marketplace\.MarketplaceApp' >/dev/null 2>&1; then
    echo "The HKUST Marketplace app is running. Close it first: a running app would save its current data straight back."
    exit 1
  fi
else
  echo "Could not check whether the app is running (no pgrep). Make sure it is closed."
fi

# 2. Find the files that belong to the chosen save files
case "$which_data" in
  normal) bases="marketplace"; label="normal" ;;
  dev) bases="marketplace-dev"; label="dev-mode" ;;
  *) bases="marketplace marketplace-dev"; label="normal and dev-mode" ;;
esac
items=""
if [ -d "$data_folder" ]; then
  for base in $bases; do
    # The save file, a leftover .tmp, any .unreadable-<date> copies, and the audit and photo folders
    for item in "$data_folder/$base.dat" "$data_folder/$base.dat."* "$data_folder/$base-audit" "$data_folder/$base-photos"; do
      if [ -e "$item" ]; then
        items="$items
$item"
      fi
    done
  done
fi
if [ -z "$items" ]; then
  echo "Nothing to reset: there is no saved $label data, so the app already starts from the demo data."
  exit 0
fi

# 3. Move them to a backup, or delete them
if [ "$backup" = "no" ]; then
  echo "$items" | while IFS= read -r item; do
    [ -n "$item" ] || continue
    rm -rf "$item"
    echo "Deleted   $(basename "$item")"
  done
else
  backup_folder="$data_folder/backups/$(date +%Y%m%d-%H%M%S)"
  mkdir -p "$backup_folder"
  echo "$items" | while IFS= read -r item; do
    [ -n "$item" ] || continue
    mv "$item" "$backup_folder/"
    echo "Backed up $(basename "$item")"
  done
  echo "Backup: $backup_folder"
fi

echo "Done. The next start of the app creates fresh demo data ($label)."
