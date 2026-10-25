#!/usr/bin/env bash
# Android TV Backup Tool
#
# Creates a comprehensive snapshot of an Android TV / Google TV device before
# applying any launcher or debloat modifications.
#
# Saves:
#   - Device metadata & hardware properties (props, resolution, density)
#   - Active Home launcher and all candidate Home activities
#   - Full package states (all, enabled, disabled, system, third-party)
#   - Stock launcher APK(s) pulled directly from the TV
#   - System, secure, and global settings dumps
#   - An automated, self-contained restore.sh inside the backup folder
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
ENV_FILE="${ENV_FILE:-$SCRIPT_DIR/.env}"
BACKUPS_DIR="${BACKUPS_DIR:-$SCRIPT_DIR/backups}"
DRY_RUN="${DRY_RUN:-0}"
TARGET_DEVICE=""

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
${C_BOLD}Android TV Backup Tool${C_RESET} v$VERSION

Creates a full backup of your Android TV / Google TV state, package configurations,
and stock launcher APK before applying any custom launchers or debloat tweaks.

Usage:
  ./backup.sh [device_ip] [options]

Options:
  --device <ip:port>      TV IP address and optional port (overrides .env)
  --output <dir>          Custom output directory (default: backups/)
  --dry-run               Print actions without connecting or modifying files
  -h, --help              Show this help

Examples:
  ./backup.sh                     # Back up TV specified in .env
  ./backup.sh 192.168.1.50        # Back up a specific TV by IP
  ./backup.sh --dry-run           # Preview backup steps
EOF
}

load_env() {
  if [[ -f "$ENV_FILE" ]]; then
    set -a
    # shellcheck disable=SC1090
    . "$ENV_FILE"
    set +a
  fi
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

validate_config() {
  if [[ -n "$TARGET_DEVICE" ]]; then
    TV_HOST="$TARGET_DEVICE"
  fi
  TV_HOST="${TV_HOST:-}"
  TV_PORT="${TV_PORT:-5555}"
  CONNECT_TIMEOUT="${CONNECT_TIMEOUT:-20}"

  [[ -n "$TV_HOST" ]] || die "TV address is required. Pass it as argument (./backup.sh 192.168.1.50) or set TV_HOST in $ENV_FILE."

  if [[ "$TV_HOST" == *:* ]]; then
    SERIAL="$TV_HOST"
  else
    SERIAL="$TV_HOST:$TV_PORT"
  fi
  SERIAL="${TV_SERIAL:-$SERIAL}"
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

  die "Could not connect to $SERIAL after ${CONNECT_TIMEOUT}s. Check IP address and ensure Network/ADB debugging is enabled on TV."
}

sanitize_name() {
  printf '%s' "$1" | tr '[:space:]/\\:;*?"<>|' '_' | tr -s '_' | sed 's/^_//;s/_$//'
}

resolve_current_home() {
  local home
  home="$(run_adb shell 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME' 2>/dev/null | tr -d '\r' | tail -n 1 || true)"
  if [[ -z "$home" || "$home" == *"No activity found"* ]]; then
    home="$(run_adb shell 'pm resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME' 2>/dev/null | tr -d '\r' | grep -E '^[a-zA-Z0-9._]+/[a-zA-Z0-9._]+' | head -n 1 || true)"
  fi
  printf '%s\n' "$home"
}

detect_stock_launcher() {
  local current_home="$1"
  local installed_pkgs="$2"
  local detected_pkg=""
  local detected_act=""

  # 1. If currently active home is NOT Aqua TV Home (com.gathin.tvhome), it's the current stock launcher!
  if [[ -n "$current_home" && "$current_home" != com.gathin.tvhome/* && "$current_home" == *"/"* ]]; then
    detected_act="$current_home"
    detected_pkg="${current_home%%/*}"
  fi

  # 2. Known TV launcher packages in priority order
  if [[ -z "$detected_pkg" ]]; then
    if printf '%s\n' "$installed_pkgs" | grep -qx "package:com.google.android.apps.tv.launcherx"; then
      detected_pkg="com.google.android.apps.tv.launcherx"
      detected_act="com.google.android.apps.tv.launcherx/.home.HomeActivity"
    elif printf '%s\n' "$installed_pkgs" | grep -qx "package:com.google.android.tvlauncher"; then
      detected_pkg="com.google.android.tvlauncher"
      detected_act="com.google.android.tvlauncher/.MainActivity"
    elif printf '%s\n' "$installed_pkgs" | grep -qx "package:com.mitv.tvhome"; then
      detected_pkg="com.mitv.tvhome"
      detected_act="com.mitv.tvhome/.MainActivity"
    elif printf '%s\n' "$installed_pkgs" | grep -qx "package:com.xiaomi.mitv.tvhome"; then
      detected_pkg="com.xiaomi.mitv.tvhome"
      detected_act="com.xiaomi.mitv.tvhome/.MainActivity"
    elif printf '%s\n' "$installed_pkgs" | grep -qx "package:com.amazon.tv.launcher"; then
      detected_pkg="com.amazon.tv.launcher"
      detected_act="com.amazon.tv.launcher/.ui.HomeActivity_v3"
    fi
  fi

  # 3. Query all registered HOME category activities if still not found
  if [[ -z "$detected_pkg" ]]; then
    local candidates candidate
    candidates="$(run_adb shell 'cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME' 2>/dev/null | tr -d '\r' || true)"
    if [[ -z "$candidates" ]]; then
      candidates="$(run_adb shell 'pm query-activities -a android.intent.action.MAIN -c android.intent.category.HOME' 2>/dev/null | tr -d '\r' || true)"
    fi
    candidate="$(printf '%s\n' "$candidates" | grep -E '[a-zA-Z0-9._]+/[a-zA-Z0-9._]+' | grep -v 'com.gathin.tvhome' | head -n 1 | awk '{print $NF}' || true)"
    if [[ -n "$candidate" ]]; then
      detected_act="$candidate"
      detected_pkg="${candidate%%/*}"
    fi
  fi

  # 4. Fallback to .env configuration or standard Leanback launcher
  if [[ -z "$detected_pkg" ]]; then
    detected_pkg="${STOCK_PACKAGE:-com.google.android.tvlauncher}"
    detected_act="${STOCK_ACTIVITY:-$detected_pkg/.MainActivity}"
  fi

  STOCK_PACKAGE_DETECTED="$detected_pkg"
  STOCK_ACTIVITY_DETECTED="$detected_act"
}

pull_stock_apk() {
  local pkg="$1"
  local dest_dir="$2"
  local paths_raw paths_arr count=0 path_line

  info "Looking for APK file(s) for $pkg..."
  if [[ "$DRY_RUN" == "1" ]]; then
    printf '  [dry-run] adb shell pm path %s\n' "$pkg"
    printf '  [dry-run] adb pull <remote_path> %s/stock_launcher.apk\n' "$dest_dir"
    return 0
  fi

  paths_raw="$(run_adb shell "pm path $pkg" 2>/dev/null | tr -d '\r' | sed 's/^package://' || true)"
  if [[ -z "$paths_raw" ]]; then
    warn "Could not obtain APK path for $pkg (app may be protected or not installed)."
    return 0
  fi

  # Count lines
  count="$(printf '%s\n' "$paths_raw" | grep -c . || true)"
  if (( count == 1 )); then
    local remote_apk
    remote_apk="$(printf '%s\n' "$paths_raw" | head -n 1)"
    info "Pulling $remote_apk..."
    if "$ADB_BIN" -s "$SERIAL" pull "$remote_apk" "$dest_dir/stock_launcher.apk" >/dev/null 2>&1; then
      ok "Stock launcher APK saved to $dest_dir/stock_launcher.apk"
    else
      warn "Failed to pull $remote_apk"
    fi
  elif (( count > 1 )); then
    info "App consists of $count split APKs. Pulling all to stock_launcher_apks/..."
    mkdir -p "$dest_dir/stock_launcher_apks"
    while IFS= read -r path_line; do
      [[ -z "$path_line" ]] && continue
      "$ADB_BIN" -s "$SERIAL" pull "$path_line" "$dest_dir/stock_launcher_apks/" >/dev/null 2>&1 || true
    done <<< "$paths_raw"
    ok "Split APKs saved to $dest_dir/stock_launcher_apks/"
  fi
}

generate_restore_script() {
  local dest="$1"
  local script_path="$dest/restore.sh"

  cat > "$script_path" <<'RESTORE_EOF'
#!/usr/bin/env bash
# Auto-generated Disaster Recovery Script for Android TV
# Created by backup.sh
set -euo pipefail

SCRIPT_DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DRY_RUN=0
TARGET_DEVICE=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run) DRY_RUN=1 ;;
    -h|--help)
      echo "Usage: ./restore.sh [device_ip:port] [--dry-run]"
      echo "Restores this Android TV backup to its original stock state."
      exit 0
      ;;
    *)
      if [[ -z "$TARGET_DEVICE" ]]; then
        TARGET_DEVICE="$1"
      fi
      ;;
  esac
  shift
done

# Read backup metadata
if [[ -f "$SCRIPT_DIR/backup_info.env" ]]; then
  # shellcheck disable=SC1091
  . "$SCRIPT_DIR/backup_info.env"
fi

DEVICE_SERIAL="${TARGET_DEVICE:-${BACKUP_DEVICE_SERIAL:-}}"
if [[ -z "$DEVICE_SERIAL" ]]; then
  echo "[x] Device address not provided. Run: ./restore.sh <device_ip>" >&2
  exit 1
fi

find_adb() {
  if [[ -n "${ADB_PATH:-}" && -x "$ADB_PATH" ]]; then
    printf '%s\n' "$ADB_PATH"
    return 0
  fi
  if command -v adb >/dev/null 2>&1; then
    command -v adb
    return 0
  fi
  for c in \
    "${ANDROID_HOME:-}/platform-tools/adb" \
    "${ANDROID_SDK_ROOT:-}/platform-tools/adb" \
    "$HOME/Library/Android/sdk/platform-tools/adb" \
    "$HOME/Android/Sdk/platform-tools/adb" \
    "/opt/homebrew/bin/adb" \
    "/usr/local/bin/adb"; do
    if [[ -n "$c" && -x "$c" ]]; then
      printf '%s\n' "$c"
      return 0
    fi
  done
  if [[ "$DRY_RUN" == "1" ]]; then
    printf '%s\n' "adb"
    return 0
  fi
  echo "[x] adb not found on system. Install Android platform-tools or set ADB_PATH." >&2
  exit 1
}

ADB="$(find_adb)"

run_adb() {
  if [[ "$DRY_RUN" == "1" ]]; then
    echo "  [dry-run] adb -s $DEVICE_SERIAL $*"
    return 0
  fi
  "$ADB" -s "$DEVICE_SERIAL" "$@"
}

echo "==> Restoring Android TV to Stock Configuration"
echo "    Device: $DEVICE_SERIAL"
echo "    Target Stock Package:  ${BACKUP_STOCK_PACKAGE:-unknown}"
echo "    Target Stock Activity: ${BACKUP_STOCK_ACTIVITY:-unknown}"

if [[ "$DRY_RUN" == "0" ]]; then
  "$ADB" connect "$DEVICE_SERIAL" >/dev/null 2>&1 || true
fi

# 1. Re-enable stock launcher package
if [[ -n "${BACKUP_STOCK_PACKAGE:-}" ]]; then
  echo "==> Enabling stock launcher: $BACKUP_STOCK_PACKAGE"
  run_adb shell "pm enable --user 0 $BACKUP_STOCK_PACKAGE" || true
fi

# 2. Re-enable any packages that were recorded as enabled at backup time
if [[ -f "$SCRIPT_DIR/packages_enabled.txt" ]]; then
  echo "==> Verifying core packages are enabled..."
  while IFS= read -r pkg_line; do
    pkg_name="${pkg_line#package:}"
    [[ -z "$pkg_name" ]] && continue
    # Re-enable if currently disabled
    if [[ "$DRY_RUN" == "1" ]]; then
      run_adb shell "pm enable --user 0 $pkg_name"
    else
      run_adb shell "pm enable --user 0 $pkg_name" >/dev/null 2>&1 || true
    fi
  done < "$SCRIPT_DIR/packages_enabled.txt"
fi

# 3. If stock launcher APK exists and package is missing, reinstall it
if [[ -f "$SCRIPT_DIR/stock_launcher.apk" && "$DRY_RUN" == "0" ]]; then
  installed="$(run_adb shell pm list packages 2>/dev/null || true)"
  if ! echo "$installed" | grep -qx "package:${BACKUP_STOCK_PACKAGE:-}"; then
    echo "==> Reinstalling original stock launcher APK..."
    run_adb install -r -d -g "$SCRIPT_DIR/stock_launcher.apk"
  fi
fi

# 4. Set Home activity back to stock
if [[ -n "${BACKUP_STOCK_ACTIVITY:-}" ]]; then
  echo "==> Setting default Home to $BACKUP_STOCK_ACTIVITY"
  run_adb shell "cmd package set-home-activity --user 0 $BACKUP_STOCK_ACTIVITY" || \
  run_adb shell "pm set-home-activity $BACKUP_STOCK_ACTIVITY" || true

  echo "==> Starting Home activity"
  run_adb shell "am start -W -a android.intent.action.MAIN -c android.intent.category.HOME" || true
fi

echo "[ok] Restoration commands executed. Press HOME on your TV remote."
RESTORE_EOF

  chmod +x "$script_path"
}

perform_backup() {
  local timestamp brand model device sdk release fingerprint wm_size wm_density
  local current_home installed_pkgs backup_folder folder_name

  timestamp="$(date '+%Y%m%d_%H%M%S')"

  if [[ "$DRY_RUN" == "1" ]]; then
    brand="DryRunBrand"
    model="DryRunModel"
    device="DryRunDevice"
    sdk="30"
    release="11"
    fingerprint="dryrun_fingerprint"
    wm_size="1920x1080"
    wm_density="320"
    current_home="com.google.android.apps.tv.launcherx/.home.HomeActivity"
    installed_pkgs="package:com.google.android.apps.tv.launcherx"
  else
    info "Inspecting device properties..."
    brand="$(run_adb shell 'getprop ro.product.brand' 2>/dev/null | tr -d '\r' | head -n 1 || echo 'unknown')"
    model="$(run_adb shell 'getprop ro.product.model' 2>/dev/null | tr -d '\r' | head -n 1 || echo 'AndroidTV')"
    device="$(run_adb shell 'getprop ro.product.device' 2>/dev/null | tr -d '\r' | head -n 1 || echo 'device')"
    sdk="$(run_adb shell 'getprop ro.build.version.sdk' 2>/dev/null | tr -d '\r' | head -n 1 || echo 'unknown')"
    release="$(run_adb shell 'getprop ro.build.version.release' 2>/dev/null | tr -d '\r' | head -n 1 || echo 'unknown')"
    fingerprint="$(run_adb shell 'getprop ro.build.fingerprint' 2>/dev/null | tr -d '\r' | head -n 1 || echo 'unknown')"
    wm_size="$(run_adb shell 'wm size' 2>/dev/null | tr -d '\r' | head -n 1 || echo 'unknown')"
    wm_density="$(run_adb shell 'wm density' 2>/dev/null | tr -d '\r' | head -n 1 || echo 'unknown')"

    current_home="$(resolve_current_home)"
    installed_pkgs="$(run_adb shell 'pm list packages -u' 2>/dev/null | tr -d '\r' || true)"
  fi

  detect_stock_launcher "$current_home" "$installed_pkgs"

  folder_name="$(sanitize_name "${brand}_${model}")_${timestamp}"
  backup_folder="$BACKUPS_DIR/$folder_name"

  info "Creating backup folder: ${C_BOLD}$backup_folder${C_RESET}"
  if [[ "$DRY_RUN" == "0" ]]; then
    mkdir -p "$backup_folder"
  fi

  printf '\n'
  printf '  %-20s %s\n' "Target Device:" "$SERIAL"
  printf '  %-20s %s %s (Android %s, SDK %s)\n' "Hardware Model:" "$brand" "$model" "$release" "$sdk"
  printf '  %-20s %s\n' "Current Home:" "${current_home:-unknown}"
  printf '  %-20s %s (%s)\n' "Stock Launcher:" "$STOCK_PACKAGE_DETECTED" "$STOCK_ACTIVITY_DETECTED"
  printf '  %-20s %s\n' "Display:" "$wm_size, $wm_density"
  printf '\n'

  if [[ "$DRY_RUN" == "1" ]]; then
    ok "[dry-run] Would dump system properties, packages, settings, pull stock APK, and generate restore.sh"
    return 0
  fi

  # 1. Save device info & props
  info "1/5 Saving device information and build properties..."
  cat > "$backup_folder/device_info.txt" <<INFO_EOF
Device Serial:       $SERIAL
Brand:               $brand
Model:               $model
Device Codename:     $device
Android OS Release:  $release
Android SDK API:     $sdk
Build Fingerprint:   $fingerprint
Display Size:        $wm_size
Display Density:     $wm_density
Current Home:        $current_home
Detected Stock Home: $STOCK_PACKAGE_DETECTED ($STOCK_ACTIVITY_DETECTED)
Backup Date:         $(date)
INFO_EOF

  run_adb shell 'getprop' > "$backup_folder/props_all.txt" 2>&1 || true

  # 2. Save environment variables for restore
  cat > "$backup_folder/backup_info.env" <<ENV_EOF
BACKUP_DEVICE_SERIAL="$SERIAL"
BACKUP_BRAND="$brand"
BACKUP_MODEL="$model"
BACKUP_SDK="$sdk"
BACKUP_RELEASE="$release"
BACKUP_CURRENT_HOME="$current_home"
BACKUP_STOCK_PACKAGE="$STOCK_PACKAGE_DETECTED"
BACKUP_STOCK_ACTIVITY="$STOCK_ACTIVITY_DETECTED"
BACKUP_DATE="$timestamp"
ENV_EOF

  # 3. Dump package lists and TV input states
  info "2/5 Dumping package states and TV inputs..."
  run_adb shell 'pm list packages -u' | tr -d '\r' | sort > "$backup_folder/packages_all.txt" 2>&1 || true
  run_adb shell 'pm list packages -e' | tr -d '\r' | sort > "$backup_folder/packages_enabled.txt" 2>&1 || true
  run_adb shell 'pm list packages -d' | tr -d '\r' | sort > "$backup_folder/packages_disabled.txt" 2>&1 || true
  run_adb shell 'pm list packages -s' | tr -d '\r' | sort > "$backup_folder/packages_system.txt" 2>&1 || true
  run_adb shell 'pm list packages -3' | tr -d '\r' | sort > "$backup_folder/packages_thirdparty.txt" 2>&1 || true

  # Query all HOME activities & claimants
  run_adb shell 'cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME' \
    | tr -d '\r' > "$backup_folder/home_activities.txt" 2>&1 || true

  # Dump TV input services (HDMI ports, Tuner, AV)
  run_adb shell 'dumpsys tv_input' | tr -d '\r' > "$backup_folder/tv_inputs.txt" 2>&1 || true

  # Dump stock launcher package details
  if [[ -n "$STOCK_PACKAGE_DETECTED" ]]; then
    run_adb shell "dumpsys package $STOCK_PACKAGE_DETECTED" | tr -d '\r' > "$backup_folder/stock_package_dump.txt" 2>&1 || true
  fi

  # 4. Dump system settings
  info "3/5 Dumping system settings..."
  run_adb shell 'settings list system' | tr -d '\r' | sort > "$backup_folder/settings_system.txt" 2>&1 || true
  run_adb shell 'settings list secure' | tr -d '\r' | sort > "$backup_folder/settings_secure.txt" 2>&1 || true
  run_adb shell 'settings list global' | tr -d '\r' | sort > "$backup_folder/settings_global.txt" 2>&1 || true

  # 5. Pull stock launcher APK
  info "4/5 Backing up stock launcher APK..."
  pull_stock_apk "$STOCK_PACKAGE_DETECTED" "$backup_folder"

  # 6. Generate standalone restore.sh
  info "5/5 Generating self-contained restore.sh..."
  generate_restore_script "$backup_folder"

  # Also update/create a symlink or marker 'latest' in backups/
  ln -sfn "$folder_name" "$BACKUPS_DIR/latest_${sanitize_name "${brand}_${model}"}" 2>/dev/null || true
  ln -sfn "$folder_name" "$BACKUPS_DIR/latest" 2>/dev/null || true

  printf '\n'
  ok "${C_BOLD}Backup successfully completed!${C_RESET}"
  printf '  Folder:  %s\n' "$backup_folder"
  printf '  Restore: %s/restore.sh\n\n' "$backup_folder"
}

# ---------------------------------------------------------------- arguments

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help|help)
      usage
      exit 0
      ;;
    --dry-run)
      DRY_RUN=1
      ;;
    --device)
      shift
      [[ $# -gt 0 ]] || die "--device requires an IP or IP:PORT argument."
      TARGET_DEVICE="$1"
      ;;
    --output)
      shift
      [[ $# -gt 0 ]] || die "--output requires a directory path."
      BACKUPS_DIR="$1"
      ;;
    -*)
      die "Unknown option: $1. Run ./backup.sh --help."
      ;;
    *)
      if [[ -z "$TARGET_DEVICE" ]]; then
        TARGET_DEVICE="$1"
      else
        die "Unexpected argument: $1"
      fi
      ;;
  esac
  shift
done

load_env
validate_config

if [[ "$DRY_RUN" == "1" ]]; then
  ADB_BIN="${ADB_PATH:-adb}"
  printf '%s\n' "${C_DIM}Dry run: no commands will be sent to the TV.${C_RESET}"
else
  ADB_BIN="$(find_adb)"
fi

connect_tv
perform_backup
