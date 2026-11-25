#!/usr/bin/env bash
# Publishes a Batbloom release to GitHub: unit tests and lint, UI tests (when an emulator is running), then the signed APK
# attached to a release. The app's Settings > Updates then offers it on the phone.
#   ./release.sh "What changed in this version"
#   SKIP_UI_TESTS=1 ./release.sh "..."   to skip the UI tests
# Bump versionCode and versionName in app/build.gradle.kts first.
set -euo pipefail
cd "$(dirname "$0")"
VERSION=$(grep -oP 'versionName = "\K[^"]+' app/build.gradle.kts)
NOTES=${1:-"Batbloom $VERSION"}

./gradlew --no-daemon testDebugUnitTest lintDebug

# UI tests reinstall the debug app, which wipes its data, so they only ever run on an emulator, never on a phone.
ADB="${ANDROID_HOME:-$LOCALAPPDATA/Android/Sdk}/platform-tools/adb"
if [ "${SKIP_UI_TESTS:-0}" != "1" ] && "$ADB" devices 2>/dev/null | grep -q '^emulator-'; then
  SERIAL=$("$ADB" devices | grep -o '^emulator-[0-9]*' | head -1)
  echo "Running UI tests on $SERIAL"
  "$ADB" -s "$SERIAL" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
  "$ADB" -s "$SERIAL" shell wm dismiss-keyguard >/dev/null 2>&1 || true
  ANDROID_SERIAL="$SERIAL" ./gradlew --no-daemon connectedDebugAndroidTest
else
  echo "No emulator running (or SKIP_UI_TESTS=1): skipping UI tests"
fi

./gradlew --no-daemon assembleRelease
cp app/build/outputs/apk/release/app-release.apk "Batbloom-$VERSION.apk"
gh release create "v$VERSION" "Batbloom-$VERSION.apk" --repo MalluGitWayne/batbloom --title "Batbloom $VERSION" --notes "$NOTES"
echo "Published v$VERSION"
