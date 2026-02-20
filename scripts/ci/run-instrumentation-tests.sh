#!/bin/bash
# Run instrumentation tests with monitoring
set -e

echo "========================================"
echo "Starting Instrumentation Tests"
echo "========================================"
echo "Timestamp: $(date)"

# Run with monitoring
set +e
# Native builds are NOT excluded here because instrumentation tests (E2E)
# require native libraries to establish actual VPN connections.
./gradlew connectedDebugAndroidTest --info --stacktrace 2>&1 | tee instrumentation-test.log

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
