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

echo ""
echo "=== Preparing Maestro Connection ==="

# Ensure device is fully awake and unlocked
echo "Waking device and ensuring screen is on..."
adb -s emulator-5554 shell input keyevent KEYCODE_WAKEUP
adb -s emulator-5554 shell input keyevent KEYCODE_MENU
sleep 2

# Verify device is responsive
echo "Verifying device responsiveness..."
adb -s emulator-5554 shell dumpsys window | grep "mCurrentFocus" || echo "Window manager check completed"

# After installing large APK, Android performs background optimizations
# Give it extra time to complete before Maestro tries to connect
echo "Waiting 30s for post-install optimizations to complete..."
echo "(Android may be optimizing the 81MB APK in the background)"
sleep 30

echo "Maestro setup complete, ready to run tests..."

echo "Running Maestro tests (with single retry on failure)..."
echo "Note: TV tests in .maestro/tv/ are intentionally excluded (require D-pad navigation)"

# Use explicit glob with bash extglob to avoid shell expansion issues
shopt -s nullglob
TEST_FILES=(.maestro/*.yaml)
shopt -u nullglob

if [ ${#TEST_FILES[@]} -eq 0 ]; then
  echo "ERROR: No Maestro test files found in .maestro/*.yaml" >&2
  exit 1
fi

echo "Found ${#TEST_FILES[@]} test files to run:"
printf '  - %s\n' "${TEST_FILES[@]}"

echo ""
echo "Target device: emulator-5554"

set +e
maestro test --device emulator-5554 "${TEST_FILES[@]}"
EXIT_CODE=$?
if [ $EXIT_CODE -ne 0 ]; then
  echo ""
  echo "Maestro failed (exit $EXIT_CODE). Retrying once after 10s delay..."
  sleep 10
  maestro test --device emulator-5554 "${TEST_FILES[@]}"
  EXIT_CODE=$?
fi

if [ $EXIT_CODE -eq 0 ]; then
  echo "✅ All Maestro tests passed"
else
  echo "❌ Maestro tests failed with exit code $EXIT_CODE" >&2
fi

exit $EXIT_CODE
