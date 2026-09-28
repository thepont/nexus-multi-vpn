#!/bin/bash
# Install Android build dependencies
set -e

echo "=== Checking installed build tools ==="
# Check for essential build dependencies
which cmake || echo "⚠️  CMake not found"
which ninja || echo "⚠️  Ninja not found"
which make || echo "⚠️  Make not found"

# Install CMake and Ninja if not present (for potential native builds)
echo "=== Installing build dependencies ==="
sudo apt-get update -qq
sudo apt-get install -y cmake ninja-build build-essential

echo "=== Installed versions ==="
cmake --version
ninja --version

echo "=== Android SDK Components ==="
SDKMANAGER_BIN=$(find "$ANDROID_HOME/cmdline-tools" -name "sdkmanager" | head -n 1)
if [ -n "$SDKMANAGER_BIN" ]; then
    "$SDKMANAGER_BIN" --list_installed || true
else
    echo "⚠️ sdkmanager binary not found under $ANDROID_HOME/cmdline-tools"
fi
