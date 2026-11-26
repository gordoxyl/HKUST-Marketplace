#!/bin/sh
# Adds practice listings and deals to HKUST Marketplace's saved data (macOS / Linux), so the
# buyer's screens can be tried by hand without setting deals up through the seller's screens
# first: deals that are accepted (Mark Complete), borrowed (Return Item), countered (Agree to
# Counter), pending and completed, and listings that are available, Reserved, Lent and Claim Pending.
#
# Usage, from the repository root (the folder with pom.xml):
#   sh scripts/practice-data.sh                 add the practice data to the saved data (marketplace.dat)
#   sh scripts/practice-data.sh --buyer bob     give the deals to this buyer (default: alice)
#   sh scripts/practice-data.sh --force         add another set although one is already there
#
# How it works: it builds the project and runs testsupport.PracticeData, which opens the save
# file the same way the app does and adds the records through the app's own rules. The save
# file is copied to marketplace-data/backups/<date-time>/ first. To start over, run
# sh scripts/reset-data.sh.
#
# The app must be closed: a running app keeps its data in memory and would save it back.

set -eu

buyer=""
force=""
while [ $# -gt 0 ]; do
  case "$1" in
    --buyer) shift; buyer="${1:-}" ;;
    --force) force="--force" ;;
    *) echo "Unknown option: $1 (use --buyer NAME or --force)"; exit 2 ;;
  esac
  shift
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

# 2. The save file; without one yet, PracticeData creates it with the demo data first
file="$data_folder/marketplace.dat"

# 3. Build the project and collect its classpath
cd "$repo_root"
echo "Building the project (this takes a moment)..."
if ! ./mvnw -q test-compile dependency:build-classpath -Dmdep.outputFile=target/practice-classpath.txt; then
  echo "The project did not build, so nothing was changed."
  exit 1
fi
case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) separator=";" ;;   # Git Bash on Windows
  *) separator=":" ;;
esac
classpath="target/classes${separator}target/test-classes${separator}$(cat target/practice-classpath.txt)"
if [ -n "${JAVA_HOME:-}" ]; then java="$JAVA_HOME/bin/java"; else java="java"; fi

# 4. Back up the save file, then add the practice data to it
if [ -f "$file" ]; then
  backup_folder="$data_folder/backups/$(date +%Y%m%d-%H%M%S)"
  mkdir -p "$backup_folder"
  cp "$file" "$backup_folder/"
  echo "Backup: $backup_folder/$(basename "$file")"
fi
set -- -cp "$classpath" com.hkust.marketplace.testsupport.PracticeData
[ -n "$buyer" ] && set -- "$@" "$buyer"
[ -n "$force" ] && set -- "$@" "$force"
status=0
"$java" "$@" || status=$?   # 0 added, 1 failed, 2 already there (not a failure)

echo ""
if [ "$status" -eq 1 ]; then
  echo "Something went wrong; see the messages above."
  exit 1
fi
if [ "$status" -eq 0 ]; then
  echo "Done. Start the app and log in as the buyer to try the deals."
else
  echo "Nothing was added."
fi
