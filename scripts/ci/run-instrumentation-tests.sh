#!/bin/bash
# Run instrumentation tests with monitoring
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Load NordVPN credentials from .env file for tests
if [ -f "$PROJECT_DIR/.env" ]; then
    echo "Sourcing NordVPN credentials from .env file."
    source "$PROJECT_DIR/.env"
else
    echo "Warning: .env file not found. NordVPN related tests might fail."
fi

echo "========================================"
echo "Starting Instrumentation Tests"
echo "========================================"
echo "Timestamp: $(date)"

# Wait for emulator to be fully ready - verify package manager service is available
echo "Verifying emulator is fully ready..."
echo "Waiting for ADB device..."
adb wait-for-device || true

echo "Waiting for boot completion..."
for i in $(seq 1 300); do
  BOOT=$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
  if [ "$BOOT" = "1" ]; then
    echo "Boot completed."
    break
  fi
  if [ $i -eq 300 ]; then
    echo "⚠️  Timeout waiting for boot_completed" >&2
  fi
  sleep 1
done

echo "Verifying package manager service is available..."
for i in $(seq 1 60); do
  # Check if package manager service is available by trying to list packages
  if adb shell pm list packages >/dev/null 2>&1; then
    echo "✅ Package manager service is ready (attempt $i/60)"
    break
  fi
  if [ $i -eq 60 ]; then
    echo "⚠️  Package manager service not ready after 60 attempts, but continuing..."
  fi
  sleep 1
done

# Additional wait for system services to fully initialize
echo "Waiting for system services to stabilize (10s)..."
sleep 10

# Verify ADB connection is stable
echo "Verifying ADB connection stability..."
for i in $(seq 1 10); do
  if adb shell echo "ready" >/dev/null 2>&1; then
    echo "✅ ADB connection verified (attempt $i/10)"
    break
  fi
  if [ $i -eq 10 ]; then
    echo "⚠️  ADB connection verification failed after 10 attempts"
  fi
  sleep 1
done

echo "Emulator readiness checks complete."

# Build diagnostic client APKs if they don't exist (they should be cached, but verify)
echo "Checking diagnostic client APKs..."
if [ ! -f "diagnostic-client-uk/build/outputs/apk/debug/diagnostic-client-uk-debug.apk" ] || \
   [ ! -f "diagnostic-client-fr/build/outputs/apk/debug/diagnostic-client-fr-debug.apk" ] || \
   [ ! -f "diagnostic-client-direct/build/outputs/apk/debug/diagnostic-client-direct-debug.apk" ]; then
    echo "Building diagnostic client APKs..."
    ./gradlew :diagnostic-client-uk:assemble :diagnostic-client-fr:assemble :diagnostic-client-direct:assemble --stacktrace
else
    echo "✅ Diagnostic client APKs found (using cached builds)"
fi

# Build only the test APK (androidTest), not the main APK
# The main APK should already be built and cached from the build job
echo "Building test APK (androidTest) only..."
./gradlew assembleDebugAndroidTest --stacktrace

# Verify main APK exists (should be from cache)
if [ ! -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
    echo "⚠️  Main APK not found! Building it now..."
    ./gradlew assembleDebug --stacktrace
else
    echo "✅ Main APK found (using cached build)"
fi

# Run tests with monitoring
set +e
# Use connectedDebugAndroidTest - it will install both APKs and run tests
# Since we've already built everything, this should just install and run
./gradlew connectedDebugAndroidTest --info --stacktrace 2>&1 | tee instrumentation-test.log

# Capture exit code of the gradle command (first command in pipeline), not tee
TEST_EXIT=${PIPESTATUS[0]}
set -e

echo ""
echo "========================================"
echo "Instrumentation Tests completed"
echo "========================================"
echo "Timestamp: $(date)"
echo "Exit code: $TEST_EXIT"

# Show test summary
echo ""
echo "=== Test Summary ==="
grep -E "(BUILD SUCCESSFUL|BUILD FAILED|tests completed|test failed|INSTRUMENTATION_STATUS)" instrumentation-test.log | tail -50 || echo "No test summary found"

# If tests failed, show more details
if [ $TEST_EXIT -ne 0 ]; then
    echo ""
    echo "=== Failed Test Details ==="
    grep -E "FAILED|FAILURE|Exception|Error" instrumentation-test.log | grep -v "^\s*at " | tail -30 || echo "No detailed failure info found"
    echo ""
    echo "=== Test Report Location ==="
    echo "See detailed report at: app/build/reports/androidTests/connected/debug/index.html"
fi

exit $TEST_EXIT
