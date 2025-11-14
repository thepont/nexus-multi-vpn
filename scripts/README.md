# E2E Test Scripts

This directory contains scripts for running End-to-End (E2E) tests for the Multi-Region VPN app.

## Quick Start

### Prerequisites

1. **Android Device/Emulator**: Connect a device or start an emulator
   ```bash
   emulator -avd <your_avd_name>
   ```

2. **NordVPN Credentials**: Create a `.env` file in the project root:
   ```bash
   NORDVPN_USERNAME=your_username
   NORDVPN_PASSWORD=your_password
   ```

3. **Environment Variables** (optional - script will try to auto-detect):
   - `ANDROID_HOME` or `ANDROID_SDK_ROOT` - Android SDK location
   - `ANDROID_NDK` - Android NDK location (auto-detected if not set)
   - `VCPKG_ROOT` - vcpkg location (auto-detected if not set)
   - `JAVA_HOME` - Java 17 location (auto-detected if not set)

### Basic Usage

```bash
# Run all E2E tests
./scripts/run-e2e-tests.sh

# Run a specific test method
./scripts/run-e2e-tests.sh --test-method test_routesToUK

# Run with app data clearing and log display
./scripts/run-e2e-tests.sh --clear-data --show-logs
```

## Main Script: `run-e2e-tests.sh`

A comprehensive test runner that:

1. ✅ Checks prerequisites (adb, device, .env file)
2. ✅ Sets up environment variables (Android SDK, NDK, Java, vcpkg)
3. ✅ Builds and installs the debug APK
4. ✅ Grants all necessary permissions:
   - Runtime permissions via `GrantPermissionRule` (INTERNET, ACCESS_NETWORK_STATE)
   - VPN permission via `appops set ... ACTIVATE_VPN allow` (REQUIRED)
5. ✅ Optionally clears app data for clean test state
6. ✅ Runs the E2E tests with proper timeout and error handling
7. ✅ Optionally displays relevant logs after tests

### Options

```bash
./scripts/run-e2e-tests.sh [OPTIONS]

Options:
  --test-class CLASS       Run specific test class (default: VpnRoutingTest)
  --test-method METHOD     Run specific test method (requires --test-class)
  --skip-install           Skip app installation
  --skip-permissions       Skip permission granting
  --clear-data             Clear app data before running tests
  --show-logs              Show logs after tests complete
  --help                   Show this help message
```

### Examples

```bash
# Run all tests in VpnRoutingTest
./scripts/run-e2e-tests.sh

# Run a single test method
./scripts/run-e2e-tests.sh --test-method test_routesToUK

# Run a different test class
./scripts/run-e2e-tests.sh --test-class VpnToggleTest

# Clear app data and show logs
./scripts/run-e2e-tests.sh --clear-data --show-logs

# Skip installation (app already installed)
./scripts/run-e2e-tests.sh --skip-install

# Skip permission granting (permissions already granted)
./scripts/run-e2e-tests.sh --skip-permissions
```

### Environment Variables

You can also use environment variables instead of flags:

```bash
# Clear app data
CLEAR_APP_DATA=true ./scripts/run-e2e-tests.sh

# Show logs
SHOW_LOGS=true ./scripts/run-e2e-tests.sh
```

## What the Script Does

### 1. Prerequisites Check
- Verifies `adb` is installed and accessible
- Checks that a device/emulator is connected
- Validates `.env` file exists and contains credentials

### 2. Environment Setup
- Auto-detects Android SDK location (`$HOME/Android/Sdk`)
- Auto-detects NDK version (latest in `$ANDROID_HOME/ndk`)
- Auto-detects vcpkg (`$HOME/vcpkg`)
- Auto-detects Java 17 (`/usr/lib/jvm/java-17*`)
- Sets up `PATH` for Java

### 3. App Installation
- Builds the debug APK
- Installs it on the connected device/emulator

### 4. Permission Granting

#### Runtime Permissions
- `INTERNET` - Granted automatically by `GrantPermissionRule` in tests
- `ACCESS_NETWORK_STATE` - Granted automatically by `GrantPermissionRule` in tests

#### VPN Permission (Special)
- **Pre-approved via `appops`**: `adb shell appops set com.multiregionvpn ACTIVATE_VPN allow`
- This makes `VpnService.prepare()` return `null` immediately (permission already granted)
- **Critical**: Without this, the VPN permission dialog will appear and tests will fail

### 5. Test Execution
- Clears logcat
- Runs tests with 2-minute timeout
- Passes NordVPN credentials via instrumentation arguments
- Captures test results

### 6. Log Display (Optional)
- Shows relevant logs from:
  - `VpnEngineService`
  - `VpnConnectionManager`
  - `NativeOpenVpnClient`
  - Test output

## CI/CD Integration

For CI/CD pipelines, add this to your test setup:

```bash
#!/bin/bash
set -e

# Start emulator (if not already running)
emulator -avd test_avd -no-window -no-audio &

# Wait for emulator to be ready
adb wait-for-device
adb shell 'while [[ -z $(getprop sys.boot_completed) ]]; do sleep 1; done'

# Run tests
./scripts/run-e2e-tests.sh --test-method test_routesToUK
```

## Troubleshooting

### "No Android device/emulator connected"
- Start an emulator: `emulator -avd <avd_name>`
- Or connect a physical device via USB (with USB debugging enabled)

### "VPN permission still not granted"
- The script should handle this automatically via `appops`
- If it fails, manually run: `adb shell appops set com.multiregionvpn ACTIVATE_VPN allow`
- Verify: `adb shell appops get com.multiregionvpn ACTIVATE_VPN` should return `allow`

### "Unable to resolve host downloads.nordcdn.com"
- The emulator/device has internet connectivity
- However, when VPN interface is established, DNS resolution may fail
- This is expected if VPN interface is active but VPN connection hasn't been established yet
- **Workaround**: Ensure VPN interface is configured to allow DNS resolution before connecting

### "Tests timeout"
- Increase timeout in script (currently 120 seconds)
- Check if VPN servers are reachable
- Verify NordVPN credentials are correct

## Setup Scripts

### `setup-openvpn3.sh`

Clones the OpenVPN 3 C++ library required for native OpenVPN integration.

**Usage**:
```bash
./scripts/setup-openvpn3.sh
```

**What it does**:
- Clones OpenVPN 3 from `https://github.com/OpenVPN/openvpn3.git` into `libs/openvpn3`
- Uses the `master` branch by default (can be overridden with `OPENVPN3_VERSION` environment variable)
- Updates the repository if it already exists
- Verifies that `CMakeLists.txt` exists after cloning

**Required for**: Building the native OpenVPN 3 integration

### `setup-ics-openvpn.sh`

Clones and patches the ics-openvpn library for use as a Gradle module.

**Usage**:
```bash
./scripts/setup-ics-openvpn.sh
```

## Related Files

- `load-env.sh` - Loads environment variables from `.env`
- `run-tests-with-env.sh` - Legacy test runner (use `run-e2e-tests.sh` instead)
- `setup-openvpn3.sh` - Sets up OpenVPN 3 C++ library
- `setup-ics-openvpn.sh` - Sets up ics-openvpn library

## Notes

- The script uses colored output for better readability
- All steps are logged with clear success/error indicators
- The script exits on any error (`set -e`)
- VPN permission is the most critical permission - it must be granted via `appops` for tests to work


