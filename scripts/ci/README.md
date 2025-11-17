# CI Scripts Documentation

This directory contains shell scripts used by GitHub Actions workflows. These scripts were extracted from inline YAML blocks to improve maintainability and reusability.

## Script Overview

### C++ Build Scripts

#### `install-cpp-build-deps.sh`
Installs C++ build dependencies for unit tests.
- Installs: cmake, build-essential
- Shows installed versions

#### `configure-cpp-tests.sh`
Configures C++ unit tests using CMake.
- Creates build directory
- Runs CMake configuration

#### `build-cpp-tests.sh`
Builds C++ unit tests using Make.
- Compiles tests in parallel (4 jobs)

#### `run-cpp-tests.sh`
Runs C++ unit tests using CTest.
- Verbose output
- Fails on first error

### Android Build Scripts

#### `install-android-build-deps.sh`
Installs Android build dependencies.
- Checks for existing tools (cmake, ninja, make)
- Installs missing dependencies
- Shows installed versions and SDK components

#### `download-gradle-dependencies.sh`
Downloads Gradle dependencies with stall monitoring.
- Creates background monitoring process
- Downloads dependencies for multiple configurations
- Detects stalled downloads and fails fast
- Shows summary and cache size

### Test Scripts

#### `run-robolectric-tests.sh`
Runs Robolectric unit tests with monitoring.
- Creates background test monitoring process
- Runs tests with detailed logging
- Shows test summary and skipped tests
- Detects stalled tests

#### `run-instrumentation-tests.sh`
Runs instrumentation tests on Android emulator.
- Runs connected Android tests
- Shows test summary
- Designed to run inside emulator-runner action

### Emulator Scripts

#### `enable-kvm.sh`
Enables KVM for Android emulator.
- Sets up udev rules
- Reloads udev configuration

### Maestro E2E Scripts

#### `evaluate-maestro-preconditions.sh`
Evaluates whether Maestro E2E tests should run.
- Checks for workflow_dispatch event
- Checks for NordVPN credentials
- Sets GitHub Actions outputs

#### `publish-maestro-summary.sh`
Publishes Maestro test summary to GitHub step summary.
- Shows whether tests ran or were skipped
- Shows credential status

#### `validate-nordvpn-credentials.sh`
Validates NordVPN credentials for E2E tests.
- Checks for required credentials
- Sets dummy credentials if allowed
- Fails if credentials missing and not allowed

#### `install-maestro.sh`
Installs Maestro CLI.
- Downloads and installs Maestro
- Adds to PATH

#### `generate-env-file.sh`
Generates .env file for Maestro tests.
- Creates .env with VPN credentials and test configuration

#### `build-debug-apk.sh`
Builds debug APK with native libraries.
- Includes OpenVPN native libraries
- Shows build status and cache info

#### `run-maestro-tests.sh`
Runs Maestro E2E tests in emulator.
- Waits for emulator to be ready
- Installs APK with retry logic
- Runs Maestro tests with single retry

### Release Build Scripts

#### `build-release-apk.sh`
Builds release APK with native libraries.
- Includes all supported ABIs
- Shows build information

#### `generate-release-summary.sh`
Generates release build summary for GitHub Actions.
- Shows APK details (size, path, architectures)
- Adds to GitHub step summary

## Usage in Workflows

These scripts are called from GitHub Actions workflows:
- `.github/workflows/android-ci.yml`
- `.github/workflows/maestro-e2e.yml`

Example:
```yaml
- name: Install Build Dependencies
  run: ./scripts/ci/install-android-build-deps.sh
```

## Environment Variables

Many scripts expect certain environment variables to be set:

- `CI`: Indicates running in CI environment
- `SKIP_NATIVE_BUILD`: Skip native C++ builds
- `ENABLE_NATIVE_BUILD`: Enable native C++ builds
- `NORDVPN_USERNAME`: NordVPN username for E2E tests
- `NORDVPN_PASSWORD`: NordVPN password for E2E tests
- `ALLOW_EMPTY_CREDENTIALS`: Allow running without credentials
- `GITHUB_OUTPUT`: GitHub Actions output file path
- `GITHUB_STEP_SUMMARY`: GitHub Actions step summary file path
- `GITHUB_ENV`: GitHub Actions environment file path
- `ANDROID_HOME`: Android SDK home directory

## Error Handling

All scripts use `set -e` to exit on error unless specifically handled otherwise (e.g., monitoring scripts).

## Monitoring Scripts

Several scripts create temporary monitoring processes:
- `download-gradle-dependencies.sh`: Monitors dependency download progress
- `run-robolectric-tests.sh`: Monitors test execution

These monitors detect stalled processes and fail fast if no progress is made for 2 minutes (4 × 30-second intervals).

## Maintenance

When updating workflows:
1. Modify the appropriate script in `scripts/ci/`
2. Ensure script is executable: `chmod +x scripts/ci/script-name.sh`
3. Test locally if possible
4. Update this README if adding new scripts
