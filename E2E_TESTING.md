# E2E Routing Test Suite

This document describes the comprehensive End-to-End (E2E) test suite that validates the core functionality of the Multi-Region VPN Router application.

## Overview

The `VpnRoutingTest` suite validates that the app correctly routes traffic based on app rules to:
1. **UK VPN Server** - Routes traffic through a UK VPN tunnel
2. **France VPN Server** - Routes traffic through a French VPN tunnel  
3. **Direct Internet** - Routes traffic directly (no VPN) when no rule exists

## Test Architecture

### Test Strategy

The tests use the **Android Test Runner package** (`com.multiregionvpn.test`) as the "app" to test routing. This is clever because:
- The test runner is itself an Android package that can make network requests
- We can create routing rules for our test package
- We can verify the IP location of test requests

### Key Components

#### 1. **IpCheckService** (`app/src/androidTest/java/com/multiregionvpn/IpCheckService.kt`)
   - Retrofit service that queries `ip-api.com` to determine the geographic location of the current IP
   - Returns `countryCode` (e.g., "GB", "FR", "US")

#### 2. **VpnRoutingTest** (`app/src/androidTest/java/com/multiregionvpn/VpnRoutingTest.kt`)
   - Main test class with three test methods:
     - `test_routesToUK()` - Verifies UK routing
     - `test_routesToFrance()` - Verifies FR routing
     - `test_routesToDirectInternet()` - Verifies direct routing

#### 3. **Test Assets** (`app/src/androidTest/assets/`)
   - `test_nord_credentials.json` - Contains NordVPN Service Credentials (username/password)
     - **⚠️ IMPORTANT:** You must fill this file with real credentials before running tests!

#### 4. **HiltTestApplication** (`app/src/androidTest/java/com/multiregionvpn/HiltTestApplication.kt`)
   - Custom Application class for Hilt dependency injection in tests
   - Required for `@HiltAndroidTest` annotation

## Test Flow

### Setup Phase (`@Before`)
1. Clears all existing test data from the database
2. Gets baseline IP location (before VPN starts)
3. Loads NordVPN credentials from test asset
4. Creates test VPN configurations for UK and FR servers

### Test Execution
Each test:
1. Creates an app rule routing `com.multiregionvpn.test` to a specific VPN (or none)
2. Starts the VPN service via UI automation (clicks the toggle, handles permission dialogs)
3. Waits 45 seconds for VPN to establish connection
4. Makes an HTTP request to `ip-api.com`
5. Asserts that the returned `countryCode` matches expected value

### Teardown Phase (`@After`)
1. Stops the VPN service
2. Cleans up all test data

## Setup Instructions

### 1. Add NordVPN Credentials

Edit `app/src/androidTest/assets/test_nord_credentials.json`:

```json
{
  "username": "your_actual_service_username",
  "password": "your_actual_service_password"
}
```

**Where to get credentials:**
- Go to: https://my.nordaccount.com/dashboard/nordvpn/manual-setup/
- Copy your **Service Credentials** (not the Access Token!)
- Paste into the JSON file above

### 2. Update Server Hostnames (Optional)

The test uses hardcoded server hostnames:
- UK: `uk1234.nordvpn.com`
- FR: `fr1234.nordvpn.com`

If these don't work, edit `VpnRoutingTest.kt` and update:
```kotlin
private val UK_SERVER_HOSTNAME = "uk1234.nordvpn.com"
private val FR_SERVER_HOSTNAME = "fr1234.nordvpn.com"
```

You can find valid server hostnames from NordVPN's server list API or by checking their documentation.

### 3. Run the Tests

```bash
# Ensure emulator is running and device is connected
adb devices

# Run all E2E tests
./gradlew connectedDebugAndroidTest

# Or run specific test class
./gradlew connectedDebugAndroidTest --tests com.multiregionvpn.VpnRoutingTest

# Or run a specific test method
./gradlew connectedDebugAndroidTest --tests com.multiregionvpn.VpnRoutingTest.test_routesToUK
```

## Expected Behavior

### Success Criteria

1. **`test_routesToUK()`**
   - IP location should be `"GB"` (United Kingdom)
   - Should NOT match baseline country

2. **`test_routesToFrance()`**
   - IP location should be `"FR"` (France)
   - Should NOT match baseline country

3. **`test_routesToDirectInternet()`**
   - IP location should match baseline country
   - Should NOT be `"GB"` or `"FR"`

### Test Output

The tests produce verbose console output:
```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📍 Baseline Country (Direct IP): US
📍 Baseline IP: 192.168.1.100
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

🧪 TEST: Routing to UK VPN
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✓ Created app rule: com.multiregionvpn.test -> UK VPN
🔌 Starting VPN engine...
   Waiting 45 seconds for VPN engine to boot and connect...
📍 Resulting IP: 185.xxx.xxx.xxx
📍 Resulting Country: GB
✅ TEST PASSED: Traffic successfully routed to UK
```

## Troubleshooting

### Test Fails with "Could not find VPN toggle"
- Ensure the app UI is visible on the emulator
- The test uses `testTag="start_service_toggle"` - verify this exists in `SettingsScreen.kt`

### Test Fails with "Traffic was not routed to UK"
- Verify NordVPN credentials are correct in `test_nord_credentials.json`
- Check that the server hostname is valid and accessible
- Increase wait time if VPN connection is slow (edit the `Thread.sleep(45000)` value)
- Verify that the VPN service actually started (check logs)

### Test Fails with "Country code mismatch"
- The VPN might not have fully connected yet - increase wait time
- The server hostname might be invalid - try a different server
- Network conditions might be blocking the VPN - check emulator network settings

### Hilt Injection Errors
- Ensure `HiltTestApplication` is properly referenced in `app/src/androidTest/AndroidManifest.xml`
- Verify Hilt dependencies are correctly added in `build.gradle.kts`

## Dependencies

The test suite requires:
- `com.squareup.retrofit2:retrofit:2.9.0`
- `com.squareup.moshi:moshi-kotlin:1.15.0`
- `com.squareup.retrofit2:converter-moshi:2.9.0`
- `com.google.dagger:hilt-android-testing:2.51.1`
- `androidx.test.uiautomator:uiautomator:2.3.0`

All dependencies are already configured in `app/build.gradle.kts`.

## Notes

- Tests take approximately **2-3 minutes each** due to VPN connection time
- The tests require an **active internet connection** and **Android emulator**
- VPN connections are **real** - you're actually connecting to NordVPN servers
- The test package name (`com.multiregionvpn.test`) is hardcoded and should match the test instrumentation package

