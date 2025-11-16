#!/bin/bash
# Install Maestro CLI
set -e

curl -Ls "https://get.maestro.mobile.dev" | bash
echo "$HOME/.maestro/bin" >> "$GITHUB_PATH"
