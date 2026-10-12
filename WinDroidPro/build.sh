#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
variant=Debug
if [[ "${1:-debug}" == release ]]; then variant=Release
elif [[ "${1:-debug}" != debug ]]; then
    echo "Usage: ./build.sh [debug|release]" >&2
    exit 2
fi
python3 -m unittest discover -s scripts -p 'test_*.py' -v
./gradlew ":app:assemble${variant}" :app:testDebugUnitTest :app:lintDebug --console=plain
apk=app/build/outputs/apk/debug/app-debug.apk
if [[ "$variant" == Release ]]; then apk=app/build/outputs/apk/release/app-release-unsigned.apk; fi
python3 scripts/verify_runtime.py "$apk"
echo "APK: $apk"
