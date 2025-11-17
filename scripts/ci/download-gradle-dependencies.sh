#!/bin/bash
# Download Gradle dependencies with monitoring
set -e

echo "========================================"
echo "Starting dependency download"
echo "========================================"
echo "Timestamp: $(date)"
echo "CI environment: $CI"
echo "SKIP_NATIVE_BUILD: $SKIP_NATIVE_BUILD"
echo "Java version: $(java -version 2>&1 | head -1)"
echo "Gradle version: $(./gradlew --version | grep Gradle)"
echo ""

# Create a monitoring script
cat > /tmp/monitor.sh << 'MONITOR_EOF'
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
    echo "Log file size: $CURRENT_SIZE bytes (was $LAST_SIZE)"
    
    if [ "$CURRENT_SIZE" -eq "$LAST_SIZE" ]; then
      STALL_COUNT=$((STALL_COUNT + 1))
      echo "⚠️  No new output for $((STALL_COUNT * INTERVAL)) seconds"
      
      if [ $STALL_COUNT -ge 4 ]; then
        echo "❌ STALLED: No output for $((STALL_COUNT * INTERVAL)) seconds"
        echo "Last 50 lines of output:"
        tail -50 "$LOG_FILE"
        exit 1
      fi
    else
      STALL_COUNT=0
    fi
    
    LAST_SIZE=$CURRENT_SIZE
    echo "Last 10 lines:"
    tail -10 "$LOG_FILE"
  else
    echo "Waiting for log file to be created..."
  fi
  echo "---"
done
MONITOR_EOF

chmod +x /tmp/monitor.sh

# Start monitoring in background
/tmp/monitor.sh gradle-dependency-download.log &
MONITOR_PID=$!

echo "=== Attempting simple dependency resolution first ==="
# Try a simpler approach: just resolve specific configurations we know we need
set +e  # Don't exit on error

./gradlew dependencies --configuration debugCompileClasspath --info 2>&1 | tee -a gradle-dependency-download.log
echo "✓ debugCompileClasspath resolved"

./gradlew dependencies --configuration debugRuntimeClasspath --info 2>&1 | tee -a gradle-dependency-download.log
echo "✓ debugRuntimeClasspath resolved"

./gradlew dependencies --configuration debugAndroidTestCompileClasspath --info 2>&1 | tee -a gradle-dependency-download.log
echo "✓ debugAndroidTestCompileClasspath resolved"

./gradlew dependencies --configuration debugAndroidTestRuntimeClasspath --info 2>&1 | tee -a gradle-dependency-download.log
echo "✓ debugAndroidTestRuntimeClasspath resolved"

# Now try our custom task
echo ""
echo "=== Running androidDependencies task ==="
# Don't exclude native build tasks when SKIP_NATIVE_BUILD=true, as they don't exist
./gradlew androidDependencies \
  --info --stacktrace 2>&1 | tee -a gradle-dependency-download.log

# Capture exit code of the gradle command (first command in pipeline), not tee
GRADLE_EXIT=${PIPESTATUS[0]}
set -e

# Stop monitoring
kill $MONITOR_PID 2>/dev/null || true

echo ""
echo "========================================"
echo "Dependency download completed"
echo "========================================"
echo "Timestamp: $(date)"
echo "Exit code: $GRADLE_EXIT"

# Show summary
echo ""
echo "=== Summary ==="
grep -E "(✓ Resolving:|✗ Could not resolve|Successfully resolved|Failed to resolve)" gradle-dependency-download.log | tail -20 || echo "No summary found"

echo ""
echo "=== Gradle cache size ==="
du -sh ~/.gradle/caches 2>/dev/null || echo "Cache directory not found"

exit $GRADLE_EXIT
