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
  local cached
  cached="$(find "$HOME/.gradle/wrapper/dists" -name gradle -type f -perm +111 2>/dev/null | grep "gradle-8" | sort -r | head -n 1 || true)"
  if [[ -n "$cached" && -x "$cached" ]]; then
    printf '%s\n' "$cached"
    return 0
  fi
  if command -v gradle >/dev/null 2>&1; then
    command -v gradle
    return 0
  fi
  printf '%s\n' "./gradlew"
}

build_variant() {
  local mode="$1"
  local target_apk="$2"
  local alt_apk="${3:-}"
  local enable_dash="$4"

  info "Configuring launcher for: $mode (dashboard_panels=$enable_dash)"
  sed -i '' "s|<bool name=\"dashboard_panels\">.*</bool>|<bool name=\"dashboard_panels\">$enable_dash</bool>|" "$CONFIG_XML"

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

MODE="${1:-}"
[[ -n "$MODE" ]] || { usage; exit 2; }

DETECTED_JAVA="$(find_java)"
DETECTED_SDK="$(find_sdk)"

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
  -h|--help|help)
    usage
    exit 0
    ;;
  *)
    die "Unknown build mode '$MODE'. Available: glass, glass-widgets, fresh, widgets, all."
    ;;
esac
