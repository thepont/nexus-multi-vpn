#!/bin/bash
# Script to clone and patch ics-openvpn for use as a library module

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
LIB_DIR="$PROJECT_ROOT/libs"
ICSOpenVPN_DIR="$LIB_DIR/ics-openvpn"
ICSOpenVPN_VERSION="${ICSOpenVPN_VERSION:-v0.7.24}"
ICSOpenVPN_REPO="https://github.com/schwabe/ics-openvpn.git"

echo "=========================================="
echo "Setting up ics-openvpn library module"
echo "=========================================="
echo "Version: $ICSOpenVPN_VERSION"
echo "Target: $ICSOpenVPN_DIR"
echo ""

# Create libs directory if it doesn't exist
mkdir -p "$LIB_DIR"

# Clone or update the repository
if [ -d "$ICSOpenVPN_DIR" ]; then
    echo "Repository already exists. Updating..."
    cd "$ICSOpenVPN_DIR"
    git fetch origin
    git checkout "$ICSOpenVPN_VERSION" 2>/dev/null || {
        echo "Warning: Could not checkout $ICSOpenVPN_VERSION, using current branch"
    }
else
    echo "Cloning ics-openvpn repository..."
    git clone --depth 1 --branch "$ICSOpenVPN_VERSION" "$ICSOpenVPN_REPO" "$ICSOpenVPN_DIR" || {
        echo "Failed to clone with branch, trying tag..."
        git clone --depth 1 "$ICSOpenVPN_REPO" "$ICSOpenVPN_DIR"
        cd "$ICSOpenVPN_DIR"
        git checkout "$ICSOpenVPN_VERSION" || {
            echo "Warning: Could not checkout $ICSOpenVPN_VERSION tag/branch"
        }
    }
fi

cd "$ICSOpenVPN_DIR"

MAIN_MODULE="$ICSOpenVPN_DIR/main"
BUILD_FILE="$MAIN_MODULE/build.gradle.kts"

if [ ! -f "$BUILD_FILE" ]; then
    echo "Error: build.gradle.kts not found at $BUILD_FILE"
    exit 1
fi

echo ""
echo "Applying patches to convert application module to library..."

# Run the patch script
PATCH_SCRIPT="$PROJECT_ROOT/scripts/patch-ics-openvpn.sh"
if [ -f "$PATCH_SCRIPT" ]; then
    echo "Running patch script..."
    bash "$PATCH_SCRIPT"
else
    echo "Warning: Patch script not found at $PATCH_SCRIPT"
    echo "Skipping automatic patching. Please run scripts/patch-ics-openvpn.sh manually."
fi

echo "=========================================="
echo "Setup complete!"
echo "=========================================="
echo ""
echo "The ics-openvpn module has been set up at:"
echo "  $ICSOpenVPN_DIR/main"
echo ""
echo "You may need to manually verify the build.gradle.kts file matches"
echo "your project's requirements. The original file is backed up at:"
echo "  $BUILD_FILE.original"
echo ""

