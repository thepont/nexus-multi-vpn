#!/bin/bash
# Build release APK with native libraries
set -e

echo "========================================"
echo "Building RELEASE APK with OpenVPN native libraries"
echo "========================================"
echo "This APK includes native libraries for all supported ABIs:"
echo "  - arm64-v8a (64-bit ARM devices)"
echo "  - armeabi-v7a (32-bit ARM devices)"
echo "  - x86_64 (64-bit x86 emulators/devices)"
echo "  - x86 (32-bit x86 emulators/devices)"
echo ""
echo "Build may take 15-30 minutes on first build, but uses cached native libraries from E2E tests"
echo ""
./gradlew --stacktrace assembleRelease
