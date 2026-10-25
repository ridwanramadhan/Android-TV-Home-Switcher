#!/usr/bin/env bash
# Android TV Home Switcher
#
# Universal launcher switcher for Android TV and Google TV devices:
#   default        - the stock launcher (Google TV launcherx, Android TV tvlauncher, PatchWall, etc.)
#   glass          - Glass TV, clean Apple TV-style grid, liquid glass, no widgets
#   glass-widgets  - Glass TV with beautiful digital clock & 3 Liquid Glass widgets (Prayer, Weather, AQI) + Adzan
#   backup         - Full backup of TV state, package configurations, and stock launcher APK
#   restore        - Restore the TV back to its factory stock launcher and configuration
#
# Attribution: https://github.com/cocombee/glass-tv
#
# Safe for all Android TV & Google TV devices (API 26+ / Android 8.0 - 14+).
# Automatically creates a baseline backup before first modification.
#
set -euo pipefail

VERSION="1.1.0"
# Resolve symlinks so the script still finds .env, backup.sh, and apks/ when linked into ~/bin.
SOURCE="${BASH_SOURCE[0]}"
while [[ -h "$SOURCE" ]]; do
  SOURCE_DIR="$(cd -P "$(dirname "$SOURCE")" && pwd)"
  SOURCE="$(readlink "$SOURCE")"
  [[ "$SOURCE" != /* ]] && SOURCE="$SOURCE_DIR/$SOURCE"
done
SCRIPT_DIR="$(cd -P "$(dirname "$SOURCE")" && pwd)"
ENV_FILE="${ENV_FILE:-$SCRIPT_DIR/.env}"
BACKUPS_DIR="${BACKUPS_DIR:-$SCRIPT_DIR/backups}"
DRY_RUN="${DRY_RUN:-0}"
NO_BACKUP="${NO_BACKUP:-0}"
TARGET_DEVICE=""
RESTORE_TARGET=""

if [[ -t 1 && -z "${NO_COLOR:-}" ]]; then
  C_RESET=$'\033[0m'
  C_DIM=$'\033[2m'
  C_BOLD=$'\033[1m'
  C_GREEN=$'\033[32m'
  C_YELLOW=$'\033[33m'
  C_RED=$'\033[31m'
  C_BLUE=$'\033[34m'
  C_CYAN=$'\033[36m'
else
  C_RESET=''
  C_DIM=''
  C_BOLD=''
  C_GREEN=''
  C_YELLOW=''
  C_RED=''
  C_BLUE=''
  C_CYAN=''
fi

info() { printf '%s==>%s %s\n' "$C_BLUE" "$C_RESET" "$*"; }
ok()   { printf '%s[ok]%s %s\n' "$C_GREEN" "$C_RESET" "$*"; }
warn() { printf '%s[!]%s %s\n' "$C_YELLOW" "$C_RESET" "$*" >&2; }
die()  { printf '%s[x]%s %s\n' "$C_RED" "$C_RESET" "$*" >&2; exit 1; }

usage() {
  cat <<EOF
${C_BOLD}Android TV Home Switcher${C_RESET} v$VERSION

Universal launcher switcher compatible with all Android TV and Google TV devices.

Usage:
  ./switch.sh <mode> [device_ip] [options]
  ./switch.sh status [device_ip]
  ./switch.sh backup [device_ip]
  ./switch.sh restore [backup_dir_or_ip]
  ./switch.sh list

Modes:
  default, stock, 1            Stock launcher (auto-detects Google TV, Android TV, PatchWall)
  glass, glass-fresh, fresh, 2 Glass TV clean grid (Apple TV style, liquid glass, no widgets)
  glass-widgets, widgets, 3    Glass TV with digital clock, 3 Liquid Glass widgets & Adzan sound

Management:
  status                  Show TV details, stock launcher detection, and backup status
  backup                  Create a full baseline backup of packages, settings & stock APK
  restore                 Restore the TV back to stock configuration from backup

Options:
  --device <ip:port>      Target TV address (overrides .env)
  --no-backup             Skip automatic backup on first switch
  --dry-run               Print the ADB commands without touching the TV
  -h, --help              Show this help

Examples:
  ./switch.sh status                      # Check current TV status
  ./switch.sh fresh                       # Switch to clean Aqua grid
  ./switch.sh default                     # Switch back to stock launcher
  ./switch.sh backup                      # Manually create backup
  ./switch.sh restore                     # Restore from latest backup
  ./switch.sh fresh 192.168.1.50          # Switch on a specific TV IP
EOF
}

list_modes() {
  printf '\n%s%-15s %-24s %s%s\n' "$C_BOLD" "MODE" "ALIASES" "WHAT IT SHOWS" "$C_RESET"
  printf '%-15s %-24s %s\n' "default" "stock, 1" "Stock TV launcher (Google TV / Android TV / OEM)"
  printf '%-15s %-24s %s\n' "glass" "glass-fresh, fresh, 2" "Glass TV, clean Apple TV-style grid (liquid glass, no widgets)"
  printf '%-15s %-24s %s\n' "glass-widgets" "widgets, dashboard, 3" "Glass TV + Digital Clock, 3 Liquid Glass widgets & Adzan"
  printf '%-15s %-24s %s\n' "backup" "dump, snapshot" "Full backup of packages, settings & stock APK"
  printf '%-15s %-24s %s\n' "restore" "recover, reset" "Revert to original stock launcher and packages"
  printf '\nRun ./switch.sh <mode>, for example: ./switch.sh glass\n\n'
}

load_env() {
  if [[ -f "$ENV_FILE" ]]; then
    set -a
    # shellcheck disable=SC1090
    . "$ENV_FILE"
    set +a
  fi
}

validate_config() {
  if [[ -n "$TARGET_DEVICE" ]]; then
    TV_HOST="$TARGET_DEVICE"
  fi
  TV_HOST="${TV_HOST:-}"
  TV_PORT="${TV_PORT:-5555}"
  CONNECT_TIMEOUT="${CONNECT_TIMEOUT:-20}"

  [[ -n "$TV_HOST" ]] || die "TV address is empty. Specify it as argument (./switch.sh status 192.168.1.50) or set TV_HOST in $ENV_FILE."

  case "$TV_PORT" in
    ''|*[!0-9]*) die "TV_PORT must be a number, not '$TV_PORT'." ;;
  esac
  case "$CONNECT_TIMEOUT" in
    ''|*[!0-9]*) die "CONNECT_TIMEOUT must be a number of seconds, not '$CONNECT_TIMEOUT'." ;;
  esac

  if [[ "$TV_HOST" == *:* ]]; then
    SERIAL="$TV_HOST"
  else
    SERIAL="$TV_HOST:$TV_PORT"
  fi
  SERIAL="${TV_SERIAL:-$SERIAL}"

  # Default fallbacks (will be auto-detected per connected device)
  STOCK_PACKAGE="${STOCK_PACKAGE:-com.google.android.tvlauncher}"
  STOCK_ACTIVITY="${STOCK_ACTIVITY:-$STOCK_PACKAGE/.MainActivity}"
  LAUNCHER_PACKAGE="${LAUNCHER_PACKAGE:-com.gathin.tvhome}"
  LAUNCHER_ACTIVITY="${LAUNCHER_ACTIVITY:-$LAUNCHER_PACKAGE/.MainActivity}"

  FRESH_APK="${FRESH_APK:-apks/glass-tv-fresh.apk}"
  WIDGETS_APK="${WIDGETS_APK:-apks/glass-tv-widgets.apk}"
  case "$FRESH_APK" in /*) ;; *) FRESH_APK="$SCRIPT_DIR/$FRESH_APK" ;; esac
  case "$WIDGETS_APK" in /*) ;; *) WIDGETS_APK="$SCRIPT_DIR/$WIDGETS_APK" ;; esac
  [[ -f "$FRESH_APK" ]] || FRESH_APK="$SCRIPT_DIR/apks/aqua-tv-home-fresh.apk"
  [[ -f "$WIDGETS_APK" ]] || WIDGETS_APK="$SCRIPT_DIR/apks/aqua-tv-home-widgets.apk"
}

find_adb() {
  local candidate
  if [[ -n "${ADB_PATH:-}" ]]; then
    [[ -x "$ADB_PATH" ]] || die "ADB_PATH is not executable: $ADB_PATH"
    printf '%s\n' "$ADB_PATH"
    return 0
  fi
  if command -v adb >/dev/null 2>&1; then
    command -v adb
    return 0
  fi
  for candidate in \
    "${ANDROID_HOME:-}/platform-tools/adb" \
    "${ANDROID_SDK_ROOT:-}/platform-tools/adb" \
    "$HOME/Library/Android/sdk/platform-tools/adb" \
    "$HOME/Android/Sdk/platform-tools/adb" \
    "/opt/homebrew/bin/adb" \
    "/usr/local/bin/adb"; do
    if [[ -n "$candidate" && -x "$candidate" ]]; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done
  die "adb was not found. Install Android platform-tools or set ADB_PATH in $ENV_FILE."
}

run_adb() {
  if [[ "$DRY_RUN" == "1" ]]; then
    printf '  [dry-run] adb -s'
    for arg in "$SERIAL" "$@"; do
      printf ' %s' "$arg"
    done
    printf '\n'
    return 0
  fi
  "$ADB_BIN" -s "$SERIAL" "$@"
}

connect_tv() {
  local state out i

  if [[ "$DRY_RUN" == "1" ]]; then
    printf '  [dry-run] adb connect %s\n' "$SERIAL"
    return 0
  fi

  state="$("$ADB_BIN" -s "$SERIAL" get-state 2>/dev/null || true)"
  if [[ "$state" == "device" ]]; then
    ok "Already connected: $SERIAL"
    return 0
  fi

  info "Connecting to $SERIAL"
  out="$("$ADB_BIN" connect "$SERIAL" 2>&1 || true)"
  case "$out" in
    *"connected to"*) ;;
    *"already connected"*) ;;
    "") ;;
    *) warn "$out" ;;
  esac

  i=0
  while (( i < CONNECT_TIMEOUT )); do
    state="$("$ADB_BIN" -s "$SERIAL" get-state 2>/dev/null || true)"
    case "$state" in
      device)
        ok "Connected: $SERIAL"
        return 0
        ;;
      unauthorized)
        die "The TV has not authorized this computer. Accept the 'Allow debugging?' prompt on the TV and run again."
        ;;
      offline) ;;
    esac
    sleep 1
    i=$((i + 1))
  done

  warn "adb devices:"
  "$ADB_BIN" devices 2>/dev/null | sed 's/^/  /' >&2 || true
  die "Could not connect to $SERIAL after ${CONNECT_TIMEOUT}s. Check TV IP address and ensure Network/ADB debugging is enabled on TV."
}

resolve_home() {
  local home
  home="$(run_adb shell 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME' 2>/dev/null | tr -d '\r' | tail -n 1 || true)"
  if [[ -z "$home" || "$home" == *"No activity found"* ]]; then
    home="$(run_adb shell 'pm resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME' 2>/dev/null | tr -d '\r' | grep -E '^[a-zA-Z0-9._]+/[a-zA-Z0-9._]+' | head -n 1 || true)"
  fi
  printf '%s\n' "$home"
}

detect_stock_launcher() {
  [[ "$DRY_RUN" == "1" ]] && return 0

  local current_home installed_pkgs
  current_home="$(resolve_home || true)"
  installed_pkgs="$(run_adb shell 'pm list packages -u' 2>/dev/null | tr -d '\r' || true)"

  # 1. If currently active home is NOT Aqua TV Home, that's the real stock launcher!
  if [[ -n "$current_home" && "$current_home" != "$LAUNCHER_PACKAGE"/* && "$current_home" == *"/"* ]]; then
    STOCK_ACTIVITY="$current_home"
    STOCK_PACKAGE="${current_home%%/*}"
    STOCK_LABEL="active system launcher"
    return 0
  fi

  # 2. Check for modern Google TV launcher
  if printf '%s\n' "$installed_pkgs" | grep -qx "package:com.google.android.apps.tv.launcherx"; then
    STOCK_PACKAGE="com.google.android.apps.tv.launcherx"
    STOCK_ACTIVITY="com.google.android.apps.tv.launcherx/.home.HomeActivity"
    STOCK_LABEL="Google TV"
    return 0
  fi

  # 3. Check for standard Android TV Leanback launcher
  if printf '%s\n' "$installed_pkgs" | grep -qx "package:com.google.android.tvlauncher"; then
    STOCK_PACKAGE="com.google.android.tvlauncher"
    STOCK_ACTIVITY="com.google.android.tvlauncher/.MainActivity"
    STOCK_LABEL="Android TV Leanback"
    return 0
  fi

  # 4. Check for Xiaomi PatchWall
  if printf '%s\n' "$installed_pkgs" | grep -qx "package:com.mitv.tvhome"; then
    STOCK_PACKAGE="com.mitv.tvhome"
    STOCK_ACTIVITY="com.mitv.tvhome/.MainActivity"
    STOCK_LABEL="Xiaomi PatchWall"
    return 0
  fi
  if printf '%s\n' "$installed_pkgs" | grep -qx "package:com.xiaomi.mitv.tvhome"; then
    STOCK_PACKAGE="com.xiaomi.mitv.tvhome"
    STOCK_ACTIVITY="com.xiaomi.mitv.tvhome/.MainActivity"
    STOCK_LABEL="Xiaomi PatchWall"
    return 0
  fi

  # 5. Check for Amazon Fire TV
  if printf '%s\n' "$installed_pkgs" | grep -qx "package:com.amazon.tv.launcher"; then
    STOCK_PACKAGE="com.amazon.tv.launcher"
    STOCK_ACTIVITY="com.amazon.tv.launcher/.ui.HomeActivity_v3"
    STOCK_LABEL="Amazon Fire TV"
    return 0
  fi

  # 6. Query registered HOME category activities
  local candidate
  candidate="$(run_adb shell 'cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME' 2>/dev/null | tr -d '\r' | grep -E '[a-zA-Z0-9._]+/[a-zA-Z0-9._]+' | grep -v "$LAUNCHER_PACKAGE" | head -n 1 | awk '{print $NF}' || true)"
  if [[ -n "$candidate" ]]; then
    STOCK_ACTIVITY="$candidate"
    STOCK_PACKAGE="${candidate%%/*}"
    STOCK_LABEL="discovered system home"
    return 0
  fi

  STOCK_LABEL="default fallback"
}

check_compatibility() {
  [[ "$DRY_RUN" == "1" ]] && return 0

  local sdk
  sdk="$(run_adb shell 'getprop ro.build.version.sdk' 2>/dev/null | tr -d '\r' | head -n 1 || true)"
  if [[ -n "$sdk" && "$sdk" =~ ^[0-9]+$ ]]; then
    if (( sdk < 26 )); then
      die "Unsupported Android TV version: SDK $sdk. Aqua TV Home requires Android 8.0+ (API 26 or higher)."
    fi
  fi
}

check_auto_backup() {
  [[ "$DRY_RUN" == "1" ]] && return 0
  [[ "$NO_BACKUP" == "1" ]] && return 0

  local brand model device_slug existing
  brand="$(run_adb shell 'getprop ro.product.brand' 2>/dev/null | tr -d '\r' | head -n 1 || echo 'Android')"
  model="$(run_adb shell 'getprop ro.product.model' 2>/dev/null | tr -d '\r' | head -n 1 || echo 'TV')"
  device_slug="$(printf '%s_%s' "$brand" "$model" | tr '[:space:]/\\:;*?"<>|' '_' | tr -s '_' | sed 's/^_//;s/_$//')"

  existing="$(find "$BACKUPS_DIR" -mindepth 1 -maxdepth 1 -type d -name "*${device_slug}*" 2>/dev/null | head -n 1 || true)"
  if [[ -z "$existing" ]]; then
    info "No previous backup found for ${C_BOLD}$brand $model${C_RESET}."
    info "Creating automatic baseline backup before applying changes..."
    "$SCRIPT_DIR/backup.sh" --device "$SERIAL"
  fi
}

set_home() {
  local component="$1" out
  info "Setting default Home: $component"

  if [[ "$DRY_RUN" == "1" ]]; then
    run_adb shell "cmd package set-home-activity --user 0 $component"
    return 0
  fi

  out="$(run_adb shell "cmd package set-home-activity --user 0 $component" 2>&1 || true)"
  case "$out" in
    *Error*|*error*|*Exception*|*exception*|*Unknown*|*unknown*)
      if [[ -n "$out" ]]; then
        warn "$out"
      fi
      out="$(run_adb shell "pm set-home-activity $component" 2>&1)" \
        || die "Could not set Home to $component: $out"
      case "$out" in
        *Error*|*error*|*Exception*|*exception*|*Unknown*|*unknown*) die "Could not set Home to $component: $out" ;;
      esac
      ;;
  esac
  if [[ -n "$out" ]]; then
    printf '%s\n' "$out" | sed 's/^/  /'
  fi
  ok "Default Home set"
}

launch_home() {
  local component="$1" package home out cmd
  package="${component%%/*}"
  info "Starting Home: $component"

  home=""
  if [[ "$DRY_RUN" == "0" ]]; then
    home="$(resolve_home || true)"
  fi

  if [[ -n "$home" && "$home" == "$package"/* ]]; then
    cmd="am start -W -a android.intent.action.MAIN -c android.intent.category.HOME"
  else
    cmd="am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -n $component"
  fi

  if [[ "$DRY_RUN" == "1" ]]; then
    run_adb shell "$cmd"
    return 0
  fi

  out="$(run_adb shell "$cmd" 2>&1 || true)"
  printf '%s\n' "$out" | sed 's/^/  /'
  case "$out" in
    *"Status: ok"*)
      ok "Home is on screen"
      ;;
    *"Error: Activity not started"*)
      die "The launcher did not start. Run './switch.sh default' to get the stock Home back."
      ;;
    *)
      warn "Android did not report 'Status: ok'. Press HOME on the remote to check."
      ;;
  esac
}

verify_home() {
  local expected="$1" required="${2:-0}" home i
  [[ "$DRY_RUN" == "1" ]] && return 0

  for i in 1 2 3; do
    home="$(resolve_home || true)"
    case "$home" in
      "$expected"/*)
        ok "Home now resolves to $home"
        return 0
        ;;
    esac
    if (( i < 3 )); then
      sleep 1
    fi
  done

  if [[ "$required" == "1" ]]; then
    warn "Home resolves to ${home:-nothing}, expected ${expected}/..."
    return 1
  fi
  case "$home" in
    "")
      warn "Could not verify the current Home activity."
      ;;
    *)
      warn "Home resolves to $home. (On some Google TV devices, press HOME on the remote and pick 'Always')."
      ;;
  esac
  return 0
}

switch_stock() {
  local out
  detect_stock_launcher
  info "${C_BOLD}Mode: Default Stock TV Launcher${C_RESET} ($STOCK_PACKAGE)"

  info "Enabling $STOCK_PACKAGE"
  if [[ "$DRY_RUN" == "1" ]]; then
    run_adb shell "pm enable --user 0 $STOCK_PACKAGE"
  else
    out="$(run_adb shell "pm enable --user 0 $STOCK_PACKAGE" 2>&1)" \
      || die "Could not enable $STOCK_PACKAGE: $out"
    if [[ -n "$out" ]]; then
      printf '%s\n' "$out" | sed 's/^/  /'
    fi
    case "$out" in
      *"Unknown package"*|*"not installed"*)
        die "$STOCK_PACKAGE is not installed on this TV."
        ;;
    esac
  fi

  set_home "$STOCK_ACTIVITY"
  launch_home "$STOCK_ACTIVITY"
  verify_home "$STOCK_PACKAGE"
}

switch_aqua() {
  local apk="$1" label="$2" out

  [[ -f "$apk" ]] || die "APK not found: $apk"
  detect_stock_launcher
  check_compatibility
  check_auto_backup

  info "${C_BOLD}Mode: $label${C_RESET}"
  info "Installing $(basename "$apk")"

  if [[ "$DRY_RUN" == "1" ]]; then
    run_adb install -r -d -g "$apk"
    run_adb shell "pm enable --user 0 $LAUNCHER_PACKAGE"
    run_adb shell "pm grant $LAUNCHER_PACKAGE android.permission.ACCESS_FINE_LOCATION"
    # Background-activity-start exemption: lets the alarm receiver open Adzan over Netflix.
    run_adb shell "appops set $LAUNCHER_PACKAGE SYSTEM_ALERT_WINDOW allow"
  else
    out="$(run_adb install -r -d -g "$apk" 2>&1 || true)"
    printf '%s\n' "$out" | sed 's/^/  /'
    case "$out" in
      *Success*)
        ok "APK installed"
        ;;
      *INSTALL_FAILED_UPDATE_INCOMPATIBLE*)
        die "The installed Aqua build was signed with a different key. Uninstall it with 'adb -s $SERIAL uninstall $LAUNCHER_PACKAGE' (this clears its settings), then run this switch again."
        ;;
      *)
        info "Standard install did not report success. Retrying via shell push (Android 14 fallback)..."
        local temp_apk="/data/local/tmp/$(basename "$apk")"
        run_adb push "$apk" "$temp_apk" >/dev/null 2>&1 || true
        out="$(run_adb shell "pm install -r -d -g $temp_apk" 2>&1 || true)"
        run_adb shell "rm -f $temp_apk" >/dev/null 2>&1 || true
        case "$out" in
          *Success*)
            ok "APK installed (via shell push fallback)"
            ;;
          *)
            die "Could not install $apk. Check that the TV has free space and that the APK is intact: $out"
            ;;
        esac
        ;;
    esac

    out="$(run_adb shell "pm enable --user 0 $LAUNCHER_PACKAGE" 2>&1 || true)"
    if [[ -n "$out" ]]; then
      printf '%s\n' "$out" | sed 's/^/  /'
    fi
    run_adb shell "pm grant $LAUNCHER_PACKAGE android.permission.ACCESS_FINE_LOCATION" >/dev/null 2>&1 || true
    # Background-activity-start exemption: lets the alarm receiver open Adzan over Netflix.
    run_adb shell "appops set $LAUNCHER_PACKAGE SYSTEM_ALERT_WINDOW allow" >/dev/null 2>&1 || true
    run_adb shell "am force-stop $LAUNCHER_PACKAGE" >/dev/null 2>&1 || true
  fi

  set_home "$LAUNCHER_ACTIVITY"
  launch_home "$LAUNCHER_ACTIVITY"

  # Never disable the stock launcher until the new Home is actually active, otherwise the
  # TV could be left with no working Home at all.
  if ! verify_home "$LAUNCHER_PACKAGE" 1; then
    warn "Re-enabling $STOCK_PACKAGE so the TV keeps a working Home."
    run_adb shell "pm enable --user 0 $STOCK_PACKAGE" >/dev/null 2>&1 || true
    die "Home did not switch to $LAUNCHER_PACKAGE. The TV was left on $STOCK_PACKAGE. Run './switch.sh default' to recover."
  fi

  # Verified: now disable stock launcher so the remote HOME button routes to Aqua TV Home
  if [[ -n "${STOCK_PACKAGE:-}" && "$STOCK_PACKAGE" != "$LAUNCHER_PACKAGE" ]]; then
    info "Home verified; setting remote HOME button to Aqua (disabling $STOCK_PACKAGE for user 0)..."
    if [[ "$DRY_RUN" == "1" ]]; then
      run_adb shell "pm disable-user --user 0 $STOCK_PACKAGE"
      run_adb shell "settings put secure enabled_accessibility_services com.gathin.tvhome/.HomeKeyService"
      run_adb shell "settings put secure accessibility_enabled 1"
    else
      run_adb shell "pm disable-user --user 0 $STOCK_PACKAGE" >/dev/null 2>&1 || true
      run_adb shell "settings put secure enabled_accessibility_services com.gathin.tvhome/.HomeKeyService" >/dev/null 2>&1 || true
      run_adb shell "settings put secure accessibility_enabled 1" >/dev/null 2>&1 || true
    fi
  fi
}

has_pkg() {
  printf '%s\n' "$1" | grep -qx "package:$2"
}

pkg_state() {
  if ! has_pkg "$installed" "$1"; then
    printf 'not installed'
  elif has_pkg "$disabled" "$1"; then
    printf 'disabled'
  else
    printf 'enabled'
  fi
}

status() {
  local brand model release sdk abi home custom_dump version code flavor mode
  local backup_count=0 backup_info=""

  if [[ "$DRY_RUN" == "1" ]]; then
    brand="DryRunBrand"
    model="DryRunModel"
    release="11"
    sdk="30"
    abi="arm64-v8a"
    home="com.google.android.apps.tv.launcherx/.home.HomeActivity"
    mode="Stock TV Launcher (Google TV)"
    STOCK_PACKAGE="com.google.android.apps.tv.launcherx"
    STOCK_LABEL="Google TV"
    installed=""
    disabled=""
    custom_dump=""
  else
    detect_stock_launcher

    brand="$(run_adb shell 'getprop ro.product.brand' 2>/dev/null | tr -d '\r' | head -n 1 || true)"
    model="$(run_adb shell 'getprop ro.product.model' 2>/dev/null | tr -d '\r' | head -n 1 || true)"
    release="$(run_adb shell 'getprop ro.build.version.release' 2>/dev/null | tr -d '\r' | head -n 1 || true)"
    sdk="$(run_adb shell 'getprop ro.build.version.sdk' 2>/dev/null | tr -d '\r' | head -n 1 || true)"
    abi="$(run_adb shell 'getprop ro.product.cpu.abi' 2>/dev/null | tr -d '\r' | head -n 1 || true)"
    home="$(resolve_home || true)"
    installed="$(run_adb shell 'pm list packages' 2>/dev/null | tr -d '\r' || true)"
    disabled="$(run_adb shell 'pm list packages -d' 2>/dev/null | tr -d '\r' || true)"
    custom_dump="$(run_adb shell "dumpsys package $LAUNCHER_PACKAGE" 2>/dev/null | tr -d '\r' || true)"
    version="$(printf '%s\n' "$custom_dump" | sed -n 's/.*versionName=\([^ ]*\).*/\1/p' | head -n 1)"
    code="$(printf '%s\n' "$custom_dump" | sed -n 's/.*versionCode=\([0-9]*\).*/\1/p' | head -n 1)"

    flavor="unknown"
    if has_pkg "$installed" "$LAUNCHER_PACKAGE" && [[ -n "$custom_dump" ]]; then
      if printf '%s\n' "$custom_dump" | grep -q 'android.permission.INTERNET'; then
        flavor="widgets"
      else
        flavor="fresh"
      fi
    fi

    case "$home" in
      "$LAUNCHER_PACKAGE"/*) mode="Aqua TV Home ($flavor)" ;;
      "$STOCK_PACKAGE"/*)    mode="Stock TV Launcher (${STOCK_LABEL:-default})" ;;
      "")                    mode="unknown" ;;
      *)                     mode="$home" ;;
    esac
  fi

  # Check backups
  if [[ -d "$BACKUPS_DIR" ]]; then
    local slug
    slug="$(printf '%s_%s' "${brand:-Android}" "${model:-TV}" | tr '[:space:]/\\:;*?"<>|' '_' | tr -s '_' | sed 's/^_//;s/_$//')"
    backup_count="$(find "$BACKUPS_DIR" -mindepth 1 -maxdepth 1 -type d -name "*${slug}*" 2>/dev/null | grep -c . || true)"
    if (( backup_count > 0 )); then
      backup_info="$backup_count backup(s) found in backups/"
    else
      backup_info="none yet (will auto-create on first switch)"
    fi
  else
    backup_info="none yet"
  fi

  printf '\n'
  printf '  %-12s %s\n' "Device" "$SERIAL"
  printf '  %-12s %s %s (Android %s, SDK %s, %s)\n' "Hardware" "${brand:-Generic}" "${model:-TV}" "${release:-?}" "${sdk:-?}" "${abi:-unknown}"
  printf '  %-12s %s\n' "Active Home" "${home:-unknown}"
  printf '  %-12s %s\n' "Current Mode" "$mode"
  printf '  %-12s %s [%s] (%s)\n' "Stock TV" "$STOCK_PACKAGE" "${STOCK_LABEL:-stock}" "$(pkg_state "$STOCK_PACKAGE")"
  if has_pkg "$installed" "$LAUNCHER_PACKAGE"; then
    printf '  %-12s %s %s (code %s) - %s, %s\n' \
      "Aqua Home" "$LAUNCHER_PACKAGE" "${version:-?}" "${code:-?}" "$flavor" "$(pkg_state "$LAUNCHER_PACKAGE")"
  else
    printf '  %-12s %s (not installed)\n' "Aqua Home" "$LAUNCHER_PACKAGE"
  fi
  printf '  %-12s %s\n' "Backups" "$backup_info"
  printf '\n'
}

# ---------------------------------------------------------------- arguments

ACTION=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help|help)
      usage
      exit 0
      ;;
    --list|list|modes)
      list_modes
      exit 0
      ;;
    --dry-run)
      DRY_RUN=1
      ;;
    --no-backup)
      NO_BACKUP=1
      ;;
    --device)
      shift
      [[ $# -gt 0 ]] || die "--device requires an IP or IP:PORT argument."
      TARGET_DEVICE="$1"
      ;;
    -*)
      die "Unknown option: $1"
      ;;
    *)
      if [[ "$1" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+ ]]; then
        TARGET_DEVICE="$1"
      elif [[ -z "$ACTION" ]]; then
        ACTION="$1"
      elif [[ "$ACTION" == "restore" && -z "$RESTORE_TARGET" ]]; then
        RESTORE_TARGET="$1"
      elif [[ -z "$TARGET_DEVICE" ]]; then
        TARGET_DEVICE="$1"
      else
        die "Unexpected argument: $1"
      fi
      ;;
  esac
  shift
done

if [[ -z "$ACTION" ]]; then
  usage
  exit 2
fi

case "$ACTION" in
  default|stock|1)                          MODE="default" ;;
  glass|glass-fresh|fresh|appletv|apple|2)  MODE="glass-fresh" ;;
  glass-widgets|widgets|dashboard|3)        MODE="glass-widgets" ;;
  status)                                   MODE="status" ;;
  backup|snapshot|dump)                     MODE="backup" ;;
  restore|recover|reset)                    MODE="restore" ;;
  *) die "Unknown mode '$ACTION'. Run './switch.sh list' to see available modes." ;;
esac

load_env
validate_config

if [[ "$MODE" == "backup" ]]; then
  BACKUP_ARGS=()
  [[ "$DRY_RUN" == "1" ]] && BACKUP_ARGS+=("--dry-run")
  [[ -n "$TARGET_DEVICE" ]] && BACKUP_ARGS+=("--device" "$TARGET_DEVICE")
  exec "$SCRIPT_DIR/backup.sh" "${BACKUP_ARGS[@]}"
fi

if [[ "$MODE" == "restore" ]]; then
  RESTORE_ARGS=()
  [[ "$DRY_RUN" == "1" ]] && RESTORE_ARGS+=("--dry-run")
  [[ -n "$TARGET_DEVICE" ]] && RESTORE_ARGS+=("--device" "$TARGET_DEVICE")
  [[ -n "$RESTORE_TARGET" ]] && RESTORE_ARGS+=("$RESTORE_TARGET")
  exec "$SCRIPT_DIR/restore.sh" "${RESTORE_ARGS[@]}"
fi

if [[ "$DRY_RUN" == "1" ]]; then
  ADB_BIN="${ADB_PATH:-adb}"
  printf '%s\n' "${C_DIM}Dry run: no commands will be sent to the TV.${C_RESET}"
else
  ADB_BIN="$(find_adb)"
fi

connect_tv

if [[ "$MODE" == "status" ]]; then
  status
  exit 0
fi

case "$MODE" in
  default)
    switch_stock
    ;;
  glass-fresh|fresh)
    switch_aqua "$FRESH_APK" "Glass TV - fresh (liquid glass, no widgets)"
    ;;
  glass-widgets|widgets)
    switch_aqua "$WIDGETS_APK" "Glass TV - widgets (digital clock, Prayer, Weather, AQI & Adzan)"
    ;;
esac

printf '\n'
ok "Switched to ${C_BOLD}${MODE}${C_RESET}. Press HOME on the remote to see it."
printf '\n'
