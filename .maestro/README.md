# Maestro UI E2E Tests

Comprehensive UI end-to-end tests for the Multi-Region VPN Router app.

## Prerequisites

1. **Maestro installed:**
   ```bash
   curl -Ls "https://get.maestro.mobile.dev" | bash
   export PATH="$PATH:$HOME/.maestro/bin"
   ```

2. **Device connected:**
   ```bash
   adb devices  # Should show your device
   ```

3. **Environment variables:**
   ```bash
   export NORDVPN_USERNAME="your_username"
   export NORDVPN_PASSWORD="your_password"
   ```

4. **Bootstrap credentials and tunnels:**
   ```bash
   # Run bootstrap test first
   adb shell am instrument -w \
     -e class com.multiregionvpn.BootstrapCredentialsTest \
     -e NORDVPN_USERNAME "$NORDVPN_USERNAME" \
     -e NORDVPN_PASSWORD "$NORDVPN_PASSWORD" \
     com.multiregionvpn.test/androidx.test.runner.AndroidJUnitRunner
   ```

## Test Files

### 01_navigation_test.yaml
Tests basic navigation between tabs and UI elements.
```bash
maestro test .maestro/01_navigation_test.yaml
```

**Tests:**
- Header bar visibility
- Bottom navigation tabs
- Screen transitions
- UI element presence

**Duration:** ~30 seconds

---

### 02_tunnel_management_test.yaml
Tests tunnel creation, editing, and deletion.
```bash
maestro test .maestro/02_tunnel_management_test.yaml
```

**Tests:**
- Add new tunnel with auto-fetch server
- Edit tunnel name
- Delete tunnel
- Tunnel list display

**Duration:** ~60 seconds

**Note:** Requires NordVPN credentials to be set in environment

---

### 03_app_rules_test.yaml
Tests app rule assignment and search functionality.
```bash
maestro test .maestro/03_app_rules_test.yaml
```

**Tests:**
- Search for apps
- Smart app ordering
- Assign app to tunnel
- Change app rule
- Remove app rule (Direct Internet)

**Duration:** ~45 seconds

**Prerequisites:** At least one tunnel configured

---

### 04_vpn_toggle_test.yaml
Tests VPN on/off toggle and status updates.
```bash
maestro test .maestro/04_vpn_toggle_test.yaml
```

**Tests:**
- Toggle VPN on
- Status changes (Disconnected → Connecting → Protected)
- Toggle VPN off
- Status returns to Disconnected
- Stability of toggle (on/off/on)

**Duration:** ~20 seconds

---

### 05_complete_workflow_test.yaml
Comprehensive test of the full user journey.
```bash
NORDVPN_USERNAME="xxx" NORDVPN_PASSWORD="yyy" \
maestro test .maestro/05_complete_workflow_test.yaml
```

**Tests:**
- Configure credentials
- Add UK tunnel
- Add France tunnel
- Assign apps to tunnels
- Start VPN
- Verify both tunnels connect
- Rapid switching between tunnels
- Stop VPN
- Cleanup

**Duration:** ~120 seconds

**Note:** Complete end-to-end test, runs full workflow

---

### 06_smart_app_badges_test.yaml
Tests smart app ordering and badge system.
```bash
maestro test .maestro/06_smart_app_badges_test.yaml
```

**Tests:**
- Geo-blocked apps appear at top
- Search filters apps correctly
- Badge system (visual verification)
- App ordering by priority

**Duration:** ~30 seconds

**Prerequisites:** Tunnels configured

---

### 07_verify_routing_with_chrome.yaml
Tests actual VPN routing with Chrome browser.
```bash
maestro test .maestro/07_verify_routing_with_chrome.yaml
```

**Tests:**
- Assign Chrome to UK tunnel
- Start VPN
- Launch Chrome
- Navigate to ip-api.com
- Verify UK IP (manual verification)

**Duration:** ~45 seconds

**Prerequisites:** Chrome installed, tunnels configured

**Note:** Requires manual verification of IP address

---

### 08_multi_tunnel_test.yaml
Tests multiple tunnels running simultaneously.
```bash
maestro test .maestro/08_multi_tunnel_test.yaml
```

**Tests:**
- Multiple apps assigned to different tunnels
- Both tunnels connect simultaneously
- Rapid switching between tunnels
- VPN stays connected during switches
- Cleanup of app rules

**Duration:** ~90 seconds

**Prerequisites:** UK and FR tunnels from bootstrap

---

## Running All Tests

### Run complete test suite:
```bash
maestro test .maestro/
```

### Run specific test:
```bash
maestro test .maestro/01_navigation_test.yaml
```

### Run with environment variables:
```bash
NORDVPN_USERNAME="xxx" NORDVPN_PASSWORD="yyy" \
maestro test .maestro/05_complete_workflow_test.yaml
```

---

## Test Coverage

### UI Components Tested
- ✅ Header bar (status, toggle switch)
- ✅ Bottom navigation (4 tabs)
- ✅ Tunnel list and management
- ✅ App list with search
- ✅ VPN toggle functionality
- ✅ Dialogs (add/edit tunnel, select tunnel)
- ✅ Status updates (Disconnected/Connecting/Protected/Error)

### User Flows Tested
- ✅ First-time setup (credentials + tunnels)
- ✅ Assign app to tunnel
- ✅ Start/stop VPN
- ✅ Rapid region switching
- ✅ Multi-tunnel operation
- ✅ Search and filtering
- ✅ Tunnel CRUD operations

### Edge Cases Tested
- ✅ VPN permission handling
- ✅ Missing credentials
- ✅ No tunnels configured
- ✅ Network errors
- ✅ Rapid toggle on/off

---

## Expected Results

All tests should **PASS** when:
1. App is installed on device
2. NordVPN credentials are valid
3. Device has internet connection
4. VPN permission is granted

## Troubleshooting

### Test fails with "Element not found"
- Check if testTag modifiers are present in Compose UI
- Verify UI text matches test expectations
- Check if device language is English

### Test fails with VPN permission dialog
- Grant permission manually once
- Or use: `adb shell appops set com.multiregionvpn ACTIVATE_VPN allow`

### Test fails with "Tunnel not connecting"
- Check NordVPN credentials
- Verify internet connection
- Check logcat for errors

---

## CI/CD Integration

### GitHub Actions Example
```yaml
- name: Run Maestro UI Tests
  run: |
    export NORDVPN_USERNAME=${{ secrets.NORDVPN_USERNAME }}
    export NORDVPN_PASSWORD=${{ secrets.NORDVPN_PASSWORD }}
    maestro test .maestro/
```

### Local Testing
```bash
# Quick smoke test
maestro test .maestro/01_navigation_test.yaml

# Full test suite
maestro test .maestro/

# Specific workflow
maestro test .maestro/05_complete_workflow_test.yaml
```

---

## Test Maintenance

### When to Update Tests
- UI text changes → Update assertVisible text
- New features added → Add new test file
- Navigation changes → Update tap sequences
- New dialogs → Add dialog assertions

### Best Practices
- Keep tests independent (each can run alone)
- Use testTag for critical UI elements
- Add optional: true for elements that may not exist
- Include wait times for network operations
- Clean up after tests (delete created data)
