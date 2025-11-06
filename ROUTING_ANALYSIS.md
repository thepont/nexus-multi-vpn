# Application-Level Routing Analysis

## Root Cause: UID Detection Failure

### Problem
The routing system fails because **UID detection cannot access `/proc/net` files**:
- Error: `EACCES (Permission denied)` when reading `/proc/net/tcp` and `/proc/net/udp`
- Android apps (including VPN services) don't have permission to read these files directly
- Result: All packets are sent to direct internet instead of VPN tunnels

### Impact
- **Tunnels connect successfully** (OpenVPN 3 works)
- **But routing fails** because `PacketRouter` cannot determine which app sent each packet
- Test shows: "Could not determine UID for packet" → all packets go to direct internet
- IP check returns AU (Australia) instead of GB (UK) because traffic isn't routed through VPN

## Current Architecture

### Flow
1. **VpnEngineService.startVpn()**:
   - Reads app rules from database
   - Calls `VpnService.Builder.addAllowedApplication(packageName)` for each app with a rule
   - Establishes TUN interface with split tunneling

2. **PacketRouter.routePacket()**:
   - Parses packet to get 5-tuple (srcIP, srcPort, destIP, destPort, protocol)
   - Tries to get UID via `ProcNetParser.readUidFromProcNet()` → **FAILS**
   - Falls back to `sendToDirectInternet()` → **All traffic bypasses VPN**

3. **VpnConnectionManager**:
   - Creates tunnels successfully
   - Tunnels connect to OpenVPN servers
   - But no packets are routed to them

## Solution Options

### Option 1: Connection Tracking Table (Recommended)
**Maintain a mapping of (srcIP, srcPort) → UID → tunnelId**

**Pros:**
- Works without special permissions
- Can track connections as they're established
- Real-time routing based on tracked connections

**Cons:**
- Need to populate table when connections are established
- May miss some connections if not tracked

**Implementation:**
1. When app rules are created, get UID from package name
2. When connections are established, track (srcIP, srcPort) → UID
3. When packets arrive, look up UID from tracking table
4. Route to appropriate tunnel based on UID → tunnelId mapping

### Option 2: Use Multiple TUN Interfaces
**Create separate TUN interface per VPN tunnel**

**Pros:**
- Each app can be routed to specific tunnel via `addAllowedApplication()`
- No need for UID detection in packets

**Cons:**
- **Android only allows ONE active VPN at a time** - this won't work for multi-tunnel routing

### Option 3: Native Code with Elevated Privileges
**Use native code (JNI) to read /proc/net with elevated privileges**

**Pros:**
- Direct access to connection information
- Most accurate UID detection

**Cons:**
- Requires root or special SELinux permissions
- Not practical for non-rooted devices

### Option 4: Use Android Network APIs
**Use ConnectivityManager and NetworkCallback to track connections**

**Pros:**
- Official Android API
- No special permissions needed

**Cons:**
- May not provide real-time packet-level routing
- Connection tracking might be delayed

## Recommended Implementation: Connection Tracking Table

### Architecture
```
App Rule Created → Package Name → UID (via PackageManager)
                                     ↓
Connection Established → (srcIP, srcPort) → Store in ConnectionTable
                                     ↓
Packet Arrives → (srcIP, srcPort) → Lookup UID → Lookup Tunnel → Route
```

### Implementation Steps
1. **Create ConnectionTracker class**:
   - Maintains (srcIP, srcPort) → UID mapping
   - Populated when connections are detected or rules are created

2. **Modify PacketRouter**:
   - Use ConnectionTracker instead of ProcNetParser
   - Look up UID from connection table
   - Route to tunnel based on UID → tunnelId mapping

3. **Populate Connection Table**:
   - When app rules are created: get UID from package name
   - When packets arrive: try to infer UID from connection patterns
   - Use TrafficStats API to help track connections

## Current Status

### Fixed
- ✅ Test package name: Now uses actual package name instead of hardcoded "com.multiregionvpn.test"
- ✅ ProcNetParser: Removed `canRead()` check, now tries to read anyway (but still fails due to permissions)

### Still Broken
- ❌ UID detection: Cannot read /proc/net files (permission denied)
- ❌ Packet routing: All packets go to direct internet
- ❌ Multi-tunnel routing: Not working because packets can't be routed to correct tunnels

## Next Steps

1. **Implement ConnectionTracker class** to maintain UID mappings
2. **Modify PacketRouter** to use ConnectionTracker instead of ProcNetParser
3. **Populate connection table** when rules are created or connections detected
4. **Test routing** to verify packets are routed to correct tunnels


