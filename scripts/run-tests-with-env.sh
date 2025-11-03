#!/bin/bash
# Script to run Maestro tests with environment variables from .env
# Usage: ./scripts/run-tests-with-env.sh [test-file.yaml]

set -e

# Load environment variables
if [ -f .env ]; then
    export $(grep -v '^#' .env | xargs)
    echo "✓ Loaded environment variables from .env"
else
    echo "⚠️  Warning: .env file not found"
    echo "   Create .env file from .env.example and fill in your credentials."
    exit 1
fi

# Verify required variables are set
if [ -z "$NORDVPN_USERNAME" ] || [ -z "$NORDVPN_PASSWORD" ]; then
    echo "❌ Error: NORDVPN_USERNAME and NORDVPN_PASSWORD must be set in .env"
    exit 1
fi

# Export variables for Maestro (Maestro can access env vars via ${VAR} in YAML)
export NORDVPN_USERNAME
export NORDVPN_PASSWORD
export TEST_VPN_HOSTNAME=${TEST_VPN_HOSTNAME:-uk1234.nordvpn.com}
export TEST_VPN_REGION=${TEST_VPN_REGION:-UK}
export TEST_APP_PACKAGE=${TEST_APP_PACKAGE:-com.android.chrome}

echo "Environment variables:"
echo "  NORDVPN_USERNAME: ${NORDVPN_USERNAME:0:5}..." # Show first 5 chars only
echo "  NORDVPN_PASSWORD: [REDACTED]"
echo "  TEST_VPN_HOSTNAME: $TEST_VPN_HOSTNAME"
echo "  TEST_VPN_REGION: $TEST_VPN_REGION"
echo ""

# Run Maestro test
TEST_FILE=${1:-.maestro/01_test_full_config_flow.yaml}
echo "Running Maestro test: $TEST_FILE"
maestro test "$TEST_FILE"

