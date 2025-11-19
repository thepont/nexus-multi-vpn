#!/bin/bash
# Install Maestro CLI with pinned version for stability
set -e

MAESTRO_VERSION="${MAESTRO_VERSION:-1.39.13}"
INSTALL_DIR="$HOME/.maestro"
BIN_DIR="$INSTALL_DIR/bin"

echo "Installing Maestro ${MAESTRO_VERSION}..."
mkdir -p "$BIN_DIR"

# Use official Maestro installer script with version pinning
curl -Ls "https://get.maestro.mobile.dev" | bash -s -- --version "$MAESTRO_VERSION"

# Ensure maestro is in PATH
if [ -f "$HOME/.maestro/bin/maestro" ]; then
  echo "$HOME/.maestro/bin" >> "$GITHUB_PATH"
  echo "Maestro $($HOME/.maestro/bin/maestro --version) installed"
elif [ -f "/usr/local/bin/maestro" ]; then
  echo "Maestro $(/usr/local/bin/maestro --version) installed (system-wide)"
else
  echo "⚠️  Warning: Could not find maestro binary after installation"
  exit 1
fi
