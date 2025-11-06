#!/bin/bash
# Script to generate PKI certificates for OpenVPN test servers
# Uses Docker to run the kylemanna/openvpn image's PKI generation tools

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BASE_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "🔐 Generating PKI certificates for OpenVPN test servers..."

generate_pki() {
    local name=$1
    local subnet=$2
    local dir="$BASE_DIR/openvpn-$name"
    
    if [ ! -d "$dir" ]; then
        echo "❌ Directory not found: $dir"
        echo "   Run generate-server-configs.sh first"
        return 1
    fi
    
    echo "📝 Generating PKI for $name..."
    
    # Create pki directory if it doesn't exist
    mkdir -p "$dir/pki"
    
    # Use Docker to generate PKI
    docker run --rm -it \
        -v "$dir:/etc/openvpn" \
        -e EASYRSA_KEY_SIZE=2048 \
        -e EASYRSA_CA_EXPIRE=3650 \
        -e EASYRSA_CERT_EXPIRE=3650 \
        kylemanna/openvpn:latest \
        ovpn_genconfig -u "udp://vpn-server-$name" -N -d -s "$subnet/24" -p "route 10.0.0.0 255.0.0.0"
    
    # Initialize PKI (non-interactive)
    docker run --rm -it \
        -v "$dir:/etc/openvpn" \
        -e EASYRSA_BATCH=1 \
        kylemanna/openvpn:latest \
        ovpn_initpki nopass
    
    echo "✅ PKI generated for $name"
}

# Generate PKI for each server
generate_pki "uk" "10.1.0.0"
generate_pki "fr" "10.2.0.0"
generate_pki "dns" "10.3.0.0"
generate_pki "uk-conflict" "10.8.0.0"
generate_pki "fr-conflict" "10.8.0.0"

echo ""
echo "✅ All PKI certificates generated!"
echo ""
echo "📋 Next steps:"
echo "   1. Generate client certificates for testing (if needed)"
echo "   2. Verify Docker Compose volume mounts are correct"
echo "   3. Test Docker Compose environments"


