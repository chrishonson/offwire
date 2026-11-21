#!/usr/bin/env bash
# Builds the installable apps for a release tag into release-artifacts/<tag>/ (docs/RELEASING.md).
# Usage: scripts/release-artifacts.sh v0.2.0
# The tag is built in a temporary worktree, so the current checkout and its uncommitted work are left alone.
set -euo pipefail

TAG="${1:?usage: scripts/release-artifacts.sh <tag, for example v0.2.0>}"
REPO="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$REPO/release-artifacts/$TAG"
# Not under mktemp's /var/folders: SwiftPM sees /var and /private/var as different trees and rejects the onnxruntime
# xcframework's symlinks. build/ is ignored.
WORK="$REPO/build/release-worktree/offwire-$TAG"
rm -rf "$WORK"

git -C "$REPO" rev-parse --verify --quiet "refs/tags/$TAG" >/dev/null || { echo "no tag $TAG" >&2; exit 1; }
git -C "$REPO" worktree add --detach "$WORK" "refs/tags/$TAG"
trap 'git -C "$REPO" worktree remove --force "$WORK"' EXIT
# local.properties holds the Android SDK path and is not tracked.
cp "$REPO/local.properties" "$WORK/"

cd "$WORK"
./gradlew :app-classic:assembleDebug :app-console:assembleDebug --no-daemon -q
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -destination 'generic/platform=iOS' -derivedDataPath build/ios-device -allowProvisioningUpdates -quiet build

mkdir -p "$OUT"
cp app-classic/build/outputs/apk/debug/app-classic-debug.apk "$OUT/offwire-classic-$TAG.apk"
cp app-console/build/outputs/apk/debug/app-console-debug.apk "$OUT/offwire-console-$TAG.apk"
# ditto keeps the code signature intact inside the zip.
ditto -c -k --keepParent build/ios-device/Build/Products/Debug-iphoneos/Offwire.app "$OUT/Offwire-$TAG-ios.zip"
git -C "$REPO" rev-parse "refs/tags/$TAG^{commit}" > "$OUT/COMMIT"
(cd "$OUT" && shasum -a 256 *.apk *.zip > SHA256SUMS)
ls -lh "$OUT"
