#!/bin/bash

# Update France Server on Device
# Fetches a fresh NordVPN France server and updates the tunnel

DEVICE="${DEVICE:-192.168.68.73:35305}"

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🇫🇷 UPDATING FRANCE SERVER"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Check for credentials
if [ -z "$NORDVPN_USERNAME" ] || [ -z "$NORDVPN_PASSWORD" ]; then
    echo "❌ ERROR: NORDVPN_USERNAME and NORDVPN_PASSWORD must be set"
    echo ""
    echo "Load from .env file:"
    echo "  source .env"
    echo "  ./scripts/update-france-server.sh"
    exit 1
fi

# Check device connection
echo "1️⃣ Checking device connection..."
if ! adb -s "$DEVICE" shell echo "connected" > /dev/null 2>&1; then
    echo "❌ Device $DEVICE not connected"
    echo ""
    echo "Reconnect with:"
    echo "  adb connect $DEVICE"
    exit 1
fi
echo "✅ Device connected: $DEVICE"
echo ""

# Build test APK if needed
echo "2️⃣ Building test APK..."
cd "$(dirname "$0")/.." || exit 1
./gradlew :app:assembleDebugAndroidTest -q
echo "✅ Test APK built"
echo ""

# Install test APK
echo "3️⃣ Installing test APK..."
adb -s "$DEVICE" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk 2>&1 | grep -v "Performing"
echo ""

# Run update test
echo "4️⃣ Fetching and updating France server..."
echo ""
adb -s "$DEVICE" shell am instrument -w \
  -e class com.multiregionvpn.GoogleTvCompatibilityTest#test_updateFranceServer \
  -e NORDVPN_USERNAME "$NORDVPN_USERNAME" \
  -e NORDVPN_PASSWORD "$NORDVPN_PASSWORD" \
  com.multiregionvpn.test/androidx.test.runner.AndroidJUnitRunner

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ FRANCE SERVER UPDATED!"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "Check the app on your device:"
echo "  • Go to Tunnels tab"
echo "  • Find 'France - Streaming'"
echo "  • Verify server hostname is updated"
echo ""

