#!/bin/bash
# Run Maestro E2E tests in emulator with retry logic
set -e

echo "Waiting for emulator to be ready..."
adb wait-for-device || true

echo "Waiting for sys.boot_completed..."
for i in $(seq 1 300); do
  BOOT=$(adb -s emulator-5554 shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
  if [ "$BOOT" = "1" ]; then
    echo "Emulator boot completed."
    break
  fi
  if [ $i -eq 300 ]; then
    echo "Timeout waiting for boot_completed" >&2
    exit 1
  fi
  sleep 2
done

echo "Checking for offline device state..."
if adb devices | grep -q "offline"; then
  echo "ADB device offline - restarting server..."
  adb kill-server || true
  sleep 2
  adb start-server || true
  adb wait-for-device || true
fi

echo "Settling emulator for 20s..."
sleep 20

# Additional wait and ADB connection verification
echo "Verifying ADB connection stability..."
for i in $(seq 1 10); do
  if adb -s emulator-5554 shell echo "test" >/dev/null 2>&1; then
    echo "ADB connection verified (attempt $i/10)"
    break
  fi
  if [ $i -eq 10 ]; then
    echo "⚠️  ADB connection verification failed after 10 attempts"
  fi
  sleep 1
done

# Wait additional time for emulator to fully stabilize
echo "Waiting additional 10s for emulator to fully stabilize..."
sleep 10

./scripts/install-apk-with-retry.sh app/build/outputs/apk/debug/app-debug.apk

# Verify app is installed and ready
echo "Verifying app installation..."
if ! adb -s emulator-5554 shell pm list packages | grep -q "com.multiregionvpn"; then
  echo "⚠️  App not found in package list, but continuing..."
fi

# Additional wait before starting Maestro
echo "Final wait before Maestro tests (5s)..."
sleep 5

# Start the app to ensure it's ready for Maestro
echo "Starting the app to ensure it's ready..."
adb -s emulator-5554 shell am start -n com.multiregionvpn/.ui.MainActivity || echo "⚠️  Could not start app (may already be running)"
sleep 3

# Ensure ADB is in a good state - restart if needed
echo "Ensuring ADB is ready..."
adb -s emulator-5554 root || echo "⚠️  Could not restart ADB as root"
sleep 2
adb -s emulator-5554 wait-for-device || true

# Verify ADB connection one more time
if ! adb -s emulator-5554 shell echo "ready" >/dev/null 2>&1; then
  echo "⚠️  ADB connection not stable, restarting ADB server..."
  adb kill-server || true
  sleep 2
  adb start-server || true
  adb wait-for-device || true
fi

# Try to enable ADB over TCP for better reliability
echo "Checking ADB connection method..."
adb -s emulator-5554 tcpip 5555 || echo "⚠️  Could not enable TCP/IP mode"

# Additional wait to ensure everything is stable
echo "Final stabilization wait (10s)..."
sleep 10

# Verify app is still running
if ! adb -s emulator-5554 shell pidof com.multiregionvpn >/dev/null 2>&1; then
  echo "App not running, restarting..."
  adb -s emulator-5554 shell am start -n com.multiregionvpn/.ui.MainActivity || true
  sleep 3
fi

echo "Running Maestro tests (with single retry on failure)..."
set +e
# Try using explicit device flag first, fallback to default if not supported
if maestro test --help 2>&1 | grep -q "\-\-device"; then
  echo "Using --device flag..."
  maestro test --device emulator-5554 .maestro/*.yaml
  EXIT_CODE=$?
else
  echo "Using default Maestro test command..."
  # Set environment variable to potentially help with timeout
  export MAESTRO_ANDROID_DRIVER_TIMEOUT=180
  maestro test .maestro/*.yaml
  EXIT_CODE=$?
fi

if [ $EXIT_CODE -ne 0 ]; then
  echo "Maestro failed (exit $EXIT_CODE). Retrying once after short delay..."
  sleep 10
  # On retry, ensure ADB is still working
  adb -s emulator-5554 shell echo "retry-check" >/dev/null 2>&1 || {
    echo "⚠️  ADB connection lost, restarting..."
    adb kill-server || true
    sleep 2
    adb start-server || true
    adb wait-for-device || true
  }
  maestro test .maestro/*.yaml
  EXIT_CODE=$?
fi
exit $EXIT_CODE
