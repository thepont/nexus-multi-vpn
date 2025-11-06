#!/bin/bash
# Script to install Android SDK and create an emulator

set -e

echo "========================================="
echo "Setting up Android Emulator"
echo "========================================="
echo ""

# Option 1: Install Android SDK via pacman (if available)
if pacman -Si android-sdk >/dev/null 2>&1; then
    echo "Installing Android SDK from Arch repositories..."
    sudo pacman -S --noconfirm android-sdk android-sdk-platform-tools
    export ANDROID_HOME=/opt/android-sdk
    export PATH=$PATH:$ANDROID_HOME/tools:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator
else
    echo "Android SDK not in repos. Installing via command-line tools..."
    
    # Install command-line tools
    SDK_DIR="$HOME/Android/Sdk"
    mkdir -p "$SDK_DIR"
    
    echo "Downloading Android command-line tools..."
    cd /tmp
    curl -L https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -o cmdline-tools.zip
    unzip -q cmdline-tools.zip
    
    mkdir -p "$SDK_DIR/cmdline-tools"
    mv cmdline-tools "$SDK_DIR/cmdline-tools/latest"
    
    export ANDROID_HOME="$SDK_DIR"
    export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator
    
    # Accept licenses and install packages
    echo "Installing Android SDK packages..."
    yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses
    "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "platform-tools" "platforms;android-34" "build-tools;34.0.0" "emulator" "system-images;android-34;google_apis;x86_64"
    
    echo "✓ Android SDK installed at $ANDROID_HOME"
fi

# Create AVD
echo ""
echo "Creating Android Virtual Device..."
avdmanager create avd -n test_device -k "system-images;android-34;google_apis;x86_64" -d "pixel_5"

echo ""
echo "========================================="
echo "Setup Complete!"
echo "========================================="
echo ""
echo "To start the emulator:"
echo "  export ANDROID_HOME=$ANDROID_HOME"
echo "  export PATH=\$PATH:\$ANDROID_HOME/emulator"
echo "  emulator -avd test_device &"
echo ""
echo "Add to ~/.bashrc:"
echo "  export ANDROID_HOME=$ANDROID_HOME"
echo "  export PATH=\$PATH:\$ANDROID_HOME/emulator:\$ANDROID_HOME/platform-tools:\$ANDROID_HOME/cmdline-tools/latest/bin"


