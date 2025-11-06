# Per-VPN DHCP Implementation Options

## Problem Statement

We need **per-VPN DHCP** - each tunnel must receive its own IP address and configuration from OpenVPN's DHCP (via `ifconfig-push` in PUSH_REPLY). This is critical because:
- Different VPN servers/regions may assign different subnets (e.g., UK: `10.100.0.2/16`, FR: `10.101.0.2/16`)
- Different subnets require different routing configurations
- Current single-interface approach can't handle multiple DHCP-assigned IPs

## Android VPN API Constraints

### Current Limitation
Android's `VpnService.Builder` allows **only one active VPN interface per app** at a time. When you call `builder.establish()`, it creates a single TUN interface that:
- Replaces any existing VPN interface from your app
- Can only have one IP address configured
- Routes all traffic through that single interface

### Android API Reality Check
```kotlin
// This is what we currently do:
val builder = VpnService.Builder()
builder.addAddress("10.0.0.2", 30)  // Only ONE address
builder.establish()  // Creates ONE interface

// If we try to establish another:
val builder2 = VpnService.Builder()
builder2.addAddress("10.100.0.2", 16)  // Different IP
builder2.establish()  // This CLOSES the first interface and creates a new one
```

**Key Constraint**: Android's VPN API is designed for **one VPN at a time**, not multiple simultaneous VPNs.

---

## Option 4A: Multiple VpnService Instances (Likely Not Possible)

### Approach
Create separate `VpnService` instances for each tunnel, each establishing its own interface.

### Investigation
```kotlin
// Would this work?
class VpnEngineServiceUK : VpnService() { /* UK tunnel */ }
class VpnEngineServiceFR : VpnService() { /* FR tunnel */ }
```

### Android System Behavior
- **VPN Permission**: Granted per-app, not per-service
- **Active VPN**: System tracks ONE active VPN per app
- **Multiple Services**: Each service would try to establish its own interface
- **Result**: Second `establish()` call would likely close the first interface

### Pros
- ✅ True per-tunnel DHCP (if it worked)
- ✅ Complete isolation between tunnels
- ✅ Each tunnel gets its own IP/subnet

### Cons
- ❌ **Likely not supported**: Android probably closes first interface when second is established
- ❌ **Complex architecture**: Multiple services, complex state management
- ❌ **User confusion**: Multiple VPN services in system settings
- ❌ **Permission issues**: VPN permission may not work across multiple services

### Feasibility: **LOW** ❌
Android's VPN API is designed for single VPN per app. This approach is unlikely to work.

---

## Option 4B: Single Interface with Multiple IP Addresses (Possible)

### Approach
Use Android's `VpnService.Builder.addAddress()` **multiple times** to add multiple IP addresses to the same interface.

### Investigation
```kotlin
val builder = VpnService.Builder()
builder.addAddress("10.100.0.2", 16)  // UK tunnel IP
builder.addAddress("10.101.0.2", 16)  // FR tunnel IP
builder.addAddress("10.102.0.2", 16)  // US tunnel IP
builder.establish()  // Single interface with multiple IPs
```

### Android API Support
Android's `VpnService.Builder` **does support** multiple `addAddress()` calls:
```java
// From Android VpnService.Builder source (conceptually):
public Builder addAddress(String address, int prefixLength) {
    // Adds to internal list - multiple addresses are allowed
    return this;
}
```

### Implementation
1. **Wait for all tunnels to connect** and receive DHCP IPs
2. **Collect all IP addresses** from `tun_builder_add_address()` callbacks
3. **Establish interface once** with all IP addresses
4. **Route packets** to correct tunnel based on destination IP

### Code Changes
```kotlin
// In VpnEngineService.kt
private val tunnelIps = mutableMapOf<String, IpAddress>()

// In tun_builder_add_address() callback (via JNI)
fun onTunnelIpReceived(tunnelId: String, ip: String, prefixLength: Int) {
    tunnelIps[tunnelId] = IpAddress(ip, prefixLength)
    
    // If all active tunnels have IPs, re-establish interface
    if (allTunnelsHaveIps()) {
        reestablishInterfaceWithAllIps()
    }
}

private fun reestablishInterfaceWithAllIps() {
    vpnInterface?.close()
    val builder = Builder()
    
    // Add all tunnel IPs to single interface
    tunnelIps.values.forEach { ip ->
        builder.addAddress(ip.address, ip.prefixLength)
    }
    
    // ... rest of setup
    vpnInterface = builder.establish()
}
```

### Pros
- ✅ **Supported by Android**: Multiple addresses on one interface is allowed
- ✅ **True per-tunnel DHCP**: Each tunnel gets its own DHCP-assigned IP
- ✅ **Single interface**: No complex multi-interface management
- ✅ **Works with current architecture**: Minimal changes needed

### Cons
- ❌ **Delayed establishment**: Interface created only after all tunnels connect
- ❌ **Re-establishment**: Must close and recreate interface when new tunnel connects
- ❌ **Routing complexity**: Need to route packets based on destination IP
- ❌ **User experience**: Interface appears only after all tunnels connect

### Routing Challenge
With multiple IPs on one interface, we need to determine which tunnel to use for each packet:
- **Option**: Route based on source IP (from ConnectionTracker)
- **Option**: Route based on destination IP (requires parsing packet headers)
- **Option**: Use tunnel-specific routing tables (if supported)

### Feasibility: **MEDIUM-HIGH** ✅
This is likely the most feasible approach for per-VPN DHCP.

---

## Option 4C: Per-Tunnel TUN Devices (Advanced Linux)

### Approach
Bypass Android's VPN API and create **raw TUN devices** directly using Linux system calls (requires root or system-level permissions).

### Implementation
```kotlin
// Use JNI to create TUN devices directly
external fun createTunDevice(interfaceName: String): Int  // Returns file descriptor

// In C++:
int createTunDevice(const char* name) {
    int fd = open("/dev/tun", O_RDWR);
    // Configure TUN device with ioctl
    struct ifreq ifr;
    strncpy(ifr.ifr_name, name, IFNAMSIZ);
    ifr.ifr_flags = IFF_TUN | IFF_NO_PI;
    ioctl(fd, TUNSETIFF, &ifr);
    return fd;
}
```

### Android Permissions Required
- **Root access**: Required to create TUN devices
- **System-level permissions**: May require system app signature
- **Network permissions**: Already have these

### Pros
- ✅ **True per-tunnel DHCP**: Each tunnel gets its own TUN device and IP
- ✅ **Complete control**: Full Linux TUN/TAP functionality
- ✅ **No Android limitations**: Bypass VpnService constraints

### Cons
- ❌ **Requires root**: Not feasible for standard Android apps
- ❌ **Security concerns**: Root access increases attack surface
- ❌ **Distribution issues**: Can't distribute on Play Store
- ❌ **Complex implementation**: Low-level Linux networking code

### Feasibility: **LOW** ❌ (unless root is acceptable)

---

## Option 4D: Virtual Interface Per Tunnel (Using Network Namespaces)

### Approach
Use Linux network namespaces to create isolated network contexts for each tunnel, each with its own TUN device.

### Implementation
```kotlin
// Create network namespace per tunnel
external fun createNetworkNamespace(name: String): Int
external fun createTunInNamespace(namespaceFd: Int, name: String): Int
```

### Requirements
- **Root access**: Required for network namespaces
- **Linux kernel support**: Namespaces must be enabled
- **Complex setup**: Requires managing multiple network contexts

### Pros
- ✅ **Complete isolation**: Each tunnel in its own network namespace
- ✅ **True per-tunnel DHCP**: Each namespace has its own IP
- ✅ **No conflicts**: Tunnels don't interfere with each other

### Cons
- ❌ **Requires root**: Same issues as Option 4C
- ❌ **Very complex**: Network namespaces are advanced Linux feature
- ❌ **Android compatibility**: May not work on all Android versions

### Feasibility: **VERY LOW** ❌

---

## Option 4E: Hybrid - Single Interface with Dynamic Re-establishment

### Approach
Use Option 4B (multiple IPs on one interface) but **re-establish the interface** when new tunnels connect or disconnect.

### Implementation Flow
1. **First tunnel connects** → Wait for IP → Establish interface with that IP
2. **Second tunnel connects** → Wait for IP → Close interface → Re-establish with both IPs
3. **Third tunnel connects** → Wait for IP → Close interface → Re-establish with all three IPs
4. **Tunnel disconnects** → Close interface → Re-establish with remaining IPs

### Pros
- ✅ **True per-tunnel DHCP**: Each tunnel gets its own IP
- ✅ **Always up-to-date**: Interface always has all active tunnel IPs
- ✅ **Works with Android API**: Uses supported multiple-address feature

### Cons
- ❌ **Frequent re-establishment**: Interface closes/reopens on every tunnel change
- ❌ **Connection disruption**: Brief disconnection during re-establishment
- ❌ **User experience**: VPN may appear to disconnect/reconnect
- ❌ **State management**: Must track which tunnels are active

### Optimization
- **Debounce re-establishment**: Wait for multiple tunnel changes before re-establishing
- **Background re-establishment**: Minimize user-visible disruption
- **Connection persistence**: Keep tunnel connections alive during re-establishment

### Feasibility: **MEDIUM** ⚠️
Works but has UX trade-offs.

---

## Option 4F: Single Interface with IP Aliasing (Recommended)

### Approach
Similar to Option 4B, but use **IP aliasing** to add multiple IPs to the interface **after** it's established.

### Investigation
Android's VPN interface may support adding IP addresses after establishment using:
- **Netlink sockets** (Linux kernel interface)
- **ip addr add** commands (via root/su)
- **NetworkManager API** (if available)

### Implementation
```kotlin
// Establish interface with first tunnel IP
val builder = Builder()
builder.addAddress(firstTunnelIp, prefixLength)
vpnInterface = builder.establish()

// Add subsequent tunnel IPs using IP aliasing
fun addTunnelIpToInterface(tunnelId: String, ip: String, prefixLength: Int) {
    // Use JNI to call Linux ip command or netlink
    nativeAddIpAddress(ip, prefixLength, vpnInterfaceName)
}
```

### Code Changes
```cpp
// In JNI/C++
extern "C" JNIEXPORT jboolean JNICALL
Java_com_multiregionvpn_core_VpnEngineService_nativeAddIpAddress(
    JNIEnv *env, jobject thiz, jstring ip, jint prefixLength, jstring interfaceName) {
    
    // Create netlink socket
    int sock = socket(AF_NETLINK, SOCK_RAW, NETLINK_ROUTE);
    
    // Build netlink message to add IP address
    struct nlmsghdr *nlh = ...;
    struct ifaddrmsg *ifa = ...;
    struct rtattr *rta = ...;
    
    // Send netlink message
    send(sock, nlh, nlh->nlmsg_len, 0);
    
    return JNI_TRUE;
}
```

### Pros
- ✅ **True per-tunnel DHCP**: Each tunnel gets its own IP
- ✅ **No re-establishment**: Interface stays stable
- ✅ **Dynamic**: Add/remove IPs as tunnels connect/disconnect
- ✅ **Better UX**: No interface disruption

### Cons
- ❌ **Requires root/netlink**: May need elevated permissions
- ❌ **Complex implementation**: Low-level Linux networking
- ❌ **Android compatibility**: May not work on all devices/versions
- ❌ **Platform-specific**: Different approaches for different Android versions

### Feasibility: **MEDIUM** ⚠️
Depends on Android version and permissions available.

---

## Recommendation

### Primary Recommendation: **Option 4B (Multiple IPs on Single Interface)**

**Rationale**:
1. **Supported by Android**: `VpnService.Builder` allows multiple `addAddress()` calls
2. **True per-VPN DHCP**: Each tunnel gets its own DHCP-assigned IP
3. **Minimal changes**: Works with current architecture
4. **No root required**: Uses standard Android VPN API

### Implementation Strategy

#### Phase 1: Collect Tunnel IPs
```kotlin
// Store tunnel IPs as they arrive
private val tunnelIps = ConcurrentHashMap<String, IpAddress>()

// In C++ wrapper, add JNI callback:
// When tun_builder_add_address() is called, notify Kotlin
fun onTunnelIpReceived(tunnelId: String, ip: String, prefixLength: Int) {
    tunnelIps[tunnelId] = IpAddress(ip, prefixLength)
    checkAndReestablishInterface()
}
```

#### Phase 2: Re-establish Interface with All IPs
```kotlin
private fun reestablishInterfaceWithAllIps() {
    if (tunnelIps.isEmpty()) return
    
    // Close current interface
    vpnInterface?.close()
    
    // Build new interface with all tunnel IPs
    val builder = Builder()
    builder.setSession("MultiRegionVPN")
    
    // Add all tunnel IPs
    tunnelIps.values.forEach { ip ->
        builder.addAddress(ip.address, ip.prefixLength)
        Log.d(TAG, "Adding tunnel IP: ${ip.address}/${ip.prefixLength}")
    }
    
    // ... add routes, DNS, allowed apps, etc.
    
    // Re-establish
    vpnInterface = builder.establish()
    
    // Re-initialize packet router
    vpnOutput = FileOutputStream(vpnInterface!!.fileDescriptor)
    initializePacketRouter()
    
    // Update TUN FD in all connections
    connectionManager.updateTunFileDescriptor(vpnInterface!!.fileDescriptor)
}
```

#### Phase 3: Handle Routing
With multiple IPs, we need to route packets correctly:
- **Source IP routing**: Use `ConnectionTracker` to map `(srcIP, srcPort) -> tunnelId`
- **Destination IP routing**: Parse packet headers to determine destination tunnel
- **Fallback**: Route to first available tunnel if destination unknown

### Alternative: Option 4E (Dynamic Re-establishment)
If Option 4B causes too many re-establishments, use Option 4E with debouncing:
- Wait for tunnel changes to stabilize (e.g., 2 seconds)
- Batch re-establishment of multiple tunnel changes
- Show "reconnecting" state during re-establishment

---

## Testing Plan

### Unit Tests
- [ ] Test multiple `addAddress()` calls on `VpnService.Builder`
- [ ] Test interface re-establishment with multiple IPs
- [ ] Test packet routing with multiple tunnel IPs

### Integration Tests
- [ ] Test with 2 tunnels (UK + FR) - verify both IPs on interface
- [ ] Test with 3 tunnels (UK + FR + US) - verify all IPs on interface
- [ ] Test tunnel disconnection - verify interface updates
- [ ] Test packet routing - verify packets go to correct tunnel

### E2E Tests
- [ ] Test DNS resolution with per-tunnel DHCP IPs
- [ ] Test connectivity with multiple tunnels active
- [ ] Test user experience (interface re-establishment visibility)

---

## Open Questions

1. **Android API Behavior**: Does `VpnService.Builder.addAddress()` actually support multiple addresses? (Need to test)
2. **Routing**: How does Android route packets when multiple IPs are on one interface?
3. **Re-establishment Performance**: How disruptive is interface re-establishment?
4. **IP Conflict Detection**: What happens if two tunnels get the same IP?
5. **Subnet Compatibility**: Can different subnets (e.g., `/16` and `/24`) coexist on one interface?

---

## Next Steps

1. **Test Android API**: Verify that multiple `addAddress()` calls work
2. **Implement IP Collection**: Add JNI callback to collect tunnel IPs
3. **Implement Re-establishment**: Add logic to re-establish interface with all IPs
4. **Test Routing**: Verify packets route correctly with multiple IPs
5. **Optimize UX**: Minimize visible disruption during re-establishment


