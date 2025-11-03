# Routing Issues Analysis

## Critical Issues Found

### 1. ❌ Direct Internet Routing Problem (MAJOR)

**Location:** `PacketRouter.sendToDirectInternet()`

**Issue:** When routing to "direct internet" (no VPN rule), packets are written back to `vpnOutput`, which routes them through the VPN TUN interface again, creating a loop.

**Current Code:**
```kotlin
private fun sendToDirectInternet(packet: ByteArray) {
    vpnOutput?.write(packet)
    vpnOutput?.flush()
}
```

**Problem:** Writing to `vpnOutput` will route packets back through the TUN interface, which will be intercepted again, causing an infinite loop or incorrect routing.

**Solution:** For direct internet, we should:
1. Use `VpnService.protect(socket)` to exclude specific sockets from VPN routing
2. Create a separate socket connection that bypasses the VPN
3. OR: Use `Socket` class and call `vpnService.protect()` on it before connecting

### 2. ⚠️ Tunnel Connection Status Check

**Location:** `VpnConnectionManager.sendPacketToTunnel()`

**Issue:** Packets are only sent if tunnel `isConnected() == true`. If tunnels aren't connecting (e.g., OpenVPN 3 not fully integrated), no packets will be routed.

**Current Code:**
```kotlin
if (client != null && client.isConnected()) {
    client.sendPacket(packet)
} else {
    Log.w(TAG, "Tunnel $tunnelId not found or not connected")
}
```

**Problem:** If OpenVPN 3 isn't working, tunnels never connect, so packets are silently dropped.

**Solution:** Add more detailed logging and error handling to detect connection failures.

### 3. ❌ OpenVPN 3 Integration Status

**Location:** `NativeOpenVpnClient`, `openvpn_wrapper.cpp`

**Issue:** OpenVPN 3 dependencies are configured but may not be fully linked/working.

**Current Status:**
- Dependencies (fmt, asio, lz4, mbedTLS) are configured via FetchContent
- OpenVPN 3 subdirectory is added in CMakeLists.txt
- But actual compilation and linking may not be working

**Solution:** Verify that:
1. OpenVPN 3 actually compiles with dependencies
2. Native library links correctly
3. `OPENVPN3_AVAILABLE` is defined when OpenVPN 3 is enabled
4. JNI functions work correctly

### 4. ⚠️ Packet Flow for VPN-Routed Packets

**Issue:** When a packet is routed to a VPN tunnel:

1. `PacketRouter.routePacket()` → `VpnConnectionManager.sendPacketToTunnel()`
2. `VpnConnectionManager.sendPacketToTunnel()` → `OpenVpnClient.sendPacket()`
3. `NativeOpenVpnClient.sendPacket()` → `nativeSendPacket()` (JNI)
4. JNI → `openvpn_wrapper_send_packet()` → OpenVPN 3 Client

**Potential Issues:**
- OpenVPN 3 may not be actually processing packets
- `openvpn_wrapper_send_packet()` has TODOs for TunBuilder implementation
- Packets may be queued but never sent

### 5. ❌ ics-openvpn Dependency Still Included

**Location:** `settings.gradle.kts`

**Issue:** The old `ics-openvpn` module is still included, causing build failures.

**Current Code:**
```kotlin
include(":openvpn")
project(":openvpn").projectDir = file("libs/ics-openvpn/main")
```

**Solution:** Remove this, we're using OpenVPN 3 now.

## Recommended Fixes (Priority Order)

### Priority 1: Fix Direct Internet Routing
- Implement proper `VpnService.protect()` for direct internet connections
- Create socket-based forwarding for direct internet traffic

### Priority 2: Remove ics-openvpn
- Remove from `settings.gradle.kts`
- Clean up build files

### Priority 3: Verify OpenVPN 3 Integration
- Check CMake build logs for OpenVPN 3 compilation
- Verify JNI library links correctly
- Test `NativeOpenVpnClient.connect()` with actual VPN server

### Priority 4: Improve Logging
- Add detailed logging for packet routing decisions
- Log tunnel connection status
- Log packet counts sent/received

## Testing Strategy

1. **Unit Tests:** Test `PacketRouter` with mocked dependencies
2. **Integration Tests:** Test tunnel creation and packet forwarding
3. **E2E Tests:** Test actual VPN routing with real VPN server
4. **Log Analysis:** Check logcat for routing decisions and errors

