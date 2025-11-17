#!/bin/bash
# Build debug APK with native libraries for E2E tests
set -e

echo "========================================"
echo "Building APK with OpenVPN native libraries for E2E tests"
echo "========================================"
echo "Note: Native builds are required for actual VPN connections in E2E tests"
echo "This may take 15-30 minutes on first build, but will be cached for subsequent runs"
echo "SKIP_NATIVE_BUILD: ${SKIP_NATIVE_BUILD:-not set (native builds ENABLED)}"
echo ""
./gradlew --stacktrace assembleDebug
