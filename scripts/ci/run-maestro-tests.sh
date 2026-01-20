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

set +e
maestro test "${TEST_FILES[@]}"
EXIT_CODE=$?
if [ $EXIT_CODE -ne 0 ]; then
  echo "Maestro failed (exit $EXIT_CODE). Retrying once after short delay..."
  sleep 5
  maestro test "${TEST_FILES[@]}"
  EXIT_CODE=$?
fi

if [ $EXIT_CODE -eq 0 ]; then
  echo "✅ All Maestro tests passed"
else
  echo "❌ Maestro tests failed with exit code $EXIT_CODE" >&2
fi

exit $EXIT_CODE
