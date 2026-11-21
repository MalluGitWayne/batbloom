#!/usr/bin/env bash
# Publishes a Batbloom release to GitHub: builds the signed APK (tests run first) and attaches it to a release.
# The app's Settings > Updates then offers it on the phone.
#   ./release.sh "What changed in this version"
# Bump versionCode and versionName in app/build.gradle.kts first.
set -euo pipefail
cd "$(dirname "$0")"
VERSION=$(grep -oP 'versionName = "\K[^"]+' app/build.gradle.kts)
NOTES=${1:-"Batbloom $VERSION"}
./gradlew --no-daemon assembleRelease
cp app/build/outputs/apk/release/app-release.apk "Batbloom-$VERSION.apk"
gh release create "v$VERSION" "Batbloom-$VERSION.apk" --repo MalluGitWayne/batbloom --title "Batbloom $VERSION" --notes "$NOTES"
echo "Published v$VERSION"
