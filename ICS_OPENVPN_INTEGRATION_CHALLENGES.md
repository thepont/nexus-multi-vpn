# ics-openvpn Integration Challenges

## Date
November 6, 2025

## Investigation Results

### What We Found

1. **ics-openvpn is an Application, Not a Library**
   - The `:main` module uses `android.application` plugin
   - It's the complete "OpenVPN for Android" app
   - Not designed to be integrated as a dependency

2. **Version Conflicts**
   - ics-openvpn requires Android Gradle Plugin 8.9.3
   - Our project uses AGP 8.2.0
   - Resolving this requires upgrading our entire build system

3. **Complex Architecture**
   - ics-openvpn has many modules and dependencies
   - Designed as a standalone app with UI, activities, etc.
   - Would require significant refactoring to extract just the VPN core

### Integration Attempts

1. ✅ Added as Git submodule
2. ✅ Updated settings.gradle.kts
3. ✅ Added dependency to app/build.gradle.kts
4. ✅ Copied version catalog (libs.versions.toml)
5. ❌ **Blocked:** Plugin version conflicts
6. ❌ **Blocked:** Application vs Library architecture mismatch

### Time Investment

- **Expected:** 2-3 days
- **Actual (so far):** 1 hour, blocked on fundamental architectural issues
- **Remaining if we continue:** Unknown (likely 1-2 weeks of refactoring)

## Alternative: WireGuard

After attempting ics-openvpn integration, **WireGuard emerges as the superior choice**:

### Why WireGuard is Better

1. **Simpler Protocol**
   - Only ~4,000 lines of code (vs OpenVPN's ~100,000)
   - Easier to understand and debug
   - Less attack surface

2. **Better Performance**
   - Modern cryptography (Noise protocol, ChaCha20, Poly1305)
   - Faster handshakes
   - Lower latency
   - Better battery life on mobile

3. **Native Android Library**
   - `com.wireguard.android:tunnel` is a proper library
   - Designed for integration (not a standalone app)
   - Maintained by the WireGuard project
   - Clean, well-documented API

4. **Multi-Tunnel Support**
   - WireGuard supports multiple concurrent tunnels out-of-the-box
   - Each tunnel is independent
   - Easier routing logic

5. **Modern & Maintained**
   - Actively developed
   - Used by major VPN providers (Mullvad, IVPN, NordVPN)
   - Built into Linux kernel (5.6+)
   - Android 12+ has native WireGuard support

### WireGuard Integration Plan

#### Phase 1: Add WireGuard Library (30 minutes)
```kotlin
dependencies {
    implementation("com.wireguard.android:tunnel:1.0.20230706")
}
```

#### Phase 2: Create WireGuard Adapter (2-3 hours)
```kotlin
class WireGuardVpnClient : NativeOpenVpnClient {
    private var tunnel: Tunnel? = null
    
    override suspend fun connect(
        config: String,
        username: String,
        password: String
    ): Boolean {
        // Parse WireGuard config (simpler than OpenVPN)
        val wgConfig = Config.parse(config)
        
        // Create tunnel
        tunnel = Tunnel.create(wgConfig, tunFd)
        
        // Connect
        tunnel?.up()
        
        return tunnel?.state == Tunnel.State.UP
    }
    
    override fun sendPacket(packet: ByteArray) {
        // WireGuard handles this internally via TUN FD
        // No manual packet injection needed!
    }
}
```

#### Phase 3: Test (1-2 hours)
- Single tunnel test
- Multi-tunnel test
- Performance comparison

**Total Time: ~1 day** (vs weeks for ics-openvpn)

### WireGuard vs OpenVPN vs ics-openvpn

| Feature | OpenVPN 3 | ics-openvpn | WireGuard |
|---------|-----------|-------------|-----------|
| **Integration** | ❌ Complex (CMake, vcpkg) | ❌ Very Complex (app, not library) | ✅ Simple (single Gradle dependency) |
| **TUN Handling** | ❌ Expects to own TUN | ⚠️  Possible but complex | ✅ Flexible FD management |
| **Multi-Tunnel** | ❌ Not designed for it | ⚠️  Requires workarounds | ✅ Native support |
| **Performance** | Good | Good | ✅ **Excellent** |
| **Code Size** | Large (~100k LoC) | Large (full app) | ✅ Small (~4k LoC) |
| **Android Support** | ⚠️  Generic C++ | ✅ Native Android | ✅ Native Android |
| **Maintenance** | Active | Active | ✅ **Very Active** |
| **Provider Support** | Universal | Universal | ✅ Growing rapidly |

### Provider Compatibility

#### OpenVPN
- ✅ NordVPN, ExpressVPN, ProtonVPN, etc. (universal)

#### WireGuard
- ✅ Mullvad (native support)
- ✅ IVPN (native support)
- ✅ NordVPN (via NordLynx)
- ✅ ProtonVPN (native support)
- ✅ Surfshark (native support)
- ⚠️  Some older providers don't support it yet

**Note:** Most major providers have WireGuard support, and it's becoming the default.

### Migration Path

1. **Immediate:** Remove ics-openvpn submodule
2. **Day 1 Morning:** Add WireGuard library dependency
3. **Day 1 Afternoon:** Create WireGuardVpnClient adapter
4. **Day 2 Morning:** Update config parsing (WireGuard format)
5. **Day 2 Afternoon:** Test single tunnel
6. **Day 3:** Test multi-tunnel, run E2E suite

### Recommendation

**Switch to WireGuard** for the following reasons:

1. **Faster Development:** 1 day vs weeks
2. **Better Performance:** Modern crypto, lower latency
3. **Simpler Code:** Easier to maintain and debug
4. **Native Multi-Tunnel:** Designed for our use case
5. **Future-Proof:** Industry is moving to WireGuard

### Trade-offs

**Pros:**
- ✅ Faster integration
- ✅ Better performance
- ✅ Simpler code
- ✅ Native multi-tunnel support
- ✅ More maintainable

**Cons:**
- ⚠️  Need to support WireGuard config format (in addition to OpenVPN)
- ⚠️  Not all VPN providers have WireGuard yet (but most do)
- ⚠️  Users familiar with OpenVPN need to adapt

### Solution for OpenVPN Support

If we still need OpenVPN support, we can:

1. **Primary:** Use WireGuard for all tunnels
2. **Fallback:** Keep OpenVPN 3 stub for providers without WireGuard
3. **Hybrid:** Let user choose per provider (WireGuard preferred)

Or:

1. **Focus on WireGuard first** (get multi-tunnel working)
2. **Add OpenVPN later** (if needed) using a different approach

## Conclusion

After investigating ics-openvpn integration, **WireGuard is the clear winner**:
- Simpler integration (1 day vs 1-2 weeks)
- Better performance
- Native multi-tunnel support
- Modern, future-proof technology

The complexity of integrating ics-openvpn as a library makes it impractical. WireGuard's clean API and library-first design makes it the ideal choice for this project.

## Next Steps

1. Remove ics-openvpn submodule
2. Document WireGuard integration plan
3. Present recommendation to user
4. Proceed with WireGuard if approved

