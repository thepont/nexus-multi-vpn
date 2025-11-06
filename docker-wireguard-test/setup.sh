#!/bin/bash

set -e

echo "🚀 Setting up WireGuard test environment..."

# Create directories
mkdir -p web-uk web-fr wireguard-uk wireguard-fr

# Create mock web server responses
echo '{"country": "GB", "countryCode": "GB", "region": "England", "city": "London", "timezone": "Europe/London", "query": "172.25.0.11"}' > web-uk/index.html
echo '{"country": "France", "countryCode": "FR", "region": "Île-de-France", "city": "Paris", "timezone": "Europe/Paris", "query": "172.25.0.21"}' > web-fr/index.html

echo "📦 Starting Docker containers..."
docker-compose up -d

echo "⏳ Waiting for WireGuard servers to initialize (30 seconds)..."
sleep 30

echo ""
echo "✅ WireGuard test environment ready!"
echo ""
echo "🔑 Client configurations:"
echo "   UK:     docker-wireguard-test/wireguard-uk/peer_android_client/peer_android_client.conf"
echo "   France: docker-wireguard-test/wireguard-fr/peer_android_client/peer_android_client.conf"
echo ""
echo "🌐 Test endpoints:"
echo "   UK web:     http://172.25.0.11"
echo "   France web: http://172.25.0.21"
echo ""
echo "📝 Next steps:"
echo "   1. Copy client configs to app/src/androidTest/assets/"
echo "   2. Update E2E tests to use WireGuard configs"
echo "   3. Run tests: ./gradlew :app:connectedDebugAndroidTest"
echo ""

# Display UK config
if [ -f "wireguard-uk/peer_android_client/peer_android_client.conf" ]; then
    echo "📋 UK Client Config:"
    cat wireguard-uk/peer_android_client/peer_android_client.conf
    echo ""
fi

# Display FR config
if [ -f "wireguard-fr/peer_android_client/peer_android_client.conf" ]; then
    echo "📋 France Client Config:"
    cat wireguard-fr/peer_android_client/peer_android_client.conf
    echo ""
fi

