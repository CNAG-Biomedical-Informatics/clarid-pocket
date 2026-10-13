#!/usr/bin/env bash
set -euo pipefail
ios_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
swift test --package-path "$ios_root"
xcodebuild -project "$ios_root/ClarIDPocket.xcodeproj" -scheme ClarIDPocket \
    -destination 'generic/platform=iOS Simulator' \
    -derivedDataPath "$ios_root/DerivedData" CODE_SIGNING_ALLOWED=NO build
