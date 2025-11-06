# Connection and Routing Issues

## Issue 1: Connection Not Completing

### Symptoms
- OpenVPN 3 connections stay in "connecting" state (connecting=true)
- Never transition to fully "connected" (connected=true)
- Connection timeout after 60+ seconds

### Possible Causes
1. **Authentication failure** - Credentials rejected by NordVPN
2. **Network issues** - Can't reach VPN server
3. **TUN FD conflict** - Even after race condition fix, there may be lingering issues
4. **OpenVPN 3 configuration** - Config modifications may be incorrect

### Investigation Steps
- Check logs for "OpenVPN 3 service connection established successfully"
- Check for "connection failed" or "Exception in connection thread"
- Verify credentials are correct and activated
- Check if TUN FD is valid when passed to OpenVPN 3

---

## Issue 2: Multi-Tunnel Routing Broken

### Problem
After the race condition fix, we stop reading from TUN when OpenVPN 3 connects. This breaks the multi-tunnel routing architecture:

**Before Fix:**
1. `VpnEngineService.readPacketsFromTun()` reads packets
2. `PacketRouter.routePacket()` parses packet, gets UID, looks up app rule
3. Routes packet to specific tunnel via `VpnConnectionManager.sendPacketToTunnel(tunnelId, packet)`
4. Different apps can route to different VPN servers

**After Fix:**
1. `VpnEngineService.readPacketsFromTun()` stops when OpenVPN 3 connects
2. OpenVPN 3 reads packets directly from TUN FD
3. OpenVPN 3 doesn't know about app routing rules
4. **ALL packets go through ONE OpenVPN 3 connection** (the first one to connect)
5. **Multi-tunnel routing is completely broken**

### Root Cause
OpenVPN 3 ClientAPI's `connect()` method expects exclusive access to the TUN FD and manages packet I/O automatically. It doesn't support:
- Per-app routing rules
- Multiple simultaneous connections to different servers
- Custom packet routing logic

### Current Architecture Limitation
Android's `VpnService` creates a SINGLE TUN interface. When OpenVPN 3 manages this interface directly, it can only route through ONE connection. We can't have:
- App A → UK VPN server
- App B → FR VPN server
- App C → Direct internet

When OpenVPN 3 is connected, ALL traffic goes through that ONE connection.

---

## Solutions

### Option 1: Keep Packet Routing Active (Partially Fix Race Condition)
**Approach:** Don't stop reading from TUN, but coordinate with OpenVPN 3
- Keep `readPacketsFromTun()` running
- Route packets based on app rules
- Send packets to appropriate OpenVPN 3 connections
- But OpenVPN 3 also reads from TUN = race condition returns

**Trade-off:** Race condition returns, but multi-tunnel routing works

### Option 2: Single Tunnel Per VpnService (Simplified Architecture)
**Approach:** Only support one VPN connection at a time
- User selects which apps use VPN
- All VPN traffic goes through one connection
- Can't route different apps to different servers
- Simpler, but loses core feature

**Trade-off:** Loses multi-tunnel routing capability

### Option 3: Use OpenVPN 3 as Library (Not ClientAPI)
**Approach:** Use OpenVPN 3's lower-level APIs
- Manual packet I/O
- Route packets based on app rules before sending to OpenVPN 3
- Multiple OpenVPN 3 instances (one per tunnel)
- Each instance processes packets for specific apps

**Trade-off:** Major refactoring required, but full control

### Option 4: Hybrid Approach - Route Before OpenVPN 3
**Approach:** Route packets in VpnEngineService, then send to OpenVPN 3
- Keep `readPacketsFromTun()` running
- Route packets based on app rules
- Send packets to OpenVPN 3 via `sendPacket()` (not through TUN FD)
- OpenVPN 3 doesn't read from TUN, only writes to it

**Trade-off:** Requires OpenVPN 3 to support manual packet feeding (may not be possible with ClientAPI)

### Option 5: Multiple TUN Interfaces (Not Possible on Android)
**Approach:** Create separate TUN interface per VPN connection
- Android doesn't support multiple TUN interfaces
- Would need root access or system-level changes

**Trade-off:** Not feasible on standard Android

---

## Recommended Solution

**Option 3: Refactor to use OpenVPN 3 as a library** (long-term)

This provides:
- Full control over packet I/O
- Ability to route packets based on app rules
- Support for multiple simultaneous connections
- No race conditions

However, this requires:
- Significant refactoring
- Understanding OpenVPN 3's lower-level APIs
- Implementing packet I/O manually

**Short-term workaround:** Option 1 - Keep packet routing, accept race condition risk

This allows multi-tunnel routing to work while we plan the proper refactoring.


