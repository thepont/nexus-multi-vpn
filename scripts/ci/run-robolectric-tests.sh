#!/bin/bash
# Run Robolectric unit tests with monitoring
set -e

echo "========================================"
echo "Starting Robolectric Unit Tests"
echo "========================================"
echo "Timestamp: $(date)"
echo "CI environment: $CI"
echo "SKIP_NATIVE_BUILD: $SKIP_NATIVE_BUILD"

# Create monitoring script
cat > /tmp/test_monitor.sh << 'MONITOR_EOF'
#!/bin/bash
LOG_FILE=$1
INTERVAL=30
LAST_SIZE=0
STALL_COUNT=0

while true; do
  sleep $INTERVAL
  
  if [ -f "$LOG_FILE" ]; then
    CURRENT_SIZE=$(wc -c < "$LOG_FILE" 2>/dev/null || echo "0")
    echo "=== $(date) ==="
    echo "Test log size: $CURRENT_SIZE bytes (was $LAST_SIZE)"
    
    if [ "$CURRENT_SIZE" -eq "$LAST_SIZE" ]; then
      STALL_COUNT=$((STALL_COUNT + 1))
      echo "⚠️  No new test output for $((STALL_COUNT * INTERVAL)) seconds"
      
      if [ $STALL_COUNT -ge 4 ]; then
        echo "❌ TESTS STALLED: No output for $((STALL_COUNT * INTERVAL)) seconds"
        echo "Last 100 lines of test output:"
        tail -100 "$LOG_FILE"
        exit 1
      fi
    else
      STALL_COUNT=0
    fi
    
    LAST_SIZE=$CURRENT_SIZE
    echo "Last 20 lines of test output:"
    tail -20 "$LOG_FILE"
  else
    echo "Waiting for test log file..."
  fi
  echo "---"
done
MONITOR_EOF

chmod +x /tmp/test_monitor.sh

# Start monitoring
/tmp/test_monitor.sh robolectric-test.log &
MONITOR_PID=$!

# Run tests with detailed logging
set +e
# Don't exclude native build tasks when SKIP_NATIVE_BUILD=true, as they don't exist
./gradlew testDebugUnitTest \
  --info --stacktrace 2>&1 | tee robolectric-test.log

# Capture exit code of the gradle command (first command in pipeline), not tee
TEST_EXIT=${PIPESTATUS[0]}
set -e

# Stop monitoring
kill $MONITOR_PID 2>/dev/null || true

echo ""
echo "========================================"
echo "Robolectric Unit Tests completed"
echo "========================================"
echo "Timestamp: $(date)"
echo "Exit code: $TEST_EXIT"

# Show test summary
echo ""
echo "=== Test Summary ==="
grep -E "(BUILD SUCCESSFUL|BUILD FAILED|> Task :app:testDebugUnitTest|tests completed|test failed|tests? skipped|SKIPPED)" robolectric-test.log | tail -30 || echo "No test summary found"

# Check for skipped tests
SKIPPED_COUNT=$(grep -c "SKIPPED" robolectric-test.log || echo "0")
echo ""
echo "=== Skipped tests: $SKIPPED_COUNT ==="
if [ "$SKIPPED_COUNT" -gt "0" ]; then
  echo "⚠️  Some tests were skipped:"
  grep "SKIPPED" robolectric-test.log || true
fi

exit $TEST_EXIT
