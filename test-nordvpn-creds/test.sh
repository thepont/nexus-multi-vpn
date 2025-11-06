#!/bin/bash

# Convenience script to test NordVPN credentials

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Load credentials from .env file
if [ -f "$PROJECT_ROOT/.env" ]; then
    echo "📁 Loading credentials from .env file..."
    # Use source to properly handle values without quotes
    # This preserves exact values including any special characters
    set -a  # automatically export all variables
    source "$PROJECT_ROOT/.env"
    set +a
else
    echo "❌ .env file not found at $PROJECT_ROOT/.env"
    exit 1
fi

# Check if credentials are set
if [ -z "$NORDVPN_USERNAME" ] || [ -z "$NORDVPN_PASSWORD" ]; then
    echo "❌ Error: NORDVPN_USERNAME and NORDVPN_PASSWORD not found in .env"
    exit 1
fi

echo "✅ Credentials loaded"
echo ""

# Build Docker image if needed
if ! docker images | grep -q "nordvpn-test"; then
    echo "🔨 Building Docker image..."
    docker build -t nordvpn-test "$SCRIPT_DIR"
    echo ""
fi

# Run the test
# Note: We use --network bridge (default) so the container can download configs
# But we use --dev null in OpenVPN so it won't affect host network
echo "🚀 Running credential test..."
echo "   (Container can access internet for config download, but won't affect host network)"
docker run --rm \
  -e NORDVPN_USERNAME="$NORDVPN_USERNAME" \
  -e NORDVPN_PASSWORD="$NORDVPN_PASSWORD" \
  --cap-add=NET_ADMIN \
  nordvpn-test

