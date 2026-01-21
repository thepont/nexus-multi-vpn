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
# Bypass interactive prompts by creating license files directly
mkdir -p $ANDROID_HOME/licenses
echo "24333f8a63b6825ea9c5514f83c2829b004d1fee" > $ANDROID_HOME/licenses/android-sdk-license
echo "d975f751698a77b662f1254ddbeed3901e976f5a" > $ANDROID_HOME/licenses/intel-android-extra-license
echo "e9acab5b5fbb560a72cfaecce8946896ff6aab9d" > $ANDROID_HOME/licenses/google-gdk-license
echo "33b6a2b64607f11b759f320ef9dff4ae5c47d97a" > $ANDROID_HOME/licenses/mips-android-sysimage-license
echo "d56a54d6bdbcc3c8c372b9d60b7f63f4e4c3a440" > $ANDROID_HOME/licenses/android-sdk-preview-license
echo "8933bad161af4178b1185d1a37fbf41ea5269c55" > $ANDROID_HOME/licenses/android-sdk-arm-dbt-license
echo -e "\n84831b9409646a918e30573bab4c9c91346d8abd" > $ANDROID_HOME/licenses/android-sdk-preview-license
echo "✓ Licenses accepted"

echo "=== Android SDK setup complete ==="
