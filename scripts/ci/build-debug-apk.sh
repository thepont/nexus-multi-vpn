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

# Run gradle build
./gradlew --stacktrace assembleDebug

# Verify APK was created
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$APK_PATH" ]; then
  echo "❌ ERROR: APK was not created at expected path: $APK_PATH" >&2
  exit 1
fi

# Show APK info
echo ""
echo "========================================"
echo "✅ Build successful!"
echo "========================================"
echo "APK location: $APK_PATH"
echo "APK size: $(du -h "$APK_PATH" | cut -f1)"
echo "========================================"
