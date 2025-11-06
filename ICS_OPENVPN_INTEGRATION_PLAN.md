# ics-openvpn Integration Plan

## Overview
Replace OpenVPN 3 ClientAPI with ics-openvpn for better Android compatibility and custom TUN routing support.

## Why ics-openvpn?
1. **Proven Solution:** Used by "OpenVPN for Android" (50M+ downloads)
2. **Android-Native:** Designed specifically for Android's VPN API
3. **Custom TUN Support:** Allows external TUN management (exactly what we need)
4. **Active Maintenance:** Regularly updated by Arne Schwabe
5. **OpenVPN 2.x Based:** More mature protocol implementation

## Architecture Comparison

### OpenVPN 3 ClientAPI (Current - Broken)
```
VpnEngineService → Socket Pair → OpenVPN 3 ClientAPI
                   ❌ OpenVPN 3 doesn't poll socket pair
```

### ics-openvpn (Target - Will Work)
```
VpnEngineService → Direct Packet Injection → ics-openvpn
                   ✅ ics-openvpn supports external TUN management
```

## Integration Steps

### Phase 1: Add ics-openvpn Dependency (Day 1 Morning)
- [ ] Add ics-openvpn as Git submodule or Gradle dependency
- [ ] Configure build.gradle to include ics-openvpn
- [ ] Build and verify ics-openvpn compiles

### Phase 2: Create Adapter (Day 1 Afternoon)
- [ ] Create `IcsOpenVpnClient` implementing `NativeOpenVpnClient` interface
- [ ] Implement connection management
- [ ] Implement packet injection API
- [ ] Implement callbacks (IP, DNS, connection state)

### Phase 3: Update VpnConnectionManager (Day 2 Morning)
- [ ] Replace OpenVPN 3 client creation with ics-openvpn
- [ ] Update packet sending logic
- [ ] Update packet receiving logic
- [ ] Remove socket pair code (no longer needed)

### Phase 4: Testing (Day 2 Afternoon)
- [ ] Test single VPN tunnel (UK)
- [ ] Test single VPN tunnel (France)
- [ ] Test multi-tunnel (UK + France)
- [ ] Test rapid switching
- [ ] Run full E2E test suite

### Phase 5: Cleanup (Day 3)
- [ ] Remove OpenVPN 3 native code
- [ ] Remove CMake build for OpenVPN 3
- [ ] Remove vcpkg dependencies
- [ ] Update documentation
- [ ] Final commit

## Key Differences

### OpenVPN 3 ClientAPI
- **TUN Handling:** Expects to own TUN FD completely
- **Event Loop:** Internal event loop (select/poll)
- **Packet I/O:** Direct read/write to TUN FD
- **Android Integration:** Limited (generic C++ library)

### ics-openvpn
- **TUN Handling:** Supports external TUN management
- **Event Loop:** Android Handler/Looper based
- **Packet I/O:** Exposed methods: `processPacket()`, `getOutgoingPacket()`
- **Android Integration:** Full (native Android library)

## API Design

### ics-openvpn Packet Flow
```kotlin
// Outbound (Device → VPN Server)
1. VpnEngineService reads packet from TUN
2. PacketRouter routes to appropriate tunnel
3. IcsOpenVpnClient.sendPacket(tunnelId, packet)
4. ics-openvpn encrypts and sends to server

// Inbound (VPN Server → Device)
1. ics-openvpn receives encrypted packet
2. ics-openvpn decrypts packet
3. Callback: IcsOpenVpnClient.onPacketReceived(tunnelId, packet)
4. VpnConnectionManager writes to TUN
5. Packet reaches app
```

### Interface Implementation
```kotlin
class IcsOpenVpnClient(
    private val context: Context,
    private val tunnelId: String
) : NativeOpenVpnClient {
    
    private var vpnService: OpenVpnManagementThread? = null
    
    override suspend fun connect(
        config: String,
        username: String,
        password: String
    ): Boolean {
        // Parse OpenVPN config
        val vpnProfile = ConfigParser.parseConfig(config)
        vpnProfile.mUsername = username
        vpnProfile.mPassword = password
        
        // Create management thread
        vpnService = OpenVpnManagementThread(
            context = context,
            profile = vpnProfile,
            tunFd = -1, // We manage TUN externally
            packetCallback = ::onPacketReceived
        )
        
        vpnService?.start()
        
        // Wait for connection
        return withTimeout(30000) {
            while (!isConnected()) {
                delay(100)
            }
            true
        }
    }
    
    override fun sendPacket(packet: ByteArray) {
        vpnService?.processOutgoingPacket(packet)
    }
    
    override fun isConnected(): Boolean {
        return vpnService?.isRunning == true &&
               vpnService?.connectionState == VpnState.CONNECTED
    }
    
    override fun disconnect() {
        vpnService?.stop()
    }
    
    private fun onPacketReceived(packet: ByteArray) {
        // Forward to VpnConnectionManager
        packetReceivedCallback?.invoke(tunnelId, packet)
    }
}
```

## Dependencies

### Option 1: Git Submodule (Recommended)
```bash
cd /home/pont/projects/multi-region-vpn
git submodule add https://github.com/schwabe/ics-openvpn.git ics-openvpn
git submodule update --init --recursive
```

Then in `settings.gradle.kts`:
```kotlin
include(":ics-openvpn:main")
```

And in `app/build.gradle.kts`:
```kotlin
dependencies {
    implementation(project(":ics-openvpn:main"))
}
```

### Option 2: Maven Dependency (If Available)
```kotlin
dependencies {
    implementation("de.blinkt.openvpn:ics-openvpn:0.7.x")
}
```

## Migration Checklist

### Files to Modify
- [x] `app/build.gradle.kts` - Add ics-openvpn dependency
- [ ] `app/src/main/java/com/multiregionvpn/core/vpnclient/IcsOpenVpnClient.kt` - New adapter
- [ ] `app/src/main/java/com/multiregionvpn/core/VpnConnectionManager.kt` - Use ics-openvpn
- [ ] `app/src/main/java/com/multiregionvpn/core/VpnEngineService.kt` - Update packet handling
- [ ] `settings.gradle.kts` - Include ics-openvpn module

### Files to Remove
- [ ] `app/src/main/cpp/openvpn_wrapper.cpp`
- [ ] `app/src/main/cpp/openvpn_wrapper.h`
- [ ] `app/src/main/cpp/openvpn_jni.cpp`
- [ ] `app/src/main/cpp/android_tun_builder.cpp`
- [ ] `app/src/main/cpp/android_tun_builder.h`
- [ ] `app/src/main/CMakeLists.txt` (or simplify significantly)
- [ ] `VCPKG_SETUP.md`

### Configuration Changes
- [ ] Remove CMake configuration from `build.gradle.kts`
- [ ] Remove vcpkg environment variables from `.env`
- [ ] Update native library loading code

## Advantages of This Approach

1. **✅ Proven:** ics-openvpn has 10+ years of production use
2. **✅ Maintained:** Active development and bug fixes
3. **✅ Android-First:** Designed for Android's constraints
4. **✅ Simpler Build:** No CMake, no vcpkg, just Gradle
5. **✅ Better Docs:** Android-focused documentation
6. **✅ Community:** Large user base and support

## Risks and Mitigations

### Risk 1: Different API
**Mitigation:** Our `NativeOpenVpnClient` interface abstracts the implementation

### Risk 2: Build Complexity
**Mitigation:** ics-openvpn provides Gradle integration

### Risk 3: Learning Curve
**Mitigation:** Good documentation and examples available

### Risk 4: Multi-Tunnel Support
**Mitigation:** We can create multiple `OpenVpnManagementThread` instances

## Timeline

- **Day 1 Morning:** Add dependency, build successfully
- **Day 1 Afternoon:** Create adapter, basic connection works
- **Day 2 Morning:** Packet injection working
- **Day 2 Afternoon:** Single VPN tests passing
- **Day 3:** Multi-tunnel tests passing, cleanup

**Total: ~3 days of focused work**

## Success Criteria

1. ✅ `test_routesToDirectInternet` passes (already passes)
2. ✅ `test_routesToUK` passes (currently fails)
3. ✅ `test_routesToFrance` passes (currently fails)
4. ✅ `test_multiTunnel_BothUKandFRActive` passes (currently fails)
5. ✅ `test_switchRegions_UKtoFR` passes (currently fails)
6. ✅ `test_rapidSwitching_UKtoFRtoUK` passes (currently fails)

All E2E tests passing on Pixel 6 = **Mission Accomplished!** 🎉

