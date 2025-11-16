#!/bin/sh
# Install APK with retry logic for E2E tests
# This script waits for the emulator to stabilize, then installs the APK with retries

set -e

APK_PATH="${1:-app/build/outputs/apk/debug/app-debug.apk}"
MAX_RETRIES=3
RETRY_DELAY=10
SETTLE_TIME=30

echo "Waiting ${SETTLE_TIME}s for emulator to fully settle..."
sleep "$SETTLE_TIME"

echo "Installing APK (with ${MAX_RETRIES} retries)..."
i=1
while [ $i -le "$MAX_RETRIES" ]; do
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
