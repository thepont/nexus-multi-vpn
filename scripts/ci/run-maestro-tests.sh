#!/bin/bash
# Simplified Maestro test runner - standard approach
set -e

echo "=== Running Maestro E2E Tests ==="
echo "MAESTRO_DRIVER_STARTUP_TIMEOUT: ${MAESTRO_DRIVER_STARTUP_TIMEOUT:-120000}ms"
echo ""

# Wait for device
echo "Waiting for device..."
adb wait-for-device

# Wait for boot completion
echo "Waiting for boot to complete..."
timeout 600 bash -c 'until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\''\r'\'')" = "1" ]; do sleep 2; done'
echo "✓ Boot completed"

# Install APK
echo ""
echo "Installing APK..."
adb install -r app/build/outputs/apk/debug/app-debug.apk
echo "✓ APK installed"

# Run Maestro tests
echo ""
echo "Running Maestro tests..."
maestro test .maestro/*.yaml
