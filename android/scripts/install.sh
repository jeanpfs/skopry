#!/bin/bash
# Build the release app, install it on the connected device (USB or `adb-tailscale.sh connect`) and have ART
# compile it at once: an installed app runs interpreted until then, and its first rail or deck dropped frames.
#
#   scripts/install.sh            build, install, compile, launch
#   scripts/install.sh --debug    the debug build instead (debuggable, for `adb logcat` and profilers)
#
# With more than one device, ANDROID_SERIAL picks one (for the tailnet one: 100.x.y.z:PORT).
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
pkg=app.skopry.android
cd "$here"

if [ "${1:-}" = "--debug" ]; then
    ./gradlew assembleDebug -q
    apk=app/build/outputs/apk/debug/app-debug.apk
else
    ./gradlew assembleRelease -q
    apk=app/build/outputs/apk/release/app-release.apk
fi

adb get-state >/dev/null 2>&1 || { echo "no device: plug it in, or run scripts/adb-tailscale.sh connect" >&2; exit 1; }
adb install -r "$apk"
adb shell cmd package compile -m speed -f "$pkg" >/dev/null
adb shell am start -n "$pkg/.MainActivity" >/dev/null
echo "installed and compiled: $(adb get-serialno)"
