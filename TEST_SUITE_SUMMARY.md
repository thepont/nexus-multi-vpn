# VPN Routing and Toggle Test Suite

## Overview
This test suite validates the VPN router's ability to handle dynamic app-to-tunnel remapping and proper service lifecycle management.

##  Tests Implemented

### 1. Unit Tests

#### `ConnectionTrackerTest.kt`
**Purpose**: Validates the connection tracking logic that maps apps to tunnels.

**Tests**:
- `mapsPackageToTunnel()` - Verifies package → tunnel mapping
- `clearPackageRemovesMapping()` - Ensures individual package cleanup works
- `clearAllMappingsResetsState()` - Verifies global state reset

**Status**: ✅ PASSING  
**Run**: `./gradlew :app:testDebugUnitTest --tests com.multiregionvpn.core.ConnectionTrackerTest`

### 2. Instrumentation/E2E Tests

#### `VpnToggleStateTest.kt`
**Purpose**: Ensures VPN service correctly updates state and clears mappings when started/stopped.

**Tests**:
- `startAndStopUpdatesServiceState()` - Validates:
  - Service starts when ACTION_START is sent
  - `VpnEngineService.isRunning()` returns true after start
  - Service stops when ACTION_STOP is sent
  - `VpnEngineService.isRunning()` returns false after stop
  - All connection tracker mappings are cleared on stop

**Status**: ✅ PASSING  
**Run**: 
```bash
adb shell am instrument -w \
  -e class com.multiregionvpn.VpnToggleStateTest \
  com.multiregionvpn.test/androidx.test.runner.AndroidJUnitRunner
```

#### `AppRuleRemapTest.kt`
**Purpose**: Validates that changing an app rule from one tunnel to another updates the ConnectionTracker.

**Tests**:
- `appRuleRemapsToNewTunnel()` - Validates:
  - App initially maps to UK tunnel
  - Changing rule to FR tunnel updates mapping
  - ConnectionTracker snapshot reflects new mapping

**Status**: ⚠️  NEEDS DOCKER - Test tries to establish OpenVPN connections  
**Next Steps**: Either:
1. Mock VPN connections in test
2. Run with Docker containers (see `app/src/androidTest/resources/docker-compose.*.yml`)
3. Skip actual connection and only test mapping logic

## Architecture Changes

### `ConnectionTracker.kt`
**New Methods**:
- `clearPackage(packageName: String)` - Clear single package mapping
- `clearAllMappings()` - Reset all state (called on VPN stop and rule changes)
- `getCurrentPackageMappings(): Map<String, String>` - Snapshot for tests/diagnostics

### `VpnEngineService.kt`
**New Static Methods**:
- `isRunning(): Boolean` - Check if service is currently active
- `getConnectionTrackerSnapshot(): Map<String, String>` - Get current package mappings

**Lifecycle Changes**:
- `onDestroy()` now clears all mappings and nullifies `runningInstance`
- `stopVpn()` clears all mappings before stopping
- `manageTunnels()` clears mappings on each rule update (Flow emission)

### `SettingsViewModel.kt`
**New Method**:
- `stopVpn(context)` - Properly stops service and updates UI state to DISCONNECTED

## Key Fixes

1. **Stale Routing Issue**: Connection tracker now clears mappings when:
   - VPN service stops
   - App rules change
   - Service is destroyed

2. **UI State Sync**: `stopVpn()` now updates UI state immediately so header shows correct status

3. **Test Coverage**: Unit and E2E tests ensure routing logic and lifecycle are correct

## Running All Tests

### Unit Tests Only
```bash
./gradlew :app:testDebugUnitTest
```

### E2E Tests (requires device/emulator)
```bash
# Install app and test APKs
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

# Run specific test
adb shell am instrument -w \
  -e class com.multiregionvpn.VpnToggleStateTest \
  com.multiregionvpn.test/androidx.test.runner.AndroidJUnitRunner
```

### All Tests (unit + instrumentation)
```bash
./gradlew :app:test :app:connectedAndroidTest
```

## Missing Tests

To achieve complete coverage, we should add:

1. **`ConnectionTrackerFlowTest`** - Verify mapping updates propagate correctly through Flows
2. **`VpnInterfaceRestartTest`** - Verify VPN interface restarts when allowed apps change
3. **`AppRuleRemapWithConnectionsTest`** - Full E2E with Docker: change app rule mid-connection and verify traffic switches tunnels
4. **`DirectInternetFallbackTest`** - Verify apps without rules get direct internet when VPN is active
5. **`MultiAppMultiTunnelTest`** - Verify multiple apps can simultaneously route through different tunnels

## Notes

- ConnectionTracker state is now ephemeral - it resets whenever rules change or service stops
- This ensures no stale routing data persists between VPN sessions
- Tests can use `VpnEngineService.getConnectionTrackerSnapshot()` to inspect current state

