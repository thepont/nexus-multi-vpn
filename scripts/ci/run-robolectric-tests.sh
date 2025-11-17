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
GRADLE_PID=$2
INTERVAL=30
LAST_SIZE=0
STALL_COUNT=0
MAX_STALL_COUNT=3  # Reduced from 4 to 3 (90 seconds total)

while true; do
  sleep $INTERVAL
  
  # Check if gradle process is still running
  if ! kill -0 $GRADLE_PID 2>/dev/null; then
    echo "Gradle process completed"
    exit 0
  fi
  
  if [ -f "$LOG_FILE" ]; then
    CURRENT_SIZE=$(wc -c < "$LOG_FILE" 2>/dev/null || echo "0")
    echo "=== $(date) ==="
    echo "Test log size: $CURRENT_SIZE bytes (was $LAST_SIZE)"
    
    if [ "$CURRENT_SIZE" -eq "$LAST_SIZE" ]; then
      STALL_COUNT=$((STALL_COUNT + 1))
      echo "⚠️  No new test output for $((STALL_COUNT * INTERVAL)) seconds"
      
      if [ $STALL_COUNT -ge $MAX_STALL_COUNT ]; then
        echo "❌ TESTS STALLED: No output for $((STALL_COUNT * INTERVAL)) seconds"
        echo "Last 100 lines of test output:"
        tail -100 "$LOG_FILE"
        echo ""
        echo "Killing stalled Gradle process (PID: $GRADLE_PID) and all child processes..."
        # Kill the entire process group
        pkill -TERM -P $GRADLE_PID 2>/dev/null || true
        sleep 2
        pkill -KILL -P $GRADLE_PID 2>/dev/null || true
        kill -TERM $GRADLE_PID 2>/dev/null || true
        sleep 2
        kill -KILL $GRADLE_PID 2>/dev/null || true
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

# Run tests with detailed logging
set +e
./gradlew testDebugUnitTest \
  --info --stacktrace 2>&1 | tee robolectric-test.log &
GRADLE_PID=$!

# Start monitoring with Gradle PID
/tmp/test_monitor.sh robolectric-test.log $GRADLE_PID &
MONITOR_PID=$!

# Wait for gradle to finish
wait $GRADLE_PID
TEST_EXIT=$?

# Stop monitoring
kill $MONITOR_PID 2>/dev/null || true

set -e

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
