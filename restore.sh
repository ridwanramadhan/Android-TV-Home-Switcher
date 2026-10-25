#!/usr/bin/env bash
# Android TV Restore Tool
#
# Restores an Android TV / Google TV to its original stock state using a
# previously created backup.
#
set -euo pipefail

VERSION="1.0.0"

SOURCE="${BASH_SOURCE[0]}"
while [[ -h "$SOURCE" ]]; do
  SOURCE_DIR="$(cd -P "$(dirname "$SOURCE")" && pwd)"
  SOURCE="$(readlink "$SOURCE")"
  [[ "$SOURCE" != /* ]] && SOURCE="$SOURCE_DIR/$SOURCE"
done
SCRIPT_DIR="$(cd -P "$(dirname "$SOURCE")" && pwd)"
BACKUPS_DIR="${BACKUPS_DIR:-$SCRIPT_DIR/backups}"
ENV_FILE="${ENV_FILE:-$SCRIPT_DIR/.env}"
DRY_RUN="${DRY_RUN:-0}"
TARGET_BACKUP=""
TARGET_DEVICE=""
DO_LIST=0

if [[ -t 1 && -z "${NO_COLOR:-}" ]]; then
  C_RESET=$'\033[0m'
  C_DIM=$'\033[2m'
  C_BOLD=$'\033[1m'
  C_GREEN=$'\033[32m'
  C_YELLOW=$'\033[33m'
  C_RED=$'\033[31m'
  C_BLUE=$'\033[34m'
else
  C_RESET=''
  C_DIM=''
  C_BOLD=''
  C_GREEN=''
  C_YELLOW=''
  C_RED=''
  C_BLUE=''
fi

info() { printf '%s==>%s %s\n' "$C_BLUE" "$C_RESET" "$*"; }
ok()   { printf '%s[ok]%s %s\n' "$C_GREEN" "$C_RESET" "$*"; }
warn() { printf '%s[!]%s %s\n' "$C_YELLOW" "$C_RESET" "$*" >&2; }
die()  { printf '%s[x]%s %s\n' "$C_RED" "$C_RESET" "$*" >&2; exit 1; }

usage() {
  cat <<EOF
${C_BOLD}Android TV Restore Tool${C_RESET} v$VERSION

Restores an Android TV to its original stock launcher and package state.

Usage:
  ./restore.sh [backup_path_or_device_ip] [options]
  ./restore.sh --list

Options:
  --device <ip:port>      TV IP address to restore to (overrides backup default)
  --dry-run               Preview restore actions without modifying the TV
  --list                  List all available backups in backups/
  -h, --help              Show this help

Examples:
  ./restore.sh                              # Restore latest backup
  ./restore.sh backups/AquaTV_20261025      # Restore specific backup folder
  ./restore.sh --device 192.168.1.50        # Restore latest backup to 192.168.1.50
  ./restore.sh --list                       # List backups
EOF
}

list_backups() {
  if [[ ! -d "$BACKUPS_DIR" ]]; then
    info "No backups found (directory $BACKUPS_DIR does not exist)."
    return 0
  fi

  local count=0 entry b_info b_model b_serial b_date b_stock
  printf '\n%s%-35s %-20s %-18s %s%s\n' "$C_BOLD" "BACKUP FOLDER" "MODEL" "DEVICE" "STOCK LAUNCHER" "$C_RESET"

  for entry in "$BACKUPS_DIR"/*; do
    [[ -d "$entry" ]] || continue
    # Skip symlinks like 'latest'
    [[ -L "$entry" ]] && continue
    b_info="$entry/backup_info.env"
    if [[ -f "$b_info" ]]; then
      b_model="$(grep '^BACKUP_MODEL=' "$b_info" | cut -d'=' -f2- | tr -d '"' || echo 'unknown')"
      b_serial="$(grep '^BACKUP_DEVICE_SERIAL=' "$b_info" | cut -d'=' -f2- | tr -d '"' || echo 'unknown')"
      b_stock="$(grep '^BACKUP_STOCK_PACKAGE=' "$b_info" | cut -d'=' -f2- | tr -d '"' || echo 'unknown')"
    else
      b_model="unknown"
      b_serial="unknown"
      b_stock="unknown"
    fi
    printf '%-35s %-20s %-18s %s\n' "$(basename "$entry")" "$b_model" "$b_serial" "$b_stock"
    count=$((count + 1))
  done

  if (( count == 0 )); then
    info "No backups found in $BACKUPS_DIR."
  else
    printf '\nTotal backups: %d\n\n' "$count"
  fi
}

find_latest_backup() {
  if [[ ! -d "$BACKUPS_DIR" ]]; then
    return 1
  fi
  if [[ -L "$BACKUPS_DIR/latest" && -d "$BACKUPS_DIR/latest" ]]; then
    printf '%s\n' "$BACKUPS_DIR/latest"
    return 0
  fi
  # Find newest non-symlink folder
  local newest
  newest="$(find "$BACKUPS_DIR" -mindepth 1 -maxdepth 1 -type d ! -type l | sort -r | head -n 1 || true)"
  if [[ -n "$newest" && -f "$newest/restore.sh" ]]; then
    printf '%s\n' "$newest"
    return 0
  fi
  return 1
}

# ---------------------------------------------------------------- arguments

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help|help)
      usage
      exit 0
      ;;
    --list|list)
      DO_LIST=1
      ;;
    --dry-run)
      DRY_RUN=1
      ;;
    --device)
      shift
      [[ $# -gt 0 ]] || die "--device requires an IP argument."
      TARGET_DEVICE="$1"
      ;;
    -*)
      die "Unknown option: $1"
      ;;
    *)
      if [[ -d "$1" ]]; then
        TARGET_BACKUP="$1"
      elif [[ "$1" == *.*.*.* ]]; then
        TARGET_DEVICE="$1"
      elif [[ -d "$BACKUPS_DIR/$1" ]]; then
        TARGET_BACKUP="$BACKUPS_DIR/$1"
      else
        die "Argument '$1' is neither an existing backup folder nor an IP address."
      fi
      ;;
  esac
  shift
done

if [[ "$DO_LIST" == "1" ]]; then
  list_backups
  exit 0
fi

if [[ -z "$TARGET_BACKUP" ]]; then
  TARGET_BACKUP="$(find_latest_backup || true)"
  if [[ -z "$TARGET_BACKUP" ]]; then
    die "No backup found in $BACKUPS_DIR. Run ./backup.sh first or specify a backup path."
  fi
  info "Using latest backup: ${C_BOLD}$(basename "$TARGET_BACKUP")${C_RESET}"
fi

[[ -f "$TARGET_BACKUP/restore.sh" ]] || die "Restore script not found in $TARGET_BACKUP (missing restore.sh)."

# Execute the self-contained restore script
RESTORE_ARGS=()
if [[ "$DRY_RUN" == "1" ]]; then
  RESTORE_ARGS+=("--dry-run")
fi
if [[ -n "$TARGET_DEVICE" ]]; then
  RESTORE_ARGS+=("$TARGET_DEVICE")
fi

exec "$TARGET_BACKUP/restore.sh" "${RESTORE_ARGS[@]}"
