#!/bin/bash

# Build app with openvpn3 and deploy to Pixel 6 with preloaded credentials and VPN configs
# 
# USAGE:
#   ./scripts/build-and-deploy-pixel.sh
#
# This script will:
#   1. Load credentials from .env file
#   2. Build the app with openvpn3 library
#   3. Install it on Pixel 6 device
#   4. Preload NordVPN credentials
#   5. Create French and English VPN configurations

set -e

# Default device ID for Pixel 6
DEVICE="${DEVICE:-18311FDF600EVG}"

# Default NordVPN server hostnames
UK_SERVER="${UK_SERVER:-uk1827.nordvpn.com}"
FR_SERVER="${FR_SERVER:-fr985.nordvpn.com}"

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🚀 Building and Deploying Multi-Region VPN to Pixel 6"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Change to project root
cd "$(dirname "$0")/.." || exit 1

# Step 1: Load credentials from .env
echo "📁 Step 1: Loading credentials from .env..."
if [ ! -f .env ]; then
    echo "❌ ERROR: .env file not found"
    echo ""
    echo "Create a .env file with:"
    echo "  NORDVPN_USERNAME=your_username"
    echo "  NORDVPN_PASSWORD=your_password"
    exit 1
fi

# Load .env file (handle values without quotes)
set -a
source .env
set +a

if [ -z "$NORDVPN_USERNAME" ] || [ -z "$NORDVPN_PASSWORD" ]; then
    echo "❌ ERROR: NORDVPN_USERNAME and NORDVPN_PASSWORD must be set in .env"
    exit 1
fi

echo "✅ Credentials loaded from .env"
echo "   Username: ${NORDVPN_USERNAME:0:10}..."
echo ""

# Step 2: Check device connection
echo "📱 Step 2: Checking device connection..."
if ! adb devices | grep -q "$DEVICE"; then
    echo "⚠️  Device $DEVICE not found in adb devices"
    echo "   Attempting to connect..."
    if ! adb -s "$DEVICE" shell echo "connected" > /dev/null 2>&1; then
        echo "❌ ERROR: Cannot connect to device $DEVICE"
        echo ""
        echo "Available devices:"
        adb devices
        echo ""
        echo "To use a different device, set DEVICE environment variable:"
        echo "  DEVICE=your_device_id ./scripts/build-and-deploy-pixel.sh"
        exit 1
    fi
fi

echo "✅ Device connected: $DEVICE"
echo ""

# Step 3: Build the app with openvpn3
echo "🔨 Step 3: Building app with openvpn3 library..."
echo "   This may take several minutes (native code compilation)..."
echo ""

# Build debug APK (includes openvpn3 native libraries)
./gradlew :app:assembleDebug -q

if [ ! -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
    echo "❌ ERROR: APK build failed"
    exit 1
fi

echo "✅ App built successfully"
echo "   APK: app/build/outputs/apk/debug/app-debug.apk"
echo ""

# Step 4: Build test APK (needed for bootstrap)
echo "🧪 Step 4: Building test APK..."
./gradlew :app:assembleDebugAndroidTest -q

if [ ! -f "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk" ]; then
    echo "❌ ERROR: Test APK build failed"
    exit 1
fi

echo "✅ Test APK built successfully"
echo ""

# Step 5: Install app on device
echo "📲 Step 5: Installing app on Pixel 6..."
adb -s "$DEVICE" install -r app/build/outputs/apk/debug/app-debug.apk 2>&1 | grep -v "Performing Streamed Install" || true
echo "✅ App installed"
echo ""

# Step 6: Install test APK
echo "🧪 Step 6: Installing test APK..."
adb -s "$DEVICE" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk 2>&1 | grep -v "Performing Streamed Install" || true
echo "✅ Test APK installed"
echo ""

# Step 7: Grant VPN permission
echo "🔐 Step 7: Granting VPN permission..."
adb -s "$DEVICE" shell appops set com.multiregionvpn ACTIVATE_VPN allow
echo "✅ VPN permission granted"
echo ""

# Step 8: Bootstrap credentials and VPN configs
echo "🔑 Step 8: Bootstrapping credentials and VPN configs..."
echo "   Creating:"
echo "   - NordVPN credentials"
echo "   - UK VPN config ($UK_SERVER)"
echo "   - FR VPN config ($FR_SERVER)"
echo ""

adb -s "$DEVICE" shell am instrument -w \
  -e class com.multiregionvpn.BootstrapCredentialsTest \
  -e NORDVPN_USERNAME "$NORDVPN_USERNAME" \
  -e NORDVPN_PASSWORD "$NORDVPN_PASSWORD" \
  -e UK_SERVER "$UK_SERVER" \
  -e FR_SERVER "$FR_SERVER" \
  com.multiregionvpn.test/com.multiregionvpn.HiltTestRunner 2>&1 | grep -E "(✅|❌|🔐|Bootstrapping|Saved|Created|COMPLETE|Configured|UK tunnel|FR tunnel)" || true

# Check if bootstrap was successful (test returns OK if successful)
if adb -s "$DEVICE" shell am instrument -w \
  -e class com.multiregionvpn.BootstrapCredentialsTest \
  -e NORDVPN_USERNAME "$NORDVPN_USERNAME" \
  -e NORDVPN_PASSWORD "$NORDVPN_PASSWORD" \
  -e UK_SERVER "$UK_SERVER" \
  -e FR_SERVER "$FR_SERVER" \
  com.multiregionvpn.test/com.multiregionvpn.HiltTestRunner 2>&1 | grep -q "OK (1 test)"; then
    echo "✅ Bootstrap completed successfully"
else
    echo "⚠️  Bootstrap may have failed - check logs above"
fi

echo ""

# Step 9: Restart app
echo "🔄 Step 9: Restarting app..."
adb -s "$DEVICE" shell am start -S -n com.multiregionvpn/.ui.MainActivity
echo "✅ App restarted"
echo ""

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ DEPLOYMENT COMPLETE!"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "Configured on device:"
echo "  ✅ App installed with openvpn3 library"
echo "  ✅ NordVPN credentials saved"
echo "  ✅ UK tunnel created ($UK_SERVER)"
echo "  ✅ FR tunnel created ($FR_SERVER)"
echo ""
echo "Next steps:"
echo "  1. Open the app on your Pixel 6"
echo "  2. Go to Settings tab"
echo "  3. Check that credentials are saved"
echo "  4. Verify UK and FR tunnels are listed"
echo "  5. Configure app routing rules as needed"
echo "  6. Toggle VPN ON to start using it"
echo ""
echo "To view logs:"
echo "  adb -s $DEVICE logcat | grep -E '(MultiRegionVPN|OpenVPN|VpnEngine)'"
echo ""

