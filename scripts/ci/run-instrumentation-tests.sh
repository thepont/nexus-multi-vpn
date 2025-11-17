#!/bin/bash
# Run instrumentation tests with monitoring
set -e

echo "========================================"
echo "Starting Instrumentation Tests"
echo "========================================"
echo "Timestamp: $(date)"

# Run with monitoring
set +e
# Don't exclude native build tasks when SKIP_NATIVE_BUILD=true, as they don't exist
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

exit $TEST_EXIT
