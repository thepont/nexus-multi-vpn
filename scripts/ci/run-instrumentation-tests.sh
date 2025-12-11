#!/bin/bash
# Run instrumentation tests with monitoring
set -e

# Helper: Retry an adb shell no-op until it responds
wait_for_adb_shell() {
    local max_attempts=${1:-6}
    local sleep_seconds=${2:-5}
    local attempt=1

    while [ $attempt -le $max_attempts ]; do
        if adb shell "echo adb-ready" >/dev/null 2>&1; then
            echo "✅ ADB shell responsive (attempt ${attempt}/${max_attempts})"
            return 0
        fi
        echo "⚠️  ADB shell not ready yet (attempt ${attempt}/${max_attempts}), retrying in ${sleep_seconds}s..."
        sleep $sleep_seconds
        attempt=$((attempt + 1))
    done

    echo "❌ Error: ADB shell unresponsive after ${max_attempts} attempts"
    return 1
}

# Helper: Wait for Package Manager to become available
wait_for_package_manager() {
    local max_attempts=${1:-12}
    local sleep_seconds=${2:-5}
    local attempt=1

    while [ $attempt -le $max_attempts ]; do
        if adb shell pm list packages >/dev/null 2>&1; then
            echo "✅ Package manager ready (attempt ${attempt}/${max_attempts})"
            return 0
        fi
        PM_OUTPUT=$(adb shell pm list packages 2>&1 | head -20 || true)
        [ -n "$PM_OUTPUT" ] && echo "   Package manager error: ${PM_OUTPUT}"
        echo "⚠️  Package manager not ready (attempt ${attempt}/${max_attempts}), retrying in ${sleep_seconds}s..."
        if [ $attempt -eq 1 ]; then
            echo "   Giving device a short grace period to finish boot tasks..."
        fi
        sleep $sleep_seconds
        attempt=$((attempt + 1))
    done

    echo "❌ Error: Package manager not ready after ${max_attempts} attempts"
    echo "Dumping package service status for diagnostics:"
    adb shell dumpsys package 2>/dev/null | head -n 200 || true
    return 1
}

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
    # Note: Device may go offline during boot, so we need to handle that
    echo "Waiting for ADB device to become available..."
    
    # Wait for device and handle offline state
    DEVICE_ONLINE=false
    TIMEOUT=180 # 3 minutes - emulator may need time to stabilize
    ELAPSED=0
    
    while [ $ELAPSED -lt $TIMEOUT ]; do
      # Check if device is online
      DEVICE_STATE=$(adb devices 2>/dev/null | grep "emulator" | awk '{print $2}' | head -1)
      
      if [ "$DEVICE_STATE" = "device" ]; then
        echo "✅ Device is online (Elapsed: ${ELAPSED}s)"
        DEVICE_ONLINE=true
        break
      elif [ "$DEVICE_STATE" = "offline" ]; then
        echo "⚠️  Device is offline, waiting for it to come online... (Elapsed: ${ELAPSED}s / ${TIMEOUT}s)"
        # Don't restart ADB - it may cause device to stay offline
        # ADB will reconnect automatically when device is ready
        # Restarting ADB disconnects device, making problem worse
        sleep 5
      else
        echo "⏳ Waiting for device... (Elapsed: ${ELAPSED}s / ${TIMEOUT}s)"
        adb wait-for-device 2>/dev/null || true
      fi
      
      sleep 5
      ELAPSED=$((ELAPSED + 5))
    done
    
    if [ "$DEVICE_ONLINE" != "true" ]; then
      echo "❌ ERROR: Device did not come online within ${TIMEOUT}s"
      echo "   Current device state:"
      adb devices -l
      exit 1
    fi
    
    # Verify emulator is booted (now that device is online)
    echo "Checking emulator boot status..."
    BOOT_COMPLETED=""
    BOOT_TIMEOUT=120 # 2 minutes for boot to complete
    BOOT_ELAPSED=0
    
    while [[ "$BOOT_COMPLETED" != "1" && $BOOT_ELAPSED -lt $BOOT_TIMEOUT ]]; do
      # Check device is still online before checking boot status
      DEVICE_STATE=$(adb devices 2>/dev/null | grep "emulator" | awk '{print $2}' | head -1)
      if [ "$DEVICE_STATE" != "device" ]; then
        echo "⚠️  Device went offline again, waiting for reconnect..."
        sleep 5
        BOOT_ELAPSED=$((BOOT_ELAPSED + 5))
        continue
      fi
      
      BOOT_COMPLETED=$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
      if [ -n "$BOOT_COMPLETED" ]; then
        echo "Emulator boot status: $BOOT_COMPLETED (Elapsed: ${BOOT_ELAPSED}s / ${BOOT_TIMEOUT}s)"
      fi
      sleep 5
      BOOT_ELAPSED=$((BOOT_ELAPSED + 5))
    done
    
    if [ "$BOOT_COMPLETED" != "1" ]; then
      echo "⚠️  Warning: Emulator may not be fully booted, but continuing..."
      echo "   Boot status: $BOOT_COMPLETED"
    else
      echo "✅ Emulator boot completed"
    fi
    
    # Additional wait for ADB daemon to stabilize after boot
    # ADB daemon may need time to be ready for large file transfers
    echo "Waiting for ADB daemon to stabilize (10 seconds)..."
    sleep 10
    
    # Verify ADB sync readiness before proceeding
    echo "Verifying ADB sync readiness..."
    wait_for_adb_shell 8 5
    wait_for_package_manager 12 5
    
    echo "✅ ADB sync readiness verified"
    
    # List connected devices for debugging and verify connection
    echo "Connected devices:"
    DEVICE_OUTPUT=$(adb devices -l)
    echo "$DEVICE_OUTPUT"
    
    # Verify at least one device is online (check second column = "device")
    # Format: "emulator-5554          device product:..."
    DEVICE_STATE=$(echo "$DEVICE_OUTPUT" | grep "emulator" | awk '{print $2}' | head -1)
    if [ "$DEVICE_STATE" != "device" ]; then
      echo "❌ ERROR: No devices in 'device' state (online and ready)"
      echo "   Device states:"
      echo "$DEVICE_OUTPUT"
      echo "   Current state: '$DEVICE_STATE'"
      echo "   This will cause Gradle to hang waiting for a device"
      exit 1
    fi
    
    # Count online devices (check second column = "device")
    ONLINE_DEVICES=$(echo "$DEVICE_OUTPUT" | grep "emulator" | awk '{print $2}' | grep -c "^device$" || echo "0")
    echo "✅ Found $ONLINE_DEVICES online device(s)"
    
    # Verify device is actually reachable
    echo "Verifying device is reachable..."
    if ! adb shell getprop ro.build.version.sdk > /dev/null 2>&1; then
      echo "❌ ERROR: Cannot read device properties - device not reachable"
      exit 1
    fi
    SDK_VERSION=$(adb shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r')
    echo "✅ Device reachable (Android SDK: $SDK_VERSION)"
    
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

# Final cleanup before Gradle runs
# NOTE: We DON'T manually install the APK here anymore
# Gradle's connectedDebugAndroidTest will handle installation via installDebug task
# Manual installation was causing conflicts - Gradle would detect the app and hang
echo "Final cleanup before Gradle test run (Gradle will handle installation)..."
# Force stop the app (if it exists)
adb shell am force-stop com.multiregionvpn 2>/dev/null || true
adb shell am force-stop com.multiregionvpn.test 2>/dev/null || true
sleep 1

# Don't manually install - let Gradle's installDebug task handle it completely
# Manual installation was causing conflicts where Gradle would detect the app
# and hang waiting for something. Gradle's installDebug task handles installation
# properly, including signature mismatches via adb install -r flag.
echo "Skipping manual installation - Gradle will handle it via installDebug task"
echo "This prevents the hanging issue where Gradle detects manually installed app"

# Run tests with monitoring
set +e
# Use connectedDebugAndroidTest - it will install both main APK and test APK, then run tests
# Gradle's installDebug task handles installation properly, including signature mismatches
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