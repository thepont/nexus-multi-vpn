#!/bin/bash
# Script to clone OpenVPN 3 C++ library for native integration

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
LIB_DIR="$PROJECT_ROOT/libs"
OPENVPN3_DIR="$LIB_DIR/openvpn3"
OPENVPN3_VERSION="${OPENVPN3_VERSION:-master}"
OPENVPN3_REPO="https://github.com/OpenVPN/openvpn3.git"

echo "=========================================="
echo "Setting up OpenVPN 3 C++ library"
echo "=========================================="
echo "Version: $OPENVPN3_VERSION"
echo "Target: $OPENVPN3_DIR"
echo ""

# Create libs directory if it doesn't exist
mkdir -p "$LIB_DIR"

# Clone or update the repository
if [ -d "$OPENVPN3_DIR" ]; then
    echo "Repository already exists. Updating..."
    cd "$OPENVPN3_DIR"
    git fetch origin
    git checkout "$OPENVPN3_VERSION" 2>/dev/null || {
        echo "Warning: Could not checkout $OPENVPN3_VERSION, using current branch"
    }
    git pull origin "$OPENVPN3_VERSION" 2>/dev/null || {
        echo "Warning: Could not pull $OPENVPN3_VERSION"
    }
else
    echo "Cloning OpenVPN 3 repository..."
    git clone --depth 1 --branch "$OPENVPN3_VERSION" "$OPENVPN3_REPO" "$OPENVPN3_DIR" || {
        echo "Failed to clone with branch, trying without branch specification..."
        git clone --depth 1 "$OPENVPN3_REPO" "$OPENVPN3_DIR"
        cd "$OPENVPN3_DIR"
        git checkout "$OPENVPN3_VERSION" || {
            echo "Warning: Could not checkout $OPENVPN3_VERSION tag/branch"
        }
    }
fi

cd "$OPENVPN3_DIR"

# Verify CMakeLists.txt exists
if [ ! -f "CMakeLists.txt" ]; then
    echo "Error: CMakeLists.txt not found at $OPENVPN3_DIR"
    exit 1
fi

echo ""
echo "=========================================="
echo "Setup complete!"
echo "=========================================="
echo ""
echo "OpenVPN 3 C++ library has been set up at:"
echo "  $OPENVPN3_DIR"
echo ""
echo "The library will be automatically detected by CMake during build."
echo ""

