#!/usr/bin/env bash
# Builds the iOS app for a device and installs it on the paired iPad, over the cable or Wi-Fi: devicectl reaches a
# paired iPad on the same network without a cable. IPAD_DEVICE picks another device by name or identifier.
set -euo pipefail

DEVICE="${IPAD_DEVICE:-BE5E49CB-27BF-5DC3-BF44-A5F7C652C5A8}"
BUNDLE_ID="app.offwire.classic"
REPO="$(cd "$(dirname "$0")/.." && pwd)"
DERIVED="$REPO/build/ios-device"

xcrun devicectl device info details --device "$DEVICE" | grep -E "transportType|tunnelState"
xcodebuild -project "$REPO/iosApp/iosApp.xcodeproj" -scheme iosApp -configuration Debug \
  -destination 'generic/platform=iOS' -derivedDataPath "$DERIVED" -allowProvisioningUpdates -quiet build
xcrun devicectl device install app --device "$DEVICE" "$DERIVED/Build/Products/Debug-iphoneos/Offwire.app"
xcrun devicectl device process launch --device "$DEVICE" "$BUNDLE_ID"
