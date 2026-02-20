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

./scripts/install-apk-with-retry.sh app/build/outputs/apk/debug/app-debug.apk

echo "Pre-approving VPN permissions..."
adb shell appops set com.multiregionvpn ACTIVATE_VPN allow || echo "Failed to set appops"

echo "Waiting for system services to be fully ready..."
for i in $(seq 1 10); do
  if adb shell service check package | grep -q "Service package: found"; then
    echo "Package manager service is ready."
    break
  fi
  echo "Waiting for package manager... ($i/10)"
  sleep 5
done

echo "Running Maestro tests (with single retry on failure)..."
set +e
maestro test .maestro/*.yaml
EXIT_CODE=$?
if [ $EXIT_CODE -ne 0 ]; then
  echo "Maestro failed (exit $EXIT_CODE). Retrying once after short delay..."
  sleep 5
  maestro test .maestro/*.yaml
  EXIT_CODE=$?
fi
exit $EXIT_CODE
