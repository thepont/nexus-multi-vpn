# Quick Start: Running Maestro Tests

## ✅ Prerequisites Installed

You've successfully installed:
- ✓ Java JDK
- ✓ Android tools (ADB)
- ✓ Gradle wrapper (Gradle 8.2)

## Next Steps to Run Tests

### 1. Install Maestro (if not already done)

```bash
curl -Ls "https://get.maestro.mobile.dev" | bash
export PATH="$HOME/.maestro/bin:$PATH"
```

Add to your shell config:
```bash
echo 'export PATH="$HOME/.maestro/bin:$PATH"' >> ~/.bashrc
source ~/.bashrc
```

### 2. Connect Android Device or Start Emulator

**Option A: Physical Device**
- Enable USB debugging on your Android device
- Connect via USB
- Verify connection:
```bash
adb devices
```

**Option B: Android Emulator**
- Start an emulator from Android Studio, or
- Use command line:
```bash
emulator -avd <avd_name> &
```

Verify device is connected:
```bash
adb devices
# Should show your device/emulator
```

### 3. Build and Install the App

```bash
cd /home/pont/projects/multi-region-vpn

# Build and install debug APK
./gradlew installDebug
```

### 4. Run Maestro Tests

```bash
# Make sure Maestro is in PATH
export PATH="$HOME/.maestro/bin:$PATH"

# Run the full configuration flow test
maestro test .maestro/01_test_full_config_flow.yaml
```

## Troubleshooting

### "Maestro: command not found"
```bash
export PATH="$HOME/.maestro/bin:$PATH"
# Or add to ~/.bashrc permanently
```

### "No devices/emulators found"
```bash
adb devices
# If empty, start an emulator or connect a device
```

### "App not installed"
```bash
./gradlew installDebug
# Make sure device is connected first
```

### Build Errors
The project uses Gradle 8.2 (via wrapper). If you see build errors:
```bash
./gradlew clean
./gradlew build
```

## Test Flow Overview

The test (`01_test_full_config_flow.yaml`) will:
1. Launch the app
2. Enter NordVPN token
3. Add a VPN server config
4. Assign routing rule to an app
5. Toggle VPN service on/off
6. Handle system permission dialogs

**Note:** Before running, you may want to update the test token in the YAML file:
```yaml
- inputText: "YOUR_ACTUAL_TEST_TOKEN_HERE"
```

## Success!

Once the test completes successfully, you'll see output like:
```
✅ Test passed
All assertions passed
```

