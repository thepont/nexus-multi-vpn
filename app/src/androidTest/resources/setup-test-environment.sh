#!/bin/bash
# Comprehensive setup script for test environment
# This script sets up Docker Compose environments, OpenVPN configs, and prepares test infrastructure

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../../../.." && pwd)"

echo "🔧 Setting up test environment for multi-region VPN router..."
echo ""

# Check prerequisites
echo "📋 Checking prerequisites..."

if ! command -v docker &> /dev/null; then
    echo "❌ Docker is not installed. Please install Docker first."
    exit 1
fi
echo "✓ Docker installed"

if ! command -v docker-compose &> /dev/null; then
    echo "❌ docker-compose is not installed. Please install docker-compose first."
    exit 1
fi
echo "✓ docker-compose installed"

# Create necessary directories
echo ""
echo "📁 Creating directories..."
mkdir -p "$PROJECT_ROOT/openvpn-uk"
mkdir -p "$PROJECT_ROOT/openvpn-fr"
mkdir -p "$PROJECT_ROOT/openvpn-dns"
mkdir -p "$PROJECT_ROOT/openvpn-uk-conflict"
mkdir -p "$PROJECT_ROOT/openvpn-fr-conflict"
mkdir -p "$PROJECT_ROOT/http-uk"
mkdir -p "$PROJECT_ROOT/http-fr"
mkdir -p "$PROJECT_ROOT/http-dns"
mkdir -p "$PROJECT_ROOT/http-uk-conflict"
mkdir -p "$PROJECT_ROOT/http-fr-conflict"
mkdir -p "$PROJECT_ROOT/openvpn-dns-domain"
mkdir -p "$PROJECT_ROOT/http-dns-domain"
echo "✓ Directories created"

# Generate OpenVPN server configs
echo ""
echo "📝 Generating OpenVPN server configurations..."
if [ -f "$SCRIPT_DIR/openvpn-configs/generate-server-configs.sh" ]; then
    bash "$SCRIPT_DIR/openvpn-configs/generate-server-configs.sh"
else
    echo "⚠️  generate-server-configs.sh not found, skipping..."
fi

# Generate PKI certificates
echo ""
echo "🔐 Generating PKI certificates..."
if [ -f "$SCRIPT_DIR/openvpn-configs/generate-pki.sh" ]; then
    read -p "Generate PKI certificates now? (requires Docker) [y/N]: " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        bash "$SCRIPT_DIR/openvpn-configs/generate-pki.sh"
    else
        echo "⚠️  Skipping PKI generation. Run generate-pki.sh manually later."
    fi
else
    echo "⚠️  generate-pki.sh not found, skipping..."
fi

# Create HTTP server content
echo ""
echo "🌐 Creating HTTP server content..."
echo "SERVER_UK" > "$PROJECT_ROOT/http-uk/index.html"
echo "SERVER_FR" > "$PROJECT_ROOT/http-fr/index.html"
echo "DNS_TEST_PASSED" > "$PROJECT_ROOT/http-dns/index.html"
echo "SERVER_UK" > "$PROJECT_ROOT/http-uk-conflict/index.html"
echo "SERVER_FR" > "$PROJECT_ROOT/http-fr-conflict/index.html"
echo "✓ HTTP server content created"

# Verify Docker Compose files
echo ""
echo "✅ Docker Compose files:"
ls -1 "$SCRIPT_DIR/docker-compose/"*.yaml | while read file; do
    echo "   - $(basename "$file")"
done

echo ""
echo "✅ Test environment setup complete!"
echo ""
echo "📋 Next steps:"
echo "   1. Generate PKI certificates (if not done):"
echo "      bash app/src/androidTest/resources/openvpn-configs/generate-pki.sh"
echo ""
echo "   2. Build test apps (if needed):"
echo "      See: app/src/androidTest/resources/test-apps/README.md"
echo ""
echo "   3. Run tests:"
echo "      ./gradlew :app:connectedAndroidTest"
echo "      # Or run individual tests:"
echo "      adb shell am instrument -w -e class com.multiregionvpn.LocalRoutingTest \\"
echo "        com.multiregionvpn.test/androidx.test.runner.AndroidJUnitRunner"


