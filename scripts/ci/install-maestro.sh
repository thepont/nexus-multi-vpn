#!/bin/bash
# Install Maestro CLI with pinned version for stability
set -e

MAESTRO_VERSION="${MAESTRO_VERSION:-1.39.0}"
INSTALL_DIR="$HOME/.maestro"
BIN_DIR="$INSTALL_DIR/bin"
MAESTRO_OS="linux"
ARCHIVE_NAME="maestro-${MAESTRO_OS}.zip"
DOWNLOAD_URL="https://github.com/mobile-dev-inc/maestro/releases/download/${MAESTRO_VERSION}/${ARCHIVE_NAME}"

echo "Installing Maestro ${MAESTRO_VERSION}..."
mkdir -p "$BIN_DIR"
TMP_DIR=$(mktemp -d)
curl -Ls "$DOWNLOAD_URL" -o "$TMP_DIR/$ARCHIVE_NAME"
unzip -o "$TMP_DIR/$ARCHIVE_NAME" -d "$TMP_DIR"
mv "$TMP_DIR/maestro" "$BIN_DIR/maestro"
chmod +x "$BIN_DIR/maestro"
rm -rf "$TMP_DIR"

echo "$BIN_DIR" >> "$GITHUB_PATH"
echo "Maestro $(\"$BIN_DIR/maestro\" --version) installed"
