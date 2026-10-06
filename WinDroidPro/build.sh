#!/usr/bin/env bash

set -euo pipefail

echo "=========================================="
echo "  WinDroid Pro - Build Script"
echo "=========================================="

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

success() { echo -e "${GREEN}✓ $1${NC}"; }
error() { echo -e "${RED}✗ $1${NC}" >&2; }
info() { echo -e "${YELLOW}ℹ $1${NC}"; }

if [[ ! -f "settings.gradle" ]]; then
    error "Run this script from the WinDroidPro directory."
    exit 1
fi

if ! command -v java >/dev/null 2>&1; then
    error "Java is not installed. Install JDK 17."
    exit 1
fi
success "Java found: $(java -version 2>&1 | head -n 1)"

if [[ -z "${ANDROID_HOME:-}" && -z "${ANDROID_SDK_ROOT:-}" ]]; then
    error "Android SDK not found. Set ANDROID_HOME or ANDROID_SDK_ROOT."
    exit 1
fi
success "Android SDK found"

if [[ -x "./gradlew" ]]; then
    GRADLE=("./gradlew")
elif command -v gradle >/dev/null 2>&1; then
    GRADLE=("gradle")
    info "Gradle wrapper is not committed; using installed Gradle: $(gradle --version | awk '/Gradle / {print $2; exit}')"
else
    error "No Gradle wrapper is present and 'gradle' is not installed. Install Gradle 8.2.1 or generate/commit the wrapper."
    exit 1
fi

cat <<'EOF'

Select build type:
1) Debug
2) Release (requires real runtime assets and external signing credentials)
EOF
read -r -p "Enter choice [1-2]: " build_choice

case "$build_choice" in
    1)
        BUILD_TYPE="Debug"
        GRADLE_TASK="assembleDebug"
        APK_GLOB="app/build/outputs/apk/debug/*.apk"
        ;;
    2)
        BUILD_TYPE="Release"
        GRADLE_TASK="assembleRelease"
        APK_GLOB="app/build/outputs/apk/release/*.apk"
        ;;
    *)
        error "Invalid choice."
        exit 1
        ;;
esac

if [[ "$BUILD_TYPE" == "Release" ]]; then
    required_vars=(
        WINDROID_RELEASE_STORE_FILE
        WINDROID_RELEASE_STORE_PASSWORD
        WINDROID_RELEASE_KEY_ALIAS
        WINDROID_RELEASE_KEY_PASSWORD
    )
    missing=()
    for variable in "${required_vars[@]}"; do
        if [[ -z "${!variable:-}" ]]; then
            missing+=("$variable")
        fi
    done
    if (( ${#missing[@]} > 0 )); then
        error "Release signing is not configured. Missing: ${missing[*]}"
        exit 1
    fi
fi

info "Cleaning previous outputs..."
"${GRADLE[@]}" --no-daemon clean

info "Building $BUILD_TYPE..."
"${GRADLE[@]}" --no-daemon --stacktrace "$GRADLE_TASK"

shopt -s nullglob
artifacts=( $APK_GLOB )
shopt -u nullglob

if (( ${#artifacts[@]} == 0 )); then
    error "Build completed without producing an APK at $APK_GLOB"
    exit 1
fi

for apk in "${artifacts[@]}"; do
    success "APK: $apk ($(du -h "$apk" | cut -f1))"
done

if [[ "$BUILD_TYPE" == "Debug" ]] && command -v adb >/dev/null 2>&1; then
    read -r -p "Install the debug APK on a connected device? (y/N): " install_choice
    if [[ "$install_choice" =~ ^[Yy]$ ]]; then
        adb install -r "${artifacts[0]}"
        success "Installed ${artifacts[0]}"
    fi
fi

success "$BUILD_TYPE build completed."
