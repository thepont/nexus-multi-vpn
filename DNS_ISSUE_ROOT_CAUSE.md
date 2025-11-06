# DNS Resolution Issue - Root Cause Analysis

## Date
November 6, 2025

## Test Environment
- **Device:** Google Pixel 6 (real hardware, Android 16)
- **Test:** `test_routesToUK` (single VPN tunnel to UK NordVPN server)

## Problem Statement
DNS queries fail with `UnknownHostException: Unable to resolve host "ip-api.com": No address associated with hostname` even though:
- VPN connection is established ✅
- TLS handshake is complete ✅
- Session is ACTIVE ✅
- DNS servers are configured (103.86.96.100, 103.86.99.100) ✅
- DNS queries are being sent to the tunnel ✅

## Investigation Results

### 1. VPN Connection Status
```
OpenVPN: Session is ACTIVE
OpenVPN: TLS Handshake: TLSv1.3/TLS1-3-AES-256-GCM-SHA384
tun_builder_add_address: 10.100.0.2/16
DNS servers: 103.86.96.100, 103.86.99.100
```
✅ **Verdict:** VPN connection is fully established

### 2. Packet Flow Analysis

#### Outbound DNS Queries (Device → VPN)
```
📦 [Packet #47] Read 67 bytes from TUN
🔍 DNS query detected: /10.100.0.2:25018 → /103.86.96.100:53
🌐 Routing DNS query to tunnel nordvpn_UK (packet size: 67)
✅ DNS query sent to tunnel nordvpn_UK
```
✅ **Verdict:** DNS queries are being routed to the correct tunnel

#### Inbound DNS Responses (VPN → Device)
```
📥 Socket pair reader stopped for tunnel nordvpn_UK (read 0 responses total)
```
❌ **Verdict:** **ZERO responses received from OpenVPN 3**

### 3. Socket Pair I/O Analysis

#### Kotlin Side (VpnConnectionManager)
- ✅ Socket pair created successfully (SOCK_SEQPACKET)
- ✅ Kotlin FD obtained and writer created
- ✅ Packets written to socket pair via `writer.write(packet)`
- ✅ `writer.flush()` called after each packet
- ❌ Socket pair reader reads 0 bytes back

#### OpenVPN 3 Side (C++ native)
- ✅ OpenVPN 3 FD created and passed to `tun_builder_establish()`
- ✅ TLS handshake completes (proves control channel works)
- ❌ **NO evidence of OpenVPN 3 reading from the socket pair**
- ❌ **NO evidence of OpenVPN 3 writing to the socket pair**
- ❌ No `tun_read`/`tun_write` logs from OpenVPN 3

## Root Cause

**OpenVPN 3 ClientAPI is NOT reading from the socket pair FD we provide via `tun_builder_establish()`.**

### Why This Happens

OpenVPN 3 ClientAPI has specific expectations for TUN FD behavior:

1. **Event-driven I/O:** OpenVPN 3 uses an internal event loop (select/poll/epoll) to monitor the TUN FD for readability
2. **Blocking vs Non-blocking:** The FD might need specific flags (O_NONBLOCK)
3. **Platform-specific behavior:** OpenVPN 3 might have Android-specific code paths that bypass custom TUN FDs
4. **DCO Mode:** OpenVPN 3 might be trying to use Data Channel Offload (DCO), which requires kernel-level TUN integration

### Evidence

1. **Control Channel Works:** TLS handshake completes, proving OpenVPN 3 CAN communicate with the VPN server
2. **Data Channel Fails:** No packets are processed, proving OpenVPN 3 is NOT reading from our TUN FD
3. **Zero I/O Activity:** No native logs showing TUN read/write operations

## Architecture Implications

Our current architecture requires:
```
Device App → TUN → VpnEngineService (read) → PacketRouter (route) 
    → VpnConnectionManager (write to socketpair) → OpenVPN 3 (read from socketpair)
    → VPN Server → OpenVPN 3 (write to socketpair) → VpnConnectionManager (read from socketpair)
    → TUN (write) → Device App
```

But OpenVPN 3 ClientAPI expects:
```
Device App → TUN → OpenVPN 3 (direct read/write)
    → VPN Server → OpenVPN 3 → TUN → Device App
```

**These two models are fundamentally incompatible.**

## Possible Solutions

### Option 1: Fix OpenVPN 3 Event Loop (Complex)
- Investigate OpenVPN 3 ClientAPI source code
- Determine why TUN FD is not being polled
- May require patching OpenVPN 3 itself
- **Estimated Effort:** High (1-2 weeks)

### Option 2: Use OpenVPN 3 DCO Mode (May not work on Android)
- Data Channel Offload bypasses userspace completely
- Requires kernel support (might not be available on Android)
- **Estimated Effort:** Medium (3-5 days to investigate)

### Option 3: Manual Packet Pumping (Requires API Changes)
- Don't rely on OpenVPN 3's event loop
- Manually call `client->tun_read()` and `client->tun_write()` methods
- Requires checking if ClientAPI exposes these methods
- **Estimated Effort:** Medium (3-5 days)

### Option 4: Use ics-openvpn (Recommended Short-term)
- ics-openvpn is Android-specific and battle-tested
- Already handles custom TUN routing
- Used by OpenVPN for Android app
- Proven to work with multi-tunnel routing
- **Estimated Effort:** Low-Medium (2-3 days integration)

### Option 5: Switch to WireGuard (Recommended Long-term)
- Simpler protocol, easier to implement multi-tunnel
- Better performance
- Modern cryptography
- Native Android support
- **Estimated Effort:** Medium (5-7 days for full implementation)

## Recommended Next Steps

### Immediate (Today)
1. ✅ Document root cause (this file)
2. Test with ics-openvpn to verify multi-tunnel concept
3. Verify that single VPN works if we give OpenVPN 3 the REAL TUN FD (not socket pair)

### Short-term (This Week)
1. Integrate ics-openvpn for OpenVPN support
2. Keep the existing architecture (PacketRouter, ConnectionTracker)
3. Get E2E tests passing

### Long-term (Next Sprint)
1. Evaluate WireGuard for better performance and simpler code
2. Consider hybrid approach (WireGuard for most tunnels, ics-openvpn for OpenVPN providers)

## Test Results Summary

| Test | Status | Notes |
|------|--------|-------|
| test_routesToDirectInternet | ✅ PASS | No VPN involved |
| test_routesToUK | ❌ FAIL | DNS fails (0 responses from OpenVPN) |
| test_routesToFrance | ❓ Not run | Would fail for same reason |
| test_multiTunnel_BothUKandFRActive | ❓ Not run | Would fail for same reason |

## Conclusion

The multi-tunnel architecture is sound (PacketRouter, ConnectionTracker, socket pairs). The issue is specifically with **OpenVPN 3 ClientAPI not being designed for custom TUN FD handling** on Android.

We need to either:
1. Fix/patch OpenVPN 3 ClientAPI (hard)
2. Use a different OpenVPN library (ics-openvpn) (easier)
3. Switch to WireGuard (cleanest long-term)

