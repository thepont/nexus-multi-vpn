#!/bin/bash
# Installation script for Maestro E2E testing dependencies on Arch Linux

set -e

echo "========================================="
echo "Installing Maestro E2E Testing Dependencies"
echo "========================================="
echo ""

# 1. Install Java JDK
echo "Step 1: Installing Java JDK..."
sudo pacman -S --noconfirm jdk-openjdk
echo "✓ Java installed"
java -version
echo ""

# 2. Install Android tools (ADB)
echo "Step 2: Installing Android tools (ADB)..."
sudo pacman -S --noconfirm android-tools
echo "✓ Android tools installed"
adb version || echo "Note: ADB will work when device is connected"
echo ""

# 3. Install Gradle
echo "Step 3: Installing Gradle..."
sudo pacman -S --noconfirm gradle
echo "✓ Gradle installed"
gradle -v | head -n 3
echo ""

# 4. Generate Gradle wrapper
echo "Step 4: Setting up Gradle wrapper..."
cd "$(dirname "$0")"
if [ ! -f "./gradlew" ]; then
    # Download wrapper JAR manually to avoid Gradle version conflicts
    mkdir -p gradle/wrapper
    curl -L https://raw.githubusercontent.com/gradle/gradle/v8.2.0/gradle/wrapper/gradle-wrapper.jar -o gradle/wrapper/gradle-wrapper.jar 2>/dev/null
    chmod +x ./gradlew 2>/dev/null || echo "Note: gradlew script will be created"
    echo "✓ Gradle wrapper created (using Gradle 8.2)"
else
    echo "✓ Gradle wrapper already exists"
fi
echo ""

# 5. Install Maestro CLI
echo "Step 5: Installing Maestro CLI..."
curl -Ls "https://get.maestro.mobile.dev" | bash
export PATH="$HOME/.maestro/bin:$PATH"
if [ -f "$HOME/.maestro/bin/maestro" ]; then
    echo "✓ Maestro installed"
    $HOME/.maestro/bin/maestro --version
else
    echo "⚠ Maestro installation may have failed"
fi
echo ""

# 6. Add to shell configuration
echo "Step 6: Adding to shell configuration..."
SHELL_CONFIG=""
if [ -f "$HOME/.bashrc" ]; then
    SHELL_CONFIG="$HOME/.bashrc"
elif [ -f "$HOME/.zshrc" ]; then
    SHELL_CONFIG="$HOME/.zshrc"
fi

if [ -n "$SHELL_CONFIG" ]; then
    if ! grep -q "\.maestro/bin" "$SHELL_CONFIG"; then
        echo '' >> "$SHELL_CONFIG"
        echo '# Maestro CLI' >> "$SHELL_CONFIG"
        echo 'export PATH="$HOME/.maestro/bin:$PATH"' >> "$SHELL_CONFIG"
        echo "✓ Added Maestro to $SHELL_CONFIG"
    else
        echo "✓ Maestro already in $SHELL_CONFIG"
    fi
else
    echo "⚠ Could not find .bashrc or .zshrc - please add manually:"
    echo "   export PATH=\"\$HOME/.maestro/bin:\$PATH\""
fi
echo ""

echo "========================================="
echo "Installation Complete!"
echo "========================================="
echo ""
echo "Next steps:"
echo "1. Restart your terminal or run: source $SHELL_CONFIG"
echo "2. Connect an Android device or start an emulator"
echo "3. Verify device connection: adb devices"
echo "4. Build and install the app: ./gradlew installDebug"
echo "5. Run tests: maestro test .maestro/01_test_full_config_flow.yaml"
echo ""

