#!/bin/bash

# Script to run E2E VPN routing tests with credentials from .env file

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$PROJECT_ROOT"

# Check if .env file exists
if [ ! -f ".env" ]; then
    echo "❌ Error: .env file not found in project root"
    echo "   Please create .env file with NORDVPN_USERNAME and NORDVPN_PASSWORD"
    exit 1
fi

# Load credentials from .env
echo "📖 Loading credentials from .env file..."
export $(grep -v '^#' .env | grep -E '^NORDVPN_' | xargs)

# Verify credentials are loaded
if [ -z "$NORDVPN_USERNAME" ] || [ -z "$NORDVPN_PASSWORD" ]; then
    echo "❌ Error: NORDVPN_USERNAME or NORDVPN_PASSWORD not found in .env"
    echo "   Please ensure .env contains:"
    echo "   NORDVPN_USERNAME=your_username"
    echo "   NORDVPN_PASSWORD=your_password"
    exit 1
fi

echo "✅ Credentials loaded (username: ${NORDVPN_USERNAME:0:3}***)"

# Check if emulator/device is connected
if ! adb devices | grep -q "device$"; then
    echo "❌ Error: No Android device or emulator connected"
    echo "   Please start an emulator or connect a device"
    exit 1
fi

echo "📱 Running E2E tests with credentials..."
echo ""

# Set Java environment
export JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk}
export PATH=$JAVA_HOME/bin:$PATH

# Run tests with credentials passed as instrumentation arguments
./gradlew :app:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.NORDVPN_USERNAME="$NORDVPN_USERNAME" \
    -Pandroid.testInstrumentationRunnerArguments.NORDVPN_PASSWORD="$NORDVPN_PASSWORD"

echo ""
echo "✅ E2E tests completed"
