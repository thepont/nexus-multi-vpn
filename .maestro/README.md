# Maestro E2E Tests

This directory contains End-to-End (E2E) test flows for the Multi-Region VPN app using [Maestro](https://maestro.mobile.dev/).

## Setup

1. **Install Maestro CLI:**
   ```bash
   curl -Ls "https://get.maestro.mobile.dev" | bash
   ```

   Or on macOS:
   ```bash
   brew tap mobile-dev-inc/tap
   brew install maestro
   ```

2. **Install the Maestro Studio app** (optional, for visual test recording):
   - Download from [maestro.mobile.dev](https://maestro.mobile.dev)

## Running Tests

### Prerequisites

- Android device or emulator connected
- App built and installed (`./gradlew installDebug`)

### Run a specific test flow:

```bash
maestro test .maestro/01_test_full_config_flow.yaml
```

### Run all tests:

```bash
maestro test .maestro/
```

### Run with a specific device:

```bash
maestro test .maestro/01_test_full_config_flow.yaml --device <device-id>
```

List devices:
```bash
adb devices
```

## Test Flows

### `01_test_full_config_flow.yaml`

A complete first-time user setup flow that tests:

1. ✅ **NordVPN Token Entry** - Saves provider credentials
2. ✅ **VPN Server Configuration** - Adds a new VPN config via dialog
3. ✅ **App Rule Assignment** - Assigns a routing rule to an installed app
4. ✅ **VPN Service Toggle** - Starts the VPN service and handles system permission dialog
5. ✅ **Service Stop** - Stops the VPN service

**Note:** Before running this test, ensure:
- You have a valid NordVPN token (replace `MY_TEST_NORDVPN_TOKEN_12345` in the YAML)
- The device/emulator has Chrome installed (`com.android.chrome`) or update the test to use a different app

## Test Tags Reference

All interactive UI elements have been tagged with `testTag` modifiers for Maestro to find them:

| Element | Test Tag ID |
|---------|-------------|
| Master VPN Toggle | `start_service_toggle` |
| NordVPN Token Field | `nord_token_textfield` |
| Save Token Button | `nord_token_save_button` |
| Add VPN Config Button | `add_vpn_config_button` |
| Config Name Field | `config_name_textfield` |
| Config Region Dropdown | `config_region_dropdown` |
| Config Server Field | `config_server_textfield` |
| Config Save Button | `config_save_button` |
| VPN Config List Item | `vpn_config_item_{configName}` |
| App Rule Dropdown | `app_rule_dropdown_{packageName}` |

## Troubleshooting

### Test fails to find elements:
- Ensure the app is built with the latest code (all `testTag` modifiers added)
- Check that the app is installed on the device/emulator
- Verify the element is visible on screen (may need to scroll)

### VPN permission dialog not handled:
- The test uses `optional: true` for system dialogs as they may vary by Android version
- Adjust the dialog text matching in the YAML if needed for your Android version

### App crashes during test:
- Check `adb logcat` for error messages
- Ensure all dependencies are properly configured in `build.gradle.kts`

## Continuous Integration

Maestro tests can be integrated into CI/CD pipelines. Example:

```yaml
# GitHub Actions example
- name: Run Maestro tests
  run: |
    maestro test .maestro/01_test_full_config_flow.yaml
```

For more information, visit [Maestro Documentation](https://maestro.mobile.dev/docs/getting-started).
