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

echo "=== Accepting Android SDK licenses ==="
# Accept all licenses to prevent interactive prompts with timeout
timeout 120 bash -c 'yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses' || true

echo "=== Android SDK setup complete ==="
