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
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --list_installed
