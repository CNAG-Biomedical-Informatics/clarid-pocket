#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"

# Use the native ARM64 AAPT2 instead of AGP's default x86-64 executable.
sdk_dir="${ANDROID_HOME:-$project_dir/../android-sdk-arm64}"
extra_args=()
if [[ "$(uname -s)" == Linux && "$(uname -m)" == aarch64 ]]; then
    aapt2="$sdk_dir/build-tools/37.0.0/aapt2"
    if [[ ! -x "$aapt2" ]]; then
        echo "ARM64 AAPT2 not found at $aapt2. Set ANDROID_HOME to the ARM64 SDK." >&2
        exit 1
    fi
    export ANDROID_HOME="$sdk_dir"
    extra_args+=("-Pandroid.aapt2FromMavenOverride=$aapt2")
fi

exec bash ./gradlew "${extra_args[@]}" "$@"
