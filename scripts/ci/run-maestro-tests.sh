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

echo "Running Maestro tests (with single retry on failure)..."
set +e
# Set Maestro timeout environment variable to give more time for driver startup
export MAESTRO_DRIVER_TIMEOUT=120
maestro test .maestro/*.yaml
EXIT_CODE=$?
if [ $EXIT_CODE -ne 0 ]; then
  echo "Maestro failed (exit $EXIT_CODE). Retrying once after short delay..."
  sleep 5
  maestro test .maestro/*.yaml
  EXIT_CODE=$?
fi
exit $EXIT_CODE
