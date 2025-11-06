# Comprehensive Test Suite for Routing and Connection

## Executive Summary

This document describes a comprehensive testing strategy for the Multi-Region VPN Router application, focusing on routing functionality, VPN tunnel connections, and end-to-end integration.

## Current Test Status

### ✅ Existing Tests

#### Unit Tests (JVM)
- `PacketRouterTest.kt` - Basic packet parsing and routing logic
- `VpnConnectionManagerTest.kt` - Tunnel creation and management
- `TunnelManagerTest.kt` - Tunnel lifecycle management
- `ProcNetParserTest.kt` - UID detection from /proc/net
- `SettingsRepositoryTest.kt` - Data layer operations
- `DirectInternetForwardingTest.kt` - Direct routing tests

#### Integration Tests (Android)
- `VpnConfigDaoTest.kt` - Database operations
- `AppRuleDaoTest.kt` - Rule persistence
- `VpnRoutingTest.kt` - E2E routing (currently failing - tunnels not connecting)

### ⚠️ Test Gaps Identified

1. **OpenVPN 3 Connection Tests** - No tests verify actual OpenVPN 3 client connections
2. **Tunnel Status Monitoring** - No tests for connection state changes
3. **Packet Forwarding Verification** - Limited tests for actual packet routing
4. **Multi-Tunnel Scenarios** - No tests for simultaneous multiple tunnels
5. **Connection Failure Handling** - No tests for error recovery
6. **Performance/Load Tests** - No tests for high packet volumes
7. **Native Library Integration** - No tests verify JNI bridge works correctly

## Comprehensive Test Suite Architecture

### Test Pyramid

```
                    /\
                   /  \
                  / E2E \          ← End-to-End Tests (Slow, Realistic)
                 /______\
                /        \
               /Integration\        ← Integration Tests (Medium Speed)
              /____________\
             /              \
            /   Unit Tests    \      ← Unit Tests (Fast, Isolated)
           /__________________\
```

## Test Categories

### 1. Unit Tests (Fast - JVM)

#### 1.1 PacketRouter Tests

**Location:** `app/src/test/java/com/multiregionvpn/core/PacketRouterTest.kt`

**Existing:**
- ✅ Basic packet parsing
- ✅ UID lookup
- ✅ Rule-based routing decisions

**Missing:**
- ⚠️ **Test: Malformed packets handling**
  - Invalid IP headers
  - Truncated packets
  - Oversized packets
  - Non-IPv4 packets (IPv6)

- ⚠️ **Test: Edge cases in routing**
  - Multiple packages for same UID
  - Missing VPN config for rule
  - Invalid tunnel IDs
  - Concurrent packet routing

- ⚠️ **Test: Performance**
  - High packet throughput
  - Memory leaks under load
  - Latency measurements

**Recommended Additions:**
```kotlin
class PacketRouterEdgeCasesTest {
    @Test fun test_malformedPackets_areDropped()
    @Test fun test_oversizedPackets_areHandled()
    @Test fun test_ipv6Packets_areHandled()
    @Test fun test_concurrentRouting_threadSafe()
    @Test fun test_missingVpnConfig_fallsBackToDirect()
    @Test fun test_highPacketThroughput_performanceAcceptable()
}
```

#### 1.2 VpnConnectionManager Tests

**Location:** `app/src/test/java/com/multiregionvpn/core/VpnConnectionManagerTest.kt`

**Existing:**
- ✅ Tunnel creation with mock client
- ✅ Packet forwarding to tunnels

**Missing:**
- ⚠️ **Test: Connection lifecycle**
  - Connection timeout handling
  - Reconnection logic
  - Connection state transitions
  - Multiple simultaneous connections

- ⚠️ **Test: Error handling**
  - Failed connection attempts
  - Network interruptions
  - Invalid configurations
  - Authentication failures

**Recommended Additions:**
```kotlin
class VpnConnectionManagerLifecycleTest {
    @Test fun test_connectionTimeout_handledGracefully()
    @Test fun test_reconnection_afterFailure()
    @Test fun test_multipleTunnels_simultaneousConnections()
    @Test fun test_invalidConfig_reportsError()
    @Test fun test_authFailure_handledCorrectly()
    @Test fun test_tunnelDisconnection_cleanupProperly()
}
```

### 2. Integration Tests (Medium Speed - Android)

#### 2.1 Native Library Integration Tests

**Location:** `app/src/androidTest/java/com/multiregionvpn/core/vpnclient/NativeOpenVpnClientIntegrationTest.kt` (NEW)

**Purpose:** Verify JNI bridge and OpenVPN 3 integration work correctly

**Critical Tests:**
```kotlin
@RunWith(AndroidJUnit4::class)
class NativeOpenVpnClientIntegrationTest {
    
    @Test
    fun test_nativeLibrary_loadsSuccessfully() {
        // Verify System.loadLibrary("openvpn-jni") works
        // Verify no UnsatisfiedLinkError
    }
    
    @Test
    fun test_jniFunctions_callable() {
        // Test each JNI function can be called
        // nativeConnect, nativeDisconnect, nativeSendPacket, etc.
    }
    
    @Test
    fun test_openvpn3Headers_available() {
        // Verify OPENVPN3_AVAILABLE is defined
        // Verify OpenVPN 3 ClientAPI headers are accessible
    }
    
    @Test
    fun test_dependencies_linkedCorrectly() {
        // Verify fmt, asio, lz4, mbedTLS are available
    }
}
```

#### 2.2 Tunnel Connection Tests

**Location:** `app/src/androidTest/java/com/multiregionvpn/core/VpnConnectionManagerIntegrationTest.kt` (NEW)

**Purpose:** Test actual tunnel creation and connection

**Critical Tests:**
```kotlin
@RunWith(AndroidJUnit4::class)
class VpnConnectionManagerIntegrationTest {
    
    @Test
    fun test_createTunnel_withValidConfig_connects() {
        // Use real OpenVPN 3 client
        // Verify connection state transitions
        // Verify isConnected() returns true
    }
    
    @Test
    fun test_createTunnel_withInvalidConfig_fails() {
        // Test error handling for bad configs
        // Verify proper error messages
    }
    
    @Test
    fun test_tunnelLifecycle_createConnectDisconnect() {
        // Full lifecycle test
        // Verify cleanup on disconnect
    }
    
    @Test
    fun test_multipleTunnels_differentRegions() {
        // Create UK and FR tunnels simultaneously
        // Verify both stay connected
    }
    
    @Test
    fun test_tunnelReconnection_afterFailure() {
        // Simulate network failure
        // Verify reconnection logic
    }
}
```

#### 2.3 Packet Forwarding Integration Tests

**Location:** `app/src/androidTest/java/com/multiregionvpn/core/PacketForwardingIntegrationTest.kt` (NEW)

**Purpose:** Verify packets actually flow through tunnels

**Critical Tests:**
```kotlin
@RunWith(AndroidJUnit4::class)
class PacketForwardingIntegrationTest {
    
    @Test
    fun test_packetSentToTunnel_receivedByOpenVPN() {
        // Send packet to connected tunnel
        // Verify it reaches OpenVPN client
        // Verify packet integrity
    }
    
    @Test
    fun test_packetFromTunnel_writtenToTunInterface() {
        // Receive packet from OpenVPN
        // Verify it's written to TUN interface
        // Verify it reaches the app
    }
    
    @Test
    fun test_bidirectionalPacketFlow() {
        // Full round-trip test
        // Packet from app → tunnel → server → tunnel → app
    }
    
    @Test
    fun test_multipleTunnels_packetRouting() {
        // Send packets to different tunnels
        // Verify correct routing
    }
}
```

### 3. End-to-End Tests (Slow - Realistic)

#### 3.1 Enhanced Routing Tests

**Location:** `app/src/androidTest/java/com/multiregionvpn/VpnRoutingTest.kt` (ENHANCE)

**Current Issues:**
- Tunnels not connecting (OpenVPN 3 integration incomplete)
- IP location verification failing (expected GB/FR, got AU)

**Improvements Needed:**

**A. Pre-Flight Checks:**
```kotlin
@Before
fun verifySystemReady() {
    // Check VPN service can start
    // Check OpenVPN 3 native library loaded
    // Check network connectivity
    // Check credentials are valid
}
```

**B. Tunnel Connection Verification:**
```kotlin
private fun verifyTunnelConnected(tunnelId: String, timeout: Long = 30000) {
    val startTime = System.currentTimeMillis()
    while (System.currentTimeMillis() - startTime < timeout) {
        if (connectionManager.isTunnelConnected(tunnelId)) {
            return // Success
        }
        Thread.sleep(1000)
    }
    fail("Tunnel $tunnelId did not connect within $timeout ms")
}
```

**C. Enhanced Logging:**
```kotlin
private fun captureDiagnostics(testName: String) {
    // Capture logcat output
    // Capture tunnel connection status
    // Capture packet routing decisions
    // Save diagnostics for analysis
}
```

**D. New Test Cases:**
```kotlin
@Test
fun test_routingToUK_withConnectionVerification() {
    // 1. Create rule for UK VPN
    // 2. Verify tunnel connects (NEW)
    // 3. Wait for connection to stabilize (NEW)
    // 4. Make HTTP request
    // 5. Verify IP location is UK
}

@Test
fun test_routingToFrance_withConnectionVerification() {
    // Same as UK but for France
}

@Test
fun test_directInternet_noRuleExists() {
    // Verify no tunnel is created
    // Verify traffic goes direct
    // Verify IP location matches baseline
}

@Test
fun test_ruleRemoval_tunnelClosed() {
    // Create rule → tunnel created
    // Remove rule → tunnel closed
    // Verify cleanup
}

@Test
fun test_multipleRules_differentRegions() {
    // Create rules for multiple apps
    // Route to different regions
    // Verify all tunnels stay connected
}
```

#### 3.2 Connection Failure Recovery Tests

**Location:** `app/src/androidTest/java/com/multiregionvpn/VpnConnectionRecoveryTest.kt` (NEW)

**Purpose:** Test behavior when VPN connections fail

**Test Cases:**
```kotlin
@RunWith(AndroidJUnit4::class)
class VpnConnectionRecoveryTest {
    
    @Test
    fun test_invalidCredentials_handledGracefully() {
        // Use invalid credentials
        // Verify error reporting
        // Verify app doesn't crash
    }
    
    @Test
    fun test_networkInterruption_reconnects() {
        // Simulate network loss
        // Verify reconnection logic
        // Verify packet routing resumes
    }
    
    @Test
    fun test_serverUnavailable_fallback() {
        // Use unreachable server
        // Verify timeout handling
        // Verify error state
    }
    
    @Test
    fun test_configurationError_detected() {
        // Use malformed OVPN config
        // Verify error detection
        // Verify user notification
    }
}
```

#### 3.3 Performance and Load Tests

**Location:** `app/src/androidTest/java/com/multiregionvpn/VpnPerformanceTest.kt` (NEW)

**Purpose:** Verify system handles high load

**Test Cases:**
```kotlin
@RunWith(AndroidJUnit4::class)
class VpnPerformanceTest {
    
    @Test
    fun test_highPacketThroughput_sustained() {
        // Send high volume of packets
        // Measure throughput
        // Verify no packet loss
        // Verify latency acceptable
    }
    
    @Test
    fun test_multipleTunnels_performance() {
        // Create 5+ simultaneous tunnels
        // Route packets to all
        // Verify performance acceptable
    }
    
    @Test
    fun test_memoryUsage_stable() {
        // Long-running test
        // Monitor memory usage
        // Verify no leaks
    }
    
    @Test
    fun test_cpuUsage_acceptable() {
        // Monitor CPU usage
        // Verify not excessive
    }
}
```

### 4. Diagnostic and Monitoring Tests

#### 4.1 Connection Diagnostics

**Location:** `app/src/androidTest/java/com/multiregionvpn/VpnDiagnosticsTest.kt` (NEW)

**Purpose:** Help diagnose connection issues

**Test Cases:**
```kotlin
@RunWith(AndroidJUnit4::class)
class VpnDiagnosticsTest {
    
    @Test
    fun test_collectDiagnostics_forFailedConnection() {
        // Collect logs
        // Collect OpenVPN 3 status
        // Collect network state
        // Generate diagnostic report
    }
    
    @Test
    fun test_verifyOpenVpn3Integration() {
        // Check native library loaded
        // Check dependencies available
        // Check JNI functions callable
        // Report integration status
    }
    
    @Test
    fun test_networkConnectivity() {
        // Test DNS resolution
        // Test HTTP connectivity
        // Test VPN server reachability
    }
}
```

## Test Execution Strategy

### Test Levels

1. **Unit Tests** - Run on every commit
   - Fast execution (< 30 seconds)
   - No device/emulator required
   - CI/CD pipeline

2. **Integration Tests** - Run on every PR
   - Medium execution (< 5 minutes)
   - Requires device/emulator
   - Full Android environment

3. **E2E Tests** - Run before releases
   - Slow execution (< 15 minutes)
   - Requires real VPN credentials
   - Full system integration

### Test Organization

```
app/src/test/java/              # Unit tests (JVM)
├── com.multiregionvpn.core/
│   ├── PacketRouterTest.kt           ✅ Existing
│   ├── VpnConnectionManagerTest.kt    ✅ Existing
│   ├── TunnelManagerTest.kt            ✅ Existing
│   ├── PacketRouterEdgeCasesTest.kt    ⚠️  NEW
│   └── VpnConnectionManagerLifecycleTest.kt  ⚠️  NEW
│
app/src/androidTest/java/      # Integration & E2E tests (Android)
├── com.multiregionvpn.core/
│   ├── vpnclient/
│   │   └── NativeOpenVpnClientIntegrationTest.kt  ⚠️  NEW
│   ├── VpnConnectionManagerIntegrationTest.kt    ⚠️  NEW
│   └── PacketForwardingIntegrationTest.kt        ⚠️  NEW
│
├── VpnRoutingTest.kt                              ✅ Existing (needs enhancement)
├── VpnConnectionRecoveryTest.kt                   ⚠️  NEW
├── VpnPerformanceTest.kt                          ⚠️  NEW
└── VpnDiagnosticsTest.kt                          ⚠️  NEW
```

## Test Data and Fixtures

### Test VPN Configurations

**Location:** `app/src/androidTest/assets/vpn-configs/`

- `valid-uk-server.ovpn` - Valid UK server config
- `valid-fr-server.ovpn` - Valid France server config
- `invalid-config.ovpn` - Malformed config for error testing
- `expired-cert.ovpn` - Expired certificate for testing

### Test Credentials

**Location:** `.env` (production credentials)
**Location:** `app/src/androidTest/assets/test-credentials.json` (test credentials - gitignored)

## Test Execution Commands

### Run All Unit Tests
```bash
./gradlew test
```

### Run Specific Test Class
```bash
./gradlew test --tests "com.multiregionvpn.core.PacketRouterTest"
```

### Run All Integration Tests
```bash
./gradlew connectedAndroidTest
```

### Run E2E Tests with Credentials
```bash
./scripts/run-e2e-tests.sh
```

### Run Specific Integration Test
```bash
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.multiregionvpn.VpnRoutingTest
```

## Test Coverage Goals

### Current Coverage
- Unit Tests: ~60% code coverage
- Integration Tests: ~30% code coverage
- E2E Tests: Basic routing scenarios only

### Target Coverage
- Unit Tests: 80%+ coverage
- Integration Tests: 70%+ coverage
- E2E Tests: All critical user flows

## Critical Test Scenarios (Priority Order)

### Priority 1: Critical Paths (Must Pass)
1. ✅ OpenVPN 3 native library loads
2. ⚠️ VPN tunnel connects with valid credentials
3. ⚠️ Packets routed correctly based on rules
4. ⚠️ IP location changes when routed through VPN
5. ⚠️ Direct internet routing works when no rule exists

### Priority 2: Error Handling (Should Pass)
1. ⚠️ Invalid credentials handled gracefully
2. ⚠️ Network failures trigger reconnection
3. ⚠️ Malformed packets don't crash system
4. ⚠️ Tunnel disconnection cleanup works

### Priority 3: Edge Cases (Nice to Have)
1. ⚠️ Multiple simultaneous tunnels
2. ⚠️ High packet throughput
3. ⚠️ Memory leak prevention
4. ⚠️ Configuration validation

## Test Failure Investigation Guide

### When E2E Tests Fail

1. **Check Native Library Loading**
   ```bash
   adb logcat | grep -i "openvpn-jni\|UnsatisfiedLinkError"
   ```

2. **Check Tunnel Connection Status**
   ```bash
   adb logcat | grep -i "VpnConnectionManager\|Tunnel\|connect"
   ```

3. **Check Packet Routing**
   ```bash
   adb logcat | grep -i "PacketRouter\|routePacket"
   ```

4. **Check OpenVPN 3 Errors**
   ```bash
   adb logcat | grep -i "OpenVPN\|native\|JNI"
   ```

5. **Capture Full Diagnostics**
   ```bash
   ./scripts/capture-diagnostics.sh
   ```

## Continuous Integration

### GitHub Actions / CI Pipeline

```yaml
name: Test Suite

on: [push, pull_request]

jobs:
  unit-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - name: Run unit tests
        run: ./gradlew test
  
  integration-tests:
    runs-on: macos-latest
    needs: unit-tests
    steps:
      - uses: actions/checkout@v2
      - name: Start emulator
        uses: reactivecircus/android-emulator-runner@v2
      - name: Run integration tests
        run: ./gradlew connectedAndroidTest
  
  e2e-tests:
    runs-on: macos-latest
    needs: integration-tests
    if: github.ref == 'refs/heads/main'
    steps:
      - uses: actions/checkout@v2
      - name: Run E2E tests
        run: ./scripts/run-e2e-tests.sh
        env:
          NORDVPN_USERNAME: ${{ secrets.NORDVPN_USERNAME }}
          NORDVPN_PASSWORD: ${{ secrets.NORDVPN_PASSWORD }}
```

## Summary

This comprehensive test suite provides:

1. **Complete Coverage** - Unit, integration, and E2E tests
2. **Diagnostic Tools** - Tests to help debug issues
3. **Performance Validation** - Tests for high-load scenarios
4. **Error Handling** - Tests for failure scenarios
5. **CI/CD Integration** - Automated test execution

**Next Steps:**
1. Implement missing unit test edge cases
2. Create integration tests for OpenVPN 3
3. Enhance E2E tests with connection verification
4. Add performance and diagnostics tests
5. Set up CI/CD pipeline


