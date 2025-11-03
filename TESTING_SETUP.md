# Testing Setup Instructions

## Current Status

The Maestro E2E test infrastructure is fully configured, but the following tools need to be installed to run the tests:

## Required Prerequisites

### 1. Java Development Kit (JDK)
```bash
# On Arch Linux:
sudo pacman -S jdk-openjdk

# On Ubuntu/Debian:
sudo apt install default-jdk

# Verify installation:
java -version
```

### 2. Android SDK & ADB
```bash
# On Arch Linux:
sudo pacman -S android-tools android-sdk

# On Ubuntu/Debian:
sudo apt install android-tools-adb android-tools-fastboot

# Set ANDROID_HOME environment variable
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/platform-tools
export PATH=$PATH:$ANDROID_HOME/tools

# Verify ADB installation:
adb version
```

### 3. Gradle Wrapper
The Gradle wrapper files need to be generated. You can either:

**Option A: Install Gradle globally**
```bash
# On Arch Linux:
sudo pacman -S gradle

# On Ubuntu/Debian:
sudo apt install gradle

# Then create wrapper:
cd /home/pont/projects/multi-region-vpn
gradle wrapper
```

**Option B: Download Gradle wrapper manually**
```bash
cd /home/pont/projects/multi-region-vpn
mkdir -p gradle/wrapper
curl -L https://raw.githubusercontent.com/gradle/gradle/master/gradle/wrapper/gradle-wrapper.jar -o gradle/wrapper/gradle-wrapper.jar
chmod +x gradlew  # If gradlew file exists
```

### 4. Maestro CLI
```bash
curl -Ls "https://get.maestro.mobile.dev" | bash

# Add to PATH:
export PATH="$HOME/.maestro/bin:$PATH"

# Verify:
maestro --version
```

### 5. Android Device/Emulator
You need either:
- A physical Android device connected via USB with USB debugging enabled
- An Android emulator running

Check connected devices:
```bash
adb devices
```

## Building and Installing the App

Once prerequisites are installed:

```bash
cd /home/pont/projects/multi-region-vpn

# Build and install debug APK
./gradlew installDebug

# Or build release APK
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Running Maestro Tests

```bash
# Single test flow
maestro test .maestro/01_test_full_config_flow.yaml

# All tests
maestro test .maestro/

# With specific device
maestro test .maestro/01_test_full_config_flow.yaml --device <device-id>

# List available devices
adb devices
```

## Quick Setup Script (Arch Linux)

```bash
#!/bin/bash
# Install all prerequisites on Arch Linux

sudo pacman -S jdk-openjdk android-tools android-sdk gradle

# Install Maestro
curl -Ls "https://get.maestro.mobile.dev" | bash

# Add to ~/.bashrc or ~/.zshrc:
echo 'export PATH="$HOME/.maestro/bin:$PATH"' >> ~/.bashrc
echo 'export ANDROID_HOME=$HOME/Android/Sdk' >> ~/.bashrc
echo 'export PATH=$PATH:$ANDROID_HOME/platform-tools' >> ~/.bashrc

# Reload shell configuration
source ~/.bashrc

# Generate Gradle wrapper
cd /home/pont/projects/multi-region-vpn
gradle wrapper

# Build and install app
./gradlew installDebug

# Run tests
maestro test .maestro/01_test_full_config_flow.yaml
```

## Validating Test Configuration

You can validate the test YAML syntax without running it:

```bash
# If Maestro is installed:
maestro validate .maestro/01_test_full_config_flow.yaml
```

## Alternative: Run Tests in CI/CD

If local setup is challenging, consider running tests in:
- GitHub Actions (with Android emulator)
- GitLab CI
- CircleCI
- Other CI/CD platforms that support Android

See `.maestro/README.md` for CI/CD examples.

