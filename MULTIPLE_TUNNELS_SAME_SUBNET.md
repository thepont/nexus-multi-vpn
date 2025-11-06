# Handling Multiple Tunnels with Same Subnet

## Problem Statement

When multiple OpenVPN tunnels receive IP addresses from the **same subnet** (e.g., both UK and FR tunnels get `10.100.0.2/16` and `10.100.0.3/16`), we face a critical issue:

- **Android's VPN interface** can only have **one IP address per subnet**
- Adding multiple IPs from the same subnet may fail or cause conflicts
- We need to route packets correctly even when tunnels share subnets

## Example Scenario

```
UK Tunnel:  10.100.0.2/16 (subnet: 10.100.0.0/16)
FR Tunnel:  10.100.0.3/16 (subnet: 10.100.0.0/16)  ← Same subnet!
US Tunnel:  10.102.0.2/16 (subnet: 10.102.0.0/16)  ← Different subnet
```

## Android VPN API Behavior

### Expected Behavior
When calling `builder.addAddress()` multiple times with IPs from the same subnet:
- **First IP added**: Succeeds
- **Subsequent IPs from same subnet**: May be ignored, rejected, or cause an exception

### Test Needed
We need to test:
```kotlin
val builder = VpnService.Builder()
builder.addAddress("10.100.0.2", 16)  // First IP - should work
builder.addAddress("10.100.0.3", 16)  // Same subnet - what happens?
builder.addAddress("10.100.0.4", 16)  // Same subnet - what happens?
builder.establish()  // Does this succeed? What IP is actually used?
```

## Solution Options

### Option A: One IP Per Subnet (Recommended)

**Approach**: Store IPs per subnet, not per tunnel. Only add one IP address per unique subnet to the interface.

```kotlin
// Data structure: subnet -> (tunnelId, ip)
private val subnetToTunnel = mutableMapOf<String, Pair<String, IpAddress>>()

fun onTunnelIpReceived(tunnelId: String, ip: String, prefixLength: Int) {
    val subnet = calculateSubnet(ip, prefixLength)  // e.g., "10.100.0.0/16"
    
    if (subnetToTunnel.containsKey(subnet)) {
        // Subnet already exists - choose strategy:
        // 1. Keep first tunnel's IP (ignore new one)
        // 2. Replace with new tunnel's IP (if new tunnel is more important)
        // 3. Use round-robin or load balancing
        
        val existing = subnetToTunnel[subnet]!!
        Log.w(TAG, "Tunnel $tunnelId IP $ip conflicts with existing ${existing.first} IP ${existing.second.address}")
        Log.w(TAG, "Both are in subnet $subnet - keeping first tunnel's IP")
        return  // Keep existing IP
    }
    
    subnetToTunnel[subnet] = tunnelId to IpAddress(ip, prefixLength)
}

private fun calculateSubnet(ip: String, prefixLength: Int): String {
    // Calculate subnet from IP and prefix length
    // e.g., "10.100.0.2/16" -> "10.100.0.0/16"
    val parts = ip.split(".")
    val subnet = when (prefixLength) {
        16 -> "${parts[0]}.${parts[1]}.0.0/$prefixLength"
        24 -> "${parts[0]}.${parts[1]}.${parts[2]}.0/$prefixLength"
        8 -> "${parts[0]}.0.0.0/$prefixLength"
        else -> {
            // Complex calculation for non-standard prefixes
            // For now, use IP address as-is (may need proper subnet calculation)
            ip.split("/")[0] + "/$prefixLength"
        }
    }
    return subnet
}
```

**Pros:**
- ✅ Avoids subnet conflicts
- ✅ One IP per subnet (standard network behavior)
- ✅ Simple to implement

**Cons:**
- ❌ Multiple tunnels in same subnet share one IP
- ❌ Routing complexity: Need to route packets to correct tunnel even with shared IP

### Option B: Use Primary Tunnel Per Subnet

**Approach**: When multiple tunnels share a subnet, designate one as "primary" and use its IP. Route packets to the correct tunnel based on connection tracking.

```kotlin
private val subnetToPrimaryTunnel = mutableMapOf<String, String>()
private val tunnelToIp = mutableMapOf<String, IpAddress>()

fun onTunnelIpReceived(tunnelId: String, ip: String, prefixLength: Int) {
    val subnet = calculateSubnet(ip, prefixLength)
    tunnelToIp[tunnelId] = IpAddress(ip, prefixLength)
    
    if (!subnetToPrimaryTunnel.containsKey(subnet)) {
        // First tunnel in this subnet - it becomes primary
        subnetToPrimaryTunnel[subnet] = tunnelId
        Log.d(TAG, "Tunnel $tunnelId is primary for subnet $subnet")
    } else {
        // Another tunnel in same subnet - becomes secondary
        val primary = subnetToPrimaryTunnel[subnet]!!
        Log.d(TAG, "Tunnel $tunnelId is secondary for subnet $subnet (primary: $primary)")
    }
}

// When establishing interface, only add primary tunnel IPs
private fun establishInterfaceWithPrimaryIps() {
    val builder = Builder()
    
    // Only add one IP per subnet (from primary tunnel)
    subnetToPrimaryTunnel.values.forEach { primaryTunnelId ->
        val ip = tunnelToIp[primaryTunnelId]!!
        builder.addAddress(ip.address, ip.prefixLength)
        Log.d(TAG, "Adding primary tunnel IP: ${ip.address}/${ip.prefixLength} for tunnel $primaryTunnelId")
    }
    
    vpnInterface = builder.establish()
}
```

**Routing Strategy:**
- Use `ConnectionTracker` to map `(srcIP, srcPort) -> tunnelId`
- Even if multiple tunnels share the same IP subnet, packets can be routed based on the connection's source port
- Connection tracking already maps `(srcIP, srcPort) -> UID -> tunnelId`

**Pros:**
- ✅ Avoids subnet conflicts
- ✅ Works with existing ConnectionTracker
- ✅ All tunnels can still route packets (via connection tracking)

**Cons:**
- ❌ Secondary tunnels in same subnet don't get their own IP on interface
- ❌ Routing depends on connection tracking (already working)

### Option C: Accept First IP, Log Warnings

**Approach**: Try to add all IPs, but accept that Android may reject duplicates. Log warnings for conflicts.

```kotlin
private val interfaceIps = mutableSetOf<String>()  // Track IPs added to interface

fun addIpToInterface(builder: Builder, ip: String, prefixLength: Int): Boolean {
    val ipKey = "$ip/$prefixLength"
    
    if (interfaceIps.contains(ipKey)) {
        Log.w(TAG, "IP $ipKey already added to interface - skipping")
        return false
    }
    
    try {
        builder.addAddress(ip, prefixLength)
        interfaceIps.add(ipKey)
        return true
    } catch (e: Exception) {
        Log.e(TAG, "Failed to add IP $ipKey to interface: ${e.message}")
        return false
    }
}
```

**Pros:**
- ✅ Simple: Let Android handle duplicates
- ✅ No complex logic needed

**Cons:**
- ❌ May fail silently if Android rejects duplicates
- ❌ Unpredictable behavior

### Option D: Use Different Routing Based on Tunnel ID

**Approach**: Even with shared subnet/IP, use tunnel-specific routing rules or connection tracking to route packets correctly.

Since we already have `ConnectionTracker` that maps `(srcIP, srcPort) -> UID -> tunnelId`, we can:
1. Add only one IP per subnet to interface (Option B)
2. Route packets based on connection tracking (already implemented)
3. All tunnels in same subnet can still work because routing is based on connection, not IP

**This is essentially what we already do!** Our `PacketRouter` uses `ConnectionTracker` to determine which tunnel to use, not the destination IP.

## Recommended Solution: Option B (Primary Tunnel Per Subnet)

### Why This Works

1. **Subnet Conflict Avoidance**: Only one IP per subnet added to interface
2. **Existing Routing Works**: `ConnectionTracker` already maps connections to tunnels
3. **No Breaking Changes**: Compatible with current architecture

### Implementation

```kotlin
// In VpnEngineService.kt

data class TunnelIpAddress(
    val tunnelId: String,
    val ip: String,
    val prefixLength: Int,
    val subnet: String
)

private val tunnelIps = ConcurrentHashMap<String, TunnelIpAddress>()
private val subnetToPrimaryTunnel = ConcurrentHashMap<String, String>()

// Called from JNI when tun_builder_add_address() is invoked
fun onTunnelIpReceived(tunnelId: String, ip: String, prefixLength: Int) {
    val subnet = calculateSubnet(ip, prefixLength)
    val tunnelIp = TunnelIpAddress(tunnelId, ip, prefixLength, subnet)
    
    tunnelIps[tunnelId] = tunnelIp
    
    // Determine primary tunnel for this subnet
    subnetToPrimaryTunnel.computeIfAbsent(subnet) { tunnelId }
    
    val primaryTunnel = subnetToPrimaryTunnel[subnet]!!
    if (primaryTunnel == tunnelId) {
        Log.d(TAG, "✅ Tunnel $tunnelId is PRIMARY for subnet $subnet (IP: $ip)")
    } else {
        Log.w(TAG, "⚠️  Tunnel $tunnelId is SECONDARY for subnet $subnet (primary: $primaryTunnel)")
        Log.w(TAG, "   Using primary tunnel's IP on interface, but routing via ConnectionTracker")
    }
    
    // Check if we should re-establish interface
    checkAndReestablishInterface()
}

private fun calculateSubnet(ip: String, prefixLength: Int): String {
    // Simplified: For /16, use first two octets
    // For production, use proper subnet calculation
    val parts = ip.split(".")
    return when (prefixLength) {
        16 -> "${parts[0]}.${parts[1]}.0.0/$prefixLength"
        24 -> "${parts[0]}.${parts[1]}.${parts[2]}.0/$prefixLength"
        8 -> "${parts[0]}.0.0.0/$prefixLength"
        else -> {
            // For non-standard, use IP address (may need proper calculation)
            val ipOnly = ip.split("/")[0]
            "$ipOnly/$prefixLength"
        }
    }
}

private fun establishInterfaceWithPrimaryIps() {
    if (subnetToPrimaryTunnel.isEmpty()) {
        Log.w(TAG, "No tunnel IPs available - cannot establish interface")
        return
    }
    
    // Close existing interface if any
    vpnInterface?.close()
    
    val builder = Builder()
    builder.setSession("MultiRegionVPN")
    
    // Add only primary tunnel IPs (one per subnet)
    val addedSubnets = mutableSetOf<String>()
    subnetToPrimaryTunnel.values.forEach { primaryTunnelId ->
        val tunnelIp = tunnelIps[primaryTunnelId]!!
        
        if (!addedSubnets.contains(tunnelIp.subnet)) {
            builder.addAddress(tunnelIp.ip, tunnelIp.prefixLength)
            addedSubnets.add(tunnelIp.subnet)
            Log.d(TAG, "✅ Added primary tunnel IP: ${tunnelIp.ip}/${tunnelIp.prefixLength} for tunnel $primaryTunnelId (subnet: ${tunnelIp.subnet})")
        }
    }
    
    // Add routes, DNS, allowed apps, etc.
    // ... rest of interface setup ...
    
    vpnInterface = builder.establish()
    Log.i(TAG, "✅ VPN interface established with ${addedSubnets.size} IP addresses (one per subnet)")
}
```

## Testing Plan

### Test Case 1: Same Subnet, Different IPs
```
UK Tunnel: 10.100.0.2/16
FR Tunnel: 10.100.0.3/16
Expected: Only one IP added to interface (primary tunnel's IP)
```

### Test Case 2: Different Subnets
```
UK Tunnel: 10.100.0.2/16
US Tunnel: 10.102.0.2/16
Expected: Both IPs added to interface
```

### Test Case 3: Multiple Tunnels, Mixed Subnets
```
UK Tunnel: 10.100.0.2/16
FR Tunnel: 10.100.0.3/16  (same subnet as UK)
US Tunnel: 10.102.0.2/16  (different subnet)
Expected: Two IPs added (UK primary for 10.100.0.0/16, US for 10.102.0.0/16)
```

### Test Case 4: Routing Verification
```
- UK and FR tunnels share subnet 10.100.0.0/16
- Interface has UK tunnel's IP (10.100.0.2/16)
- Packets from apps routed to FR tunnel should still work
- Verify: ConnectionTracker routes packets correctly despite shared IP
```

## Open Questions

1. **Android Behavior**: What exactly happens if we call `addAddress()` twice with same subnet?
   - Does it throw an exception?
   - Does it silently ignore?
   - Does it replace the first IP?

2. **Subnet Calculation**: Do we need proper subnet calculation library, or is simple string manipulation sufficient?

3. **Primary Tunnel Selection**: Should we use first-come-first-served, or prioritize certain tunnels?

4. **Connection Tracking**: Does our existing `ConnectionTracker` handle shared subnet correctly?

## Next Steps

1. **Test Android Behavior**: Create test to see what happens with duplicate subnets
2. **Implement Subnet Tracking**: Add subnet calculation and primary tunnel selection
3. **Update Interface Establishment**: Only add primary tunnel IPs
4. **Verify Routing**: Test that packets route correctly even with shared subnets


