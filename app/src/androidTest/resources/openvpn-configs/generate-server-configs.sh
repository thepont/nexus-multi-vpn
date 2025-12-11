#!/bin/bash
# Script to generate OpenVPN server configurations for test environments
# This creates the necessary configs and certificates for Docker Compose test environments

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BASE_DIR="$SCRIPT_DIR/.."

echo "🔧 Generating OpenVPN server configurations for test environments..."

# Function to generate server config
generate_server_config() {
    local name=$1
    local subnet=$2
    local port=$3
    local dir="$BASE_DIR/openvpn-$name"
    
    echo "📝 Generating config for $name (subnet: $subnet, port: $port)..."
    
    mkdir -p "$dir/pki"
    
    # Create server.conf
    cat > "$dir/server.conf" <<EOF
port $port
proto udp
dev tun
server $subnet 255.255.255.0
dh /etc/openvpn/pki/dh.pem
ca /etc/openvpn/pki/ca.crt
cert /etc/openvpn/pki/server.crt
key /etc/openvpn/pki/server.key
keepalive 10 120
verb 3
user nobody
group nogroup
persist-key
persist-tun
status /var/log/openvpn-status.log
log /var/log/openvpn.log
EOF
    
    # Add DNS push for DNS test server
    if [ "$name" = "dns" ]; then
        echo 'push "dhcp-option DNS 10.3.0.2"' >> "$dir/server.conf"
        echo "✓ Added DNS push option for DNS test server"
    fi
    
    # Generate basic PKI (using easy-rsa would be better, but for testing we'll create minimal certs)
    echo "⚠️  Note: PKI certificates need to be generated separately"
    echo "   You can use easy-rsa or the kylemanna/openvpn image's built-in tools"
    echo "   For testing, you may need to:"
    echo "   1. Run a temporary OpenVPN container to generate certificates"
    echo "   2. Or use a certificate generation script"
    
    echo "✅ Config generated in: $dir"
}

# Generate configurations
echo ""
generate_server_config "uk" "172.31.1.0" "1194"
generate_server_config "fr" "172.31.2.0" "1194"
generate_server_config "dns" "10.3.0.0" "1194"
generate_server_config "uk-conflict" "10.8.0.0" "1194"
generate_server_config "fr-conflict" "10.8.0.0" "1194"

echo ""
echo "✅ All server configurations generated!"
echo ""
echo "📋 Next steps:"
echo "   1. Generate PKI certificates for each server"
echo "   2. Create client certificates for testing"
echo "   3. Update Docker Compose volume paths if needed"
echo ""
echo "💡 Tip: Use the kylemanna/openvpn image's ovpn_genconfig and ovpn_initpki commands"


