#!/bin/bash
# Run instrumentation tests with monitoring
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Set ANDROID_HOME and add emulator to PATH for this script
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator

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

# Check if we're running in GitHub Actions (emulator already provided by android-emulator-runner)
# or if we need to start our own emulator (local development)
if [ -n "$CI" ] || [ -n "$GITHUB_ACTIONS" ]; then
    echo "🔍 Running in CI environment - emulator should already be provided"
    echo "Checking for existing emulator..."
    
    # Wait for emulator to be available (it's started by android-emulator-runner)
    echo "Waiting for ADB device to become available..."
    adb wait-for-device
    
    # Verify emulator is booted
    echo "Checking emulator boot status..."
    BOOT_COMPLETED=""
    TIMEOUT=120 # 2 minutes - emulator should already be booting
    ELAPSED=0
    
    while [[ "$BOOT_COMPLETED" != "1" && $ELAPSED -lt $TIMEOUT ]]; do
      BOOT_COMPLETED=$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
      if [ -n "$BOOT_COMPLETED" ]; then
        echo "Emulator boot status: $BOOT_COMPLETED (Elapsed: ${ELAPSED}s / ${TIMEOUT}s)"
      fi
      sleep 5
      ELAPSED=$((ELAPSED + 5))
    done
    
    if [ "$BOOT_COMPLETED" != "1" ]; then
      echo "⚠️  Warning: Emulator may not be fully booted, but continuing..."
      echo "   Boot status: $BOOT_COMPLETED"
    else
      echo "✅ Emulator is ready (provided by CI)"
    fi
    
    # List connected devices for debugging
    echo "Connected devices:"
    adb devices -l
    
else
    echo "🔍 Running locally - starting emulator..."
    # --- Local Emulator Management ---
    echo "Attempting to stop any running emulators..."
    adb emu kill || true
    sleep 5 # Give it some time to shut down
    
    echo "Starting 'test_device' emulator with wiped data..."
    nohup emulator -avd test_device -wipe-data -no-snapshot-load -no-window > /dev/null 2>&1 &
    EMULATOR_STARTED_PID=$!
    echo "Emulator started in background with PID: $EMULATOR_STARTED_PID"
    
    echo "Waiting for ADB device to become available..."
    adb wait-for-device
    
    echo "Waiting for emulator to boot completely..."
    # Wait for the boot animation to complete or for sys.boot_completed property to be set
    # Note: With -wipe-data, boot can take 8-10 minutes, so we use a longer timeout
    BOOT_COMPLETED=""
    TIMEOUT=600 # 10 minutes (increased from 5 minutes to handle -wipe-data)
    ELAPSED=0
    
    while [[ "$BOOT_COMPLETED" != "1" && $ELAPSED -lt $TIMEOUT ]]; do
      BOOT_COMPLETED=$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
      echo "Emulator boot status: $BOOT_COMPLETED (Elapsed: ${ELAPSED}s / ${TIMEOUT}s)"
      sleep 5
      ELAPSED=$((ELAPSED + 5))
    done
    
    if [ "$BOOT_COMPLETED" != "1" ]; then
      echo "❌ Error: Emulator did not boot completely within the allotted time."
      exit 1
    fi
    echo "✅ Emulator boot sequence complete."
    # --- End Local Emulator Management ---
    
    # Restart ADB server for a fresh connection (only needed locally)
    echo "Restarting ADB server..."
    adb kill-server
    adb start-server
    adb wait-for-device || true # Wait for device again after server restart
    echo "ADB server restarted and device reconnected."
fi

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

# Attempt to clean up existing app to avoid signature mismatch issues
# Note: DELETE_FAILED_INTERNAL_ERROR is often just "app not installed" - harmless
# The emulator is fresh each run (no state persistence), so this is just cleanup
echo "Attempting to clean up existing app (errors are expected if app not installed)..."
# Force stop the app first to ensure it's not running (silent - app may not exist)
adb shell am force-stop com.multiregionvpn 2>/dev/null || true
adb shell am force-stop com.multiregionvpn.test 2>/dev/null || true
sleep 1
# Try to uninstall (will fail with DELETE_FAILED_INTERNAL_ERROR if app doesn't exist - that's OK)
# Suppress error output since it's expected when app doesn't exist
adb uninstall com.multiregionvpn >/dev/null 2>&1 || true
adb uninstall com.multiregionvpn.test >/dev/null 2>&1 || true
sleep 1
echo "✅ Cleanup attempted (errors are harmless if app doesn't exist)"

# --- Start capturing logcat ---
echo "Starting adb logcat in background..."
adb logcat -c # Clear existing logcat buffer
adb logcat > instrumentation-test-logcat.log &
LOGCAT_PID=$!
echo "adb logcat started with PID: $LOGCAT_PID"
# --- End capturing logcat ---

# Final cleanup attempt before Gradle runs
# Since uninstall keeps failing, manually install with force flags before Gradle runs
# This ensures the APK is installed with correct signature before Gradle tries
echo "Final cleanup and manual installation before Gradle test run..."
# Force stop the app
adb shell am force-stop com.multiregionvpn 2>/dev/null || true
adb shell am force-stop com.multiregionvpn.test 2>/dev/null || true
sleep 1

# Manually install the APK with force flags to override signature mismatch
# Use -r (replace), -d (allow downgrade), -t (allow test APKs)
echo "Manually installing main APK with force flags..."
INSTALL_LOG="$PROJECT_DIR/apk-install.log"
INSTALL_SUCCESS=false

# Try multiple installation strategies
echo "Attempt 1: Install with -r -d -t flags..."
if timeout 120 adb install -r -d -t app/build/outputs/apk/debug/app-debug.apk > "$INSTALL_LOG" 2>&1; then
    echo "✅ Main APK installed successfully with -r -d -t"
    cat "$INSTALL_LOG"
    INSTALL_SUCCESS=true
else
    INSTALL_OUTPUT=$(cat "$INSTALL_LOG" 2>/dev/null || echo "")
    echo "Install attempt 1 output:"
    cat "$INSTALL_LOG" 2>/dev/null || echo "(no log file)"
    
    if echo "$INSTALL_OUTPUT" | grep -q "INSTALL_FAILED_UPDATE_INCOMPATIBLE"; then
        echo "⚠️  Signature mismatch detected, trying with -r only..."
        if timeout 120 adb install -r app/build/outputs/apk/debug/app-debug.apk > "$INSTALL_LOG" 2>&1; then
            echo "✅ Main APK installed with -r flag"
            INSTALL_SUCCESS=true
        else
            echo "⚠️  Install with -r also failed"
            cat "$INSTALL_LOG" 2>/dev/null || true
        fi
    elif echo "$INSTALL_OUTPUT" | grep -q "Success"; then
        # Sometimes adb install returns non-zero even on success
        echo "✅ Install appears successful (found 'Success' in output)"
        INSTALL_SUCCESS=true
    else
        echo "⚠️  Install failed with unknown error"
    fi
fi

# Final verification
if [ "$INSTALL_SUCCESS" = "true" ] || adb shell pm list packages | grep -q "package:com.multiregionvpn"; then
    echo "✅ App is confirmed installed"
    INSTALL_SUCCESS=true
else
    echo "❌ CRITICAL: App installation failed and app is not installed"
    echo "   This will likely cause Gradle to hang trying to install"
    echo "   Attempting emergency uninstall to clear state..."
    # Try one more aggressive cleanup
    adb shell pm uninstall --user 0 com.multiregionvpn 2>/dev/null || true
    adb shell pm uninstall --user 0 com.multiregionvpn.test 2>/dev/null || true
    sleep 2
    # Try install one more time
    echo "   Retrying installation..."
    if timeout 60 adb install -r -d -t app/build/outputs/apk/debug/app-debug.apk > "$INSTALL_LOG" 2>&1; then
        echo "✅ Emergency install succeeded"
        INSTALL_SUCCESS=true
    else
        echo "❌ Emergency install also failed - Gradle will likely hang"
        cat "$INSTALL_LOG" 2>/dev/null || true
    fi
fi

# Verify app is installed before Gradle runs
echo "Verifying app installation status..."
if adb shell pm list packages | grep -q "package:com.multiregionvpn"; then
    echo "✅ com.multiregionvpn is installed"
    adb shell pm list packages | grep "com.multiregionvpn"
else
    echo "⚠️  WARNING: com.multiregionvpn is NOT installed"
    echo "   Gradle will attempt to install it, but may fail due to signature mismatch"
fi

# Run tests with monitoring
set +e
# Use connectedDebugAndroidTest - it will install test APK and run tests
# Main APK should already be installed above
# Pass NordVPN credentials as instrumentation arguments
TEST_LOG_FILE="$PROJECT_DIR/instrumentation-test-temp.log"

# Add timeout to Gradle command itself to prevent indefinite hanging
# Use timeout command with kill signal after timeout
echo "Starting Gradle connectedDebugAndroidTest with 25 minute timeout..."
timeout 1500 ./gradlew connectedDebugAndroidTest --info --stacktrace > "$TEST_LOG_FILE" 2>&1 &
GRADLE_PID=$!
echo "Gradle connectedDebugAndroidTest started with PID: $GRADLE_PID"
echo "Test output being logged to: $TEST_LOG_FILE"
echo "Gradle process has 25 minute timeout (1500s)"

TIMEOUT_SECONDS=1800  # 30 minutes total (25 min Gradle timeout + 5 min buffer)
ELAPSED_TIME=0

echo "Waiting for Gradle connectedDebugAndroidTest to complete (timeout: ${TIMEOUT_SECONDS}s)..."
while ps -p $GRADLE_PID > /dev/null && [ $ELAPSED_TIME -lt $TIMEOUT_SECONDS ]; do
  sleep 5
  ELAPSED_TIME=$((ELAPSED_TIME + 5))
  echo "Elapsed: ${ELAPSED_TIME}s / ${TIMEOUT_SECONDS}s"
done

if ps -p $GRADLE_PID > /dev/null; then
  echo "⚠️  Timeout reached. Gradle process $GRADLE_PID is still running..."
  echo "   This suggests Gradle is hung. Checking recent log output..."
  tail -50 "$TEST_LOG_FILE" 2>/dev/null || echo "   (Log file not accessible)"
  echo "Killing Gradle process $GRADLE_PID..."
  kill -9 $GRADLE_PID 2>/dev/null || true
  sleep 2
  # Make sure it's dead
  if ps -p $GRADLE_PID > /dev/null; then
    echo "⚠️  Process still running, force killing..."
    kill -9 $GRADLE_PID 2>/dev/null || true
  fi
  TEST_EXIT=124 # Common exit code for timeout
  echo "Gradle process $GRADLE_PID killed."
else
  echo "Gradle connectedDebugAndroidTest completed."
  # Need to get the actual exit code from the background process if it completed
  wait $GRADLE_PID
  TEST_EXIT=$?
  echo "Gradle exit code: $TEST_EXIT"
fi

# --- Stop capturing logcat ---
echo "Stopping adb logcat (PID: $LOGCAT_PID)..."
kill $LOGCAT_PID || true
# --- End capturing logcat ---

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
if [ -f "$TEST_LOG_FILE" ]; then
    grep -E "(BUILD SUCCESSFUL|BUILD FAILED|tests completed|test failed|INSTRUMENTATION_STATUS)" "$TEST_LOG_FILE" | tail -50 || echo "No test summary found"
else
    echo "Test log file not found: $TEST_LOG_FILE"
fi

echo ""
echo "=== Failed Test Details ==="
if [ -f "$TEST_LOG_FILE" ]; then
    grep -E "FAILED|FAILURE|Exception|Error" "$TEST_LOG_FILE" | grep -v "^\s*at " | tail -30 || echo "No detailed failure info found"
else
    echo "Test log file not found: $TEST_LOG_FILE"
fi
echo ""
echo "=== Test Report Location ==="
echo "See detailed report at: app/build/reports/androidTests/connected/debug/index.html"

exit $TEST_EXIT