#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Copyright 2026 The steakOS Authors
#
# Backup steakOS **dev** work to a USB stick (or any mounted path):
#   A) project source (rsync, excludes rebuildable junk)
#   B) PostgreSQL steakos_dev dump (pg_dump -Fc)
#
# Usage:
#   ./scripts/backup-dev-usb.sh
#   DEST_ROOT=/media/matt/USB_64GB/backups/steakOS ./scripts/backup-dev-usb.sh
#   MOUNT_CHECK= DEST_ROOT=/home/matt/backups/steakOS ./scripts/backup-dev-usb.sh
#   ./scripts/backup-dev-usb.sh --dry-run
#
# Exit codes:
#   0  success (or dry-run)
#   1  usage / config error
#   2  destination not mounted / not writable
#   3  source missing
#   4  rsync failed
#   5  pg_dump failed (source still may have been copied)
#   6  post-check failed

set -euo pipefail

# --- defaults (override with env) ---
SRC_ROOT="${SRC_ROOT:-/home/matt/Dev/grails/steakOS}"
DEST_ROOT="${DEST_ROOT:-/media/matt/USB_64GB/backups/steakOS}"
# Parent that must be a mountpoint (USB). Empty string = skip mount check.
# Use :- only when unset; allow explicit MOUNT_CHECK= to disable.
if [[ ! -v MOUNT_CHECK ]]; then
  MOUNT_CHECK="/media/matt/USB_64GB"
fi

PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5432}"
PGUSER="${PGUSER:-steakos}"
PGDATABASE="${PGDATABASE:-steakos_dev}"
# Prefer env; fall back to project dev default (local only)
export PGPASSWORD="${PGPASSWORD:-${STEAKOS_DB_PASSWORD:-steakos}}"

KEEP_DB_DUMPS="${KEEP_DB_DUMPS:-14}"   # how many .dump files to keep (0 = keep all)
LOG_FILE="${LOG_FILE:-}"               # empty = log only to stderr/stdout + DEST_ROOT/backup.log

DRY_RUN=0
if [[ "${1:-}" == "--dry-run" || "${1:-}" == "-n" ]]; then
  DRY_RUN=1
fi

ts() { date -u +%Y-%m-%dT%H:%M:%SZ; }
log() {
  local line="[$(ts)] $*"
  echo "$line" >&2
  if [[ -n "${LOG_FILE}" ]]; then
    echo "$line" >>"$LOG_FILE"
  fi
  if [[ -n "${DEST_LOG:-}" ]]; then
    echo "$line" >>"$DEST_LOG" 2>/dev/null || true
  fi
}

die() {
  log "ERROR: $*"
  exit "${2:-1}"
}

# --- preflight ---
[[ -d "$SRC_ROOT" ]] || die "Source not found: $SRC_ROOT" 3

if [[ -n "$MOUNT_CHECK" ]]; then
  if ! mountpoint -q "$MOUNT_CHECK" 2>/dev/null; then
    die "USB (or mount) not mounted: $MOUNT_CHECK — plug in / mount the stick, then retry" 2
  fi
fi

if [[ "$DRY_RUN" -eq 0 ]]; then
  mkdir -p "$DEST_ROOT/source" "$DEST_ROOT/db" || die "Cannot create $DEST_ROOT (writable?)" 2
  DEST_LOG="$DEST_ROOT/backup.log"
  touch "$DEST_LOG" 2>/dev/null || die "Cannot write $DEST_LOG" 2
else
  DEST_LOG=""
  log "DRY-RUN: no files will be written"
fi

log "=== steakOS USB backup start ==="
log "SRC=$SRC_ROOT"
log "DEST=$DEST_ROOT"
log "DB=${PGUSER}@${PGHOST}:${PGPORT}/${PGDATABASE}"

# --- A) source rsync ---
# USB sticks are often vfat/exfat/ntfs: no real symlinks/hardlinks/Unix perms.
FSTYPE=""
if [[ -n "$MOUNT_CHECK" ]] && command -v findmnt >/dev/null 2>&1; then
  FSTYPE="$(findmnt -n -o FSTYPE "$MOUNT_CHECK" 2>/dev/null || true)"
fi
log "filesystem=$FSTYPE"

RSYNC_FLAGS=(--delete --human-readable)
case "${FSTYPE,,}" in
  vfat|fat|fat32|exfat|ntfs|fuseblk|msdos)
    # Copy symlink targets as files; drop hardlinks/owner (not supported)
    RSYNC_FLAGS+=(-rt --modify-window=1 --copy-links)
    log "rsync mode: USB/Windows FS (copy symlinks as files)"
    ;;
  *)
    RSYNC_FLAGS+=(-aH)
    log "rsync mode: Unix FS (archive + hardlinks)"
    ;;
esac
if [[ "$DRY_RUN" -eq 1 ]]; then
  RSYNC_FLAGS+=(--dry-run -v)
fi

EXCLUDES=(
  --exclude='.gradle/'
  --exclude='build/'
  --exclude='frontend/node_modules/'
  --exclude='frontend/dist/'
  --exclude='dist/out/'
  --exclude='**/.vite/'
  --exclude='**/*.log'
  --exclude='**/*~'
  --exclude='.idea/'
  --exclude='*.iml'
  --exclude='.DS_Store'
)

log "A) rsync source → $DEST_ROOT/source/"
set +e
rsync "${RSYNC_FLAGS[@]}" "${EXCLUDES[@]}" "$SRC_ROOT/" "$DEST_ROOT/source/"
rc=$?
set -e
# rsync 23 = partial transfer (attrs); treat as hard fail only if source dir empty/missing
if [[ $rc -ne 0 && $rc -ne 24 ]]; then
  # 24 = vanished source files mid-run
  if [[ $rc -eq 23 ]]; then
    log "WARN: rsync exit 23 (some attrs/files skipped) — continuing if source tree looks present"
  else
    die "rsync failed (exit $rc)" 4
  fi
fi
if [[ "$DRY_RUN" -eq 0 && ! -d "$DEST_ROOT/source" ]]; then
  die "rsync failed: destination source/ missing" 4
fi
log "A) rsync OK (exit $rc)"

# --- B) database dump ---
STAMP="$(date +%Y%m%d-%H%M%S)"
DUMP_NAME="steakos_dev-${STAMP}.dump"
DUMP_PATH="$DEST_ROOT/db/$DUMP_NAME"
LATEST_LINK="$DEST_ROOT/db/steakos_dev-latest.dump"

log "B) pg_dump → $DUMP_PATH"
if [[ "$DRY_RUN" -eq 1 ]]; then
  log "B) dry-run: would run pg_dump -Fc $PGDATABASE"
else
  if ! command -v pg_dump >/dev/null 2>&1; then
    die "pg_dump not found (install postgresql-client)" 5
  fi
  if ! pg_dump -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -Fc -f "$DUMP_PATH" "$PGDATABASE"; then
    die "pg_dump failed (is Postgres up? credentials? database $PGDATABASE?)" 5
  fi
  # "latest" pointer: symlink on Unix FS; plain text on exFAT/NTFS (no symlinks)
  case "${FSTYPE,,}" in
    vfat|fat|fat32|exfat|ntfs|fuseblk|msdos)
      echo "$DUMP_NAME" >"${LATEST_LINK}.txt"
      # also copy as latest file name for one-shot restore path
      cp -f "$DUMP_PATH" "$LATEST_LINK" || true
      ;;
    *)
      ln -sfn "$DUMP_NAME" "$LATEST_LINK"
      ;;
  esac
  log "B) pg_dump OK ($(du -h "$DUMP_PATH" | awk '{print $1}'))"

  # prune old dumps
  if [[ "$KEEP_DB_DUMPS" -gt 0 ]]; then
    mapfile -t dumps < <(ls -1t "$DEST_ROOT/db"/steakos_dev-*.dump 2>/dev/null | grep -v 'latest' || true)
    if [[ ${#dumps[@]} -gt $KEEP_DB_DUMPS ]]; then
      for old in "${dumps[@]:$KEEP_DB_DUMPS}"; do
        log "prune old dump: $old"
        rm -f "$old"
      done
    fi
  fi
fi

# --- marker + size summary ---
if [[ "$DRY_RUN" -eq 0 ]]; then
  {
    echo "last_success_utc=$(ts)"
    echo "src=$SRC_ROOT"
    echo "host=$(hostname -f 2>/dev/null || hostname)"
    echo "db=$PGDATABASE"
    echo "dump=$DUMP_NAME"
  } >"$DEST_ROOT/LAST_SUCCESS.txt"
  log "source size on stick: $(du -sh "$DEST_ROOT/source" 2>/dev/null | awk '{print $1}')"
  log "db dir size: $(du -sh "$DEST_ROOT/db" 2>/dev/null | awk '{print $1}')"
fi

log "=== steakOS USB backup finished OK ==="
exit 0
