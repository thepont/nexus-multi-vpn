#!/bin/bash
# Run instrumentation tests with monitoring
set -e

echo "========================================"
echo "Starting Instrumentation Tests"
echo "========================================"
echo "Timestamp: $(date)"

echo "Waiting 60s for emulator to stabilize..."
sleep 60

# Wait for package manager service to be ready
echo "Waiting for package manager service..."
for i in $(seq 1 30); do
  if adb shell service check package | grep -q "Service package: found"; then
    echo "Package manager service found."
    break
  fi
  echo "Package manager service not ready yet (attempt $i)..."
  sleep 5
done

# Run with monitoring
set +e
./gradlew connectedDebugAndroidTest -x externalNativeBuildDebug -x externalNativeBuildRelease --info --stacktrace 2>&1 | tee instrumentation-test.log

TEST_EXIT=$?
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

exit $TEST_EXIT
