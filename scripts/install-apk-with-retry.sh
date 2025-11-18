#!/bin/sh
# Install APK with retry logic for E2E tests
# This script waits for the emulator to stabilize, then installs the APK with retries

set -e

APK_PATH="${1:-app/build/outputs/apk/debug/app-debug.apk}"
MAX_RETRIES=5
RETRY_DELAY=15
SETTLE_TIME=30

echo "Waiting ${SETTLE_TIME}s for emulator to fully settle..."
sleep "$SETTLE_TIME"

# Verify package manager service is available before attempting installation
echo "Verifying package manager service is available..."
for i in $(seq 1 60); do
  if adb shell pm list packages >/dev/null 2>&1; then
    echo "✅ Package manager service is ready (attempt $i/60)"
    break
  fi
  if [ $i -eq 60 ]; then
    echo "⚠️  Package manager service not ready after 60 attempts, but continuing..."
  fi
  sleep 1
done

# Additional wait for system services to fully stabilize
echo "Waiting additional 10s for system services to stabilize..."
sleep 10

# Verify ADB connection is stable
echo "Verifying ADB connection stability..."
for i in $(seq 1 10); do
  if adb shell echo "ready" >/dev/null 2>&1; then
    echo "✅ ADB connection verified (attempt $i/10)"
    break
  fi
  if [ $i -eq 10 ]; then
    echo "⚠️  ADB connection verification failed after 10 attempts"
  fi
  sleep 1
done

echo "Installing APK (with ${MAX_RETRIES} retries)..."
i=1
while [ $i -le "$MAX_RETRIES" ]; do
  # Before each attempt, verify package manager is still available
  if ! adb shell pm list packages >/dev/null 2>&1; then
    echo "⚠️  Package manager service unavailable, waiting ${RETRY_DELAY}s..."
    sleep "$RETRY_DELAY"
  fi
  
  if adb install -r "$APK_PATH"; then
    echo "✅ APK installed successfully on attempt $i"
    exit 0
  else
    if [ $i -lt "$MAX_RETRIES" ]; then
      echo "⚠️  Installation attempt $i failed, retrying in ${RETRY_DELAY}s..."
      sleep "$RETRY_DELAY"
    else
      echo "❌ APK installation failed after $i attempts"
      exit 1
    fi
    i=$((i + 1))
  fi
done
