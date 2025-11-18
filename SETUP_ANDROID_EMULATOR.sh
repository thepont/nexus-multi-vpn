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
    
    echo "✓ Android SDK installed at $ANDROID_HOME"
fi

SDKMANAGER_BIN="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"

if [[ ! -x "$SDKMANAGER_BIN" ]]; then
    echo "ERROR: sdkmanager not found at $SDKMANAGER_BIN"
    exit 1
fi

echo "Accepting Android SDK licenses..."
yes | "$SDKMANAGER_BIN" --licenses >/dev/null

echo "Installing required SDK packages..."
"$SDKMANAGER_BIN" \
    "platform-tools" \
    "platforms;android-34" \
    "build-tools;34.0.0" \
    "emulator" \
    "system-images;android-34;google_apis;x86_64" \
    "system-images;android-34;android-tv;x86"

# Create AVD
echo ""
echo "Creating Android Virtual Devices..."

create_avd_if_missing() {
    local name="$1"
    local package="$2"
    local device="$3"

    if [[ -d "$HOME/.android/avd/${name}.avd" ]]; then
        echo "  • AVD \"$name\" already exists, skipping creation."
        return
    fi

    echo "  • Creating \"$name\"..."
    printf "no\n" | avdmanager create avd -n "$name" -k "$package" -d "$device"
}

create_avd_if_missing "pixel_phone_api34" "system-images;android-34;google_apis;x86_64" "pixel_7"
create_avd_if_missing "google_tv_api34" "system-images;android-34;android-tv;x86" "tv_1080p"

echo ""
echo "========================================="
echo "Setup Complete!"
echo "========================================="
echo ""
echo "To start the emulators:"
echo "  export ANDROID_HOME=$ANDROID_HOME"
echo "  export PATH=\$PATH:\$ANDROID_HOME/emulator:\$ANDROID_HOME/platform-tools"
echo ""
echo "  # Phone"
echo "  emulator -avd pixel_phone_api34 -no-boot-anim -accel auto &"
echo ""
echo "  # Google TV"
echo "  emulator -avd google_tv_api34 -no-boot-anim -accel auto -gpu swiftshader_indirect &"
echo ""
echo "Once booted, you can target them via:"
echo "  adb -s emulator-5554 ...   # Phone"
echo "  adb -s emulator-5556 ...   # Google TV (port may vary, check with 'adb devices')"
echo ""
echo "For first-time setup (fresh emulator data recommended):"
echo "  # Phone emulator (wipe data once so device-owner command succeeds)"
echo "  emulator -avd pixel_phone_api34 -wipe-data -no-boot-anim -accel auto &"
echo "  adb wait-for-device"
echo "  adb shell dpm set-device-owner com.multiregionvpn/.deviceowner.TestDeviceOwnerReceiver"
echo ""
echo "  # (Optional) Google TV emulator device-owner"
echo "  emulator -avd google_tv_api34 -wipe-data -no-boot-anim -accel auto -gpu swiftshader_indirect &"
echo "  adb wait-for-device"
echo "  adb shell dpm set-device-owner com.multiregionvpn/.deviceowner.TestDeviceOwnerReceiver"
echo ""
echo "After that, instrumentation tests will auto-install the local CA via DevicePolicyManager."
echo ""
echo "Add to ~/.bashrc:"
echo "  export ANDROID_HOME=$ANDROID_HOME"
echo "  export PATH=\$PATH:\$ANDROID_HOME/emulator:\$ANDROID_HOME/platform-tools:\$ANDROID_HOME/cmdline-tools/latest/bin"


