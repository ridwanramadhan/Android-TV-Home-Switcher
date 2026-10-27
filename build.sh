#!/usr/bin/env bash
# Glass TV / Android TV Home Launcher Builder
#
# Builds Glass TV APKs from the local Java/XML source code in launcher/.
# Attribution: https://github.com/cocombee/glass-tv
#
# Modes:
#   glass, glass-fresh, fresh    - Glass TV clean Apple TV-style grid, liquid glass, no widgets
#   glass-widgets, widgets       - Glass TV with beautiful digital clock & 3 Liquid Glass widgets (Prayer, Weather, AQI) + Adzan
#   all                          - Compiles both variants and places them in apks/
#
set -euo pipefail

SOURCE="${BASH_SOURCE[0]}"
while [[ -h "$SOURCE" ]]; do
  SOURCE_DIR="$(cd -P "$(dirname "$SOURCE")" && pwd)"
  SOURCE="$(readlink "$SOURCE")"
  [[ "$SOURCE" != /* ]] && SOURCE="$SOURCE_DIR/$SOURCE"
done
SCRIPT_DIR="$(cd -P "$(dirname "$SOURCE")" && pwd)"
LAUNCHER_DIR="$SCRIPT_DIR/launcher"
APKS_DIR="$SCRIPT_DIR/apks"
CONFIG_XML="$LAUNCHER_DIR/app/src/main/res/values/config.xml"

info() { printf '\033[34m==>\033[0m %s\n' "$*"; }
ok()   { printf '\033[32m[ok]\033[0m %s\n' "$*"; }
warn() { printf '\033[33m[!]\033[0m %s\n' "$*" >&2; }
die()  { printf '\033[31m[x]\033[0m %s\n' "$*" >&2; exit 1; }

usage() {
  cat <<EOF
Glass TV Home Launcher Builder
Attribution: https://github.com/cocombee/glass-tv

Usage:
  ./build.sh <mode>

Modes:
  glass, glass-fresh, fresh    Build clean Glass TV launcher (liquid glass, no widgets)
  glass-widgets, widgets       Build Glass TV with digital clock, 3 Liquid Glass widgets & Adzan
  all                          Build both fresh and widgets APKs

Examples:
  ./build.sh glass
  ./build.sh glass-widgets
  ./build.sh all
EOF
}

find_java() {
  if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
    printf '%s\n' "$JAVA_HOME"
    return 0
  fi
  for cand in \
    "/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
    "/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home" \
    "/Library/Java/JavaVirtualMachines/jdk-20.jdk/Contents/Home" \
    "/opt/homebrew/opt/openjdk@17" \
    "/usr/local/opt/openjdk@17"; do
    if [[ -d "$cand" && -x "$cand/bin/java" ]]; then
      printf '%s\n' "$cand"
      return 0
    fi
  done
  if command -v /usr/libexec/java_home >/dev/null 2>&1; then
    local jh
    jh="$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home 2>/dev/null || true)"
    if [[ -n "$jh" && -x "$jh/bin/java" ]]; then
      printf '%s\n' "$jh"
      return 0
    fi
  fi
  die "Java 17+ not found. Install openjdk@17 or Android Studio."
}

find_sdk() {
  if [[ -n "${ANDROID_HOME:-}" && -d "$ANDROID_HOME" ]]; then
    printf '%s\n' "$ANDROID_HOME"
    return 0
  fi
  for cand in \
    "$HOME/Library/Android/sdk" \
    "$HOME/Android/Sdk" \
    "/opt/homebrew/share/android-commandlinetools"; do
    if [[ -d "$cand" ]]; then
      printf '%s\n' "$cand"
      return 0
    fi
  done
  die "Android SDK not found. Set ANDROID_HOME or install Android Studio platform-tools."
}

find_gradle() {
  # The wrapper pins the exact Gradle version and verifies its checksum, so it wins
  # over any system or cached Gradle installation.
  local wrapper="$LAUNCHER_DIR/gradlew"
  if [[ -x "$wrapper" ]]; then
    printf '%s\n' "$wrapper"
    return 0
  fi
  if command -v gradle >/dev/null 2>&1; then
    warn "Gradle wrapper missing at $wrapper; falling back to system gradle."
    command -v gradle
    return 0
  fi
  die "Gradle not found. Restore launcher/gradlew or install Gradle 8.x."
}

# Rewrite dashboard_panels with a temp file: 'sed -i' is BSD-only and breaks on Linux.
set_dashboard_panels() {
  local enable="$1" tmp replacement pattern
  tmp="$(mktemp "${TMPDIR:-/tmp}/config.xml.XXXXXX")"
  replacement="<bool name=\"dashboard_panels\">$enable</bool>"
  pattern="s|<bool name=\"dashboard_panels\">.*</bool>|$replacement|"
  if ! sed "$pattern" "$CONFIG_XML" > "$tmp"; then
    rm -f "$tmp"
    die "Failed to update dashboard_panels in $CONFIG_XML."
  fi
  mv "$tmp" "$CONFIG_XML"
}

# build_variant flips config.xml; always put the original back, even on failure or after 'all'.
CONFIG_BACKUP=""
restore_config() {
  if [[ -n "$CONFIG_BACKUP" && -f "$CONFIG_BACKUP" ]]; then
    if ! cp -p "$CONFIG_BACKUP" "$CONFIG_XML"; then
      warn "Could not restore $CONFIG_XML from $CONFIG_BACKUP."
    fi
    rm -f "$CONFIG_BACKUP"
    CONFIG_BACKUP=""
  fi
}

build_variant() {
  local mode="$1"
  local target_apk="$2"
  local alt_apk="${3:-}"
  local enable_dash="$4"

  info "Configuring launcher for: $mode (dashboard_panels=$enable_dash)"
  set_dashboard_panels "$enable_dash"

  info "Compiling $mode release APK..."
  (
    cd "$LAUNCHER_DIR"
    export JAVA_HOME="$DETECTED_JAVA"
    export ANDROID_HOME="$DETECTED_SDK"
    export PATH="$JAVA_HOME/bin:$PATH"
    echo "sdk.dir=$ANDROID_HOME" > local.properties
    GRADLE_BIN="$(find_gradle)"
    "$GRADLE_BIN" assembleRelease --no-daemon -q --console=plain
  )

  local built_apk="$LAUNCHER_DIR/app/build/outputs/apk/release/app-release.apk"
  [[ -f "$built_apk" ]] || die "Build failed. $built_apk was not generated."

  mkdir -p "$APKS_DIR"
  cp "$built_apk" "$target_apk"
  if [[ -n "$alt_apk" ]]; then
    cp "$built_apk" "$alt_apk"
  fi
  ok "$mode APK built: $(basename "$target_apk") ($(du -h "$target_apk" | awk '{print $1}'))"
}

write_checksums() {
  local -a checksum_tool
  if command -v shasum >/dev/null 2>&1; then
    checksum_tool=(shasum -a 256)
  elif command -v sha256sum >/dev/null 2>&1; then
    checksum_tool=(sha256sum)
  else
    die "Neither shasum nor sha256sum is available to write APK checksums."
  fi

  (
    cd "$APKS_DIR"
    "${checksum_tool[@]}" ./*.apk > SHA256SUMS
  )
  ok "APK checksums written to apks/SHA256SUMS"
}

MODE="${1:-}"
[[ -n "$MODE" ]] || { usage; exit 2; }

case "$MODE" in
  -h|--help|help)
    usage
    exit 0
    ;;
esac

DETECTED_JAVA="$(find_java)"
DETECTED_SDK="$(find_sdk)"

# Snapshot config.xml before any variant flips it, and restore it via the EXIT trap.
CONFIG_BACKUP="$(mktemp "${TMPDIR:-/tmp}/config.xml.orig.XXXXXX")"
cp -p "$CONFIG_XML" "$CONFIG_BACKUP"
trap restore_config EXIT
trap 'exit 130' INT TERM

case "$MODE" in
  glass|glass-fresh|fresh)
    build_variant "glass-fresh" "$APKS_DIR/glass-tv-fresh.apk" "$APKS_DIR/aqua-tv-home-fresh.apk" "false"
    ;;
  glass-widgets|widgets|dashboard)
    build_variant "glass-widgets" "$APKS_DIR/glass-tv-widgets.apk" "$APKS_DIR/aqua-tv-home-widgets.apk" "true"
    ;;
  all)
    build_variant "glass-fresh" "$APKS_DIR/glass-tv-fresh.apk" "$APKS_DIR/aqua-tv-home-fresh.apk" "false"
    build_variant "glass-widgets" "$APKS_DIR/glass-tv-widgets.apk" "$APKS_DIR/aqua-tv-home-widgets.apk" "true"
    ;;
  *)
    die "Unknown build mode '$MODE'. Available: glass, glass-widgets, fresh, widgets, all."
    ;;
esac

write_checksums
