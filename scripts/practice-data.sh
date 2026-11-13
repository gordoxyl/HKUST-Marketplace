#!/bin/sh
# Adds practice listings and deals to HKUST Marketplace's saved data (macOS / Linux), so the
# buyer's screens can be tried by hand before the seller's screens exist: deals that are
# accepted (Mark Complete), borrowed (Return Item), countered (Agree to Counter), pending and
# completed, and listings that are available, Reserved, Lent and Claim Pending.
#
# Usage, from the repository root (the folder with pom.xml):
#   sh scripts/practice-data.sh                 add to every save file that exists (normal and dev)
#   sh scripts/practice-data.sh normal          add only to the normal data (marketplace.dat)
#   sh scripts/practice-data.sh dev             add only to the dev-mode data (marketplace-dev.dat)
#   sh scripts/practice-data.sh --buyer bob     give the deals to this buyer (default: alice; "buyer" in dev mode)
#   sh scripts/practice-data.sh --force         add another set although one is already there
#
# How it works: it builds the project and runs testsupport.PracticeData, which opens the save
# file the same way the app does and adds the records through the app's own rules. The save
# file is copied to marketplace-data/backups/<date-time>/ first. To start over, run
# sh scripts/reset-data.sh.
#
# The app must be closed: a running app keeps its data in memory and would save it back.

set -eu

which_data="all"
buyer=""
force=""
while [ $# -gt 0 ]; do
  case "$1" in
    all|normal|dev) which_data="$1" ;;
    --buyer) shift; buyer="${1:-}" ;;
    --force) force="--force" ;;
    *) echo "Unknown option: $1 (use all, normal, dev, --buyer NAME or --force)"; exit 2 ;;
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

# 2. Which save files. "all" means the ones that exist; with none yet, the normal one is created.
case "$which_data" in
  normal) targets="normal" ;;
  dev) targets="dev" ;;
  *)
    targets=""
    [ -f "$data_folder/marketplace.dat" ] && targets="normal"
    [ -f "$data_folder/marketplace-dev.dat" ] && targets="$targets dev"
    [ -z "$targets" ] && targets="normal"
    ;;
esac

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

# 4. Back up each save file, then add the practice data to it
stamp="$(date +%Y%m%d-%H%M%S)"
failed="no"
added="no"
for target in $targets; do
  if [ "$target" = "dev" ]; then file="$data_folder/marketplace-dev.dat"; label="dev-mode data"
  else file="$data_folder/marketplace.dat"; label="normal data"; fi
  echo ""
  echo "== $label ($(basename "$file")) =="
  if [ -f "$file" ]; then
    mkdir -p "$data_folder/backups/$stamp"
    cp "$file" "$data_folder/backups/$stamp/"
    echo "Backup: $data_folder/backups/$stamp/$(basename "$file")"
  fi

  set -- -cp "$classpath" com.hkust.marketplace.testsupport.PracticeData
  [ "$target" = "dev" ] && set -- -Dmarketplace.dev=true "$@"
  [ -n "$buyer" ] && set -- "$@" "$buyer"
  [ -n "$force" ] && set -- "$@" "$force"
  status=0
  "$java" "$@" || status=$?
  [ "$status" -eq 0 ] && added="yes"
  [ "$status" -eq 1 ] && failed="yes"   # 2 means "already there": not a failure
done

echo ""
if [ "$failed" = "yes" ]; then
  echo "Something went wrong; see the messages above."
  exit 1
fi
if [ "$added" = "yes" ]; then
  echo "Done. Start the app and log in as the buyer to try the deals."
else
  echo "Nothing was added."
fi
