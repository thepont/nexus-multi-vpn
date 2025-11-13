#!/bin/bash

# Bootstrap NordVPN credentials on Pixel device
# Runs instrumentation test with credentials from environment
#
# USAGE:
#   export NORDVPN_USERNAME="your_username"
#   export NORDVPN_PASSWORD="your_password"
#   ./scripts/bootstrap-credentials-pixel.sh
#
# OR:
#   NORDVPN_USERNAME="xxx" NORDVPN_PASSWORD="yyy" ./scripts/bootstrap-credentials-pixel.sh

DEVICE="${DEVICE:-18311FDF600EVG}"

# Check for credentials in environment
if [ -z "$NORDVPN_USERNAME" ] || [ -z "$NORDVPN_PASSWORD" ]; then
    echo "❌ ERROR: NORDVPN_USERNAME and NORDVPN_PASSWORD must be set in environment"
    echo ""
    echo "Load from .env file:"
    echo "  source .env"
    echo "  ./scripts/bootstrap-credentials-pixel.sh"
    echo ""
    echo "Or pass directly:"
    echo "  NORDVPN_USERNAME='xxx' NORDVPN_PASSWORD='yyy' ./scripts/bootstrap-credentials-pixel.sh"
    exit 1
fi

echo "🔐 Bootstrapping NordVPN credentials on Pixel..."
echo ""

# Build test APK if needed
echo "1. Building test APK..."
cd "$(dirname "$0")/.." || exit 1
./gradlew :app:assembleDebugAndroidTest -q

# Install test APK
echo "2. Installing test APK..."
adb -s "$DEVICE" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk 2>&1 | grep -v "Performing Streamed Install"

# Run bootstrap test with credentials
echo "3. Running bootstrap test..."
adb -s "$DEVICE" shell am instrument -w \
  -e class com.multiregionvpn.BootstrapCredentialsTest \
  -e NORDVPN_USERNAME "$NORDVPN_USERNAME" \
  -e NORDVPN_PASSWORD "$NORDVPN_PASSWORD" \
  com.multiregionvpn.test/androidx.test.runner.AndroidJUnitRunner

# Restart app
echo ""
echo "4. Restarting app..."
adb -s "$DEVICE" shell am start -S -n com.multiregionvpn/.MainActivity

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ BOOTSTRAP COMPLETE!"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "Configured on device:"
echo "  ✅ NordVPN credentials saved"
echo "  ✅ UK tunnel created"
echo "  ✅ FR tunnel created"
echo ""
echo "Open the app and check:"
echo "  • Tunnels tab → See UK & FR tunnels"
echo "  • Apps tab → See smart badges"
echo "  • Settings tab → See saved credentials"
echo ""

