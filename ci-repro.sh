#!/usr/bin/env bash
set -euo pipefail
export ANDROID_HOME=${ANDROID_HOME:-$HOME/Android/Sdk}
export PATH="$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

echo "== Ensuring Android cmdline-tools and image =="
mkdir -p "$ANDROID_HOME"
if ! command -v sdkmanager >/dev/null 2>&1; then
  echo "Installing cmdline-tools..."
  tmp=$(mktemp -d)
  pushd "$tmp" >/dev/null
  curl -fsSL https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -o cmdline-tools.zip
  unzip -q cmdline-tools.zip
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  mv cmdline-tools "$ANDROID_HOME/cmdline-tools/latest"
  popd >/dev/null
fi
yes | sdkmanager --licenses >/dev/null || true
sdkmanager --install platform-tools emulator platforms
