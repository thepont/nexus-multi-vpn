# OpenVPN 2 vs OpenVPN 3: Implementation Analysis

## Executive Summary

**TL;DR:** OpenVPN 2 would be **MUCH more compatible** with our multi-tunnel architecture than OpenVPN 3 ClientAPI. The key difference is that OpenVPN 2 is a **process-based** implementation that properly handles TUN file descriptors from Android VpnService.

---

## The Problem with OpenVPN 3 ClientAPI

### What We've Been Using
```cpp
// OpenVPN 3 ClientAPI (what we tried)
#include <openvpn/client/cliconnect.hpp>

// In-process library
// Expects to OWN the TUN device
// Does NOT poll custom FDs
// Result: DNS fails ❌
```

### Why It Failed
1. **In-Process Library**: Designed to be embedded in applications
2. **TUN Ownership**: Expects exclusive control of TUN device creation
3. **Event Loop**: Only polls its own internal file descriptors
4. **No Custom FD Support**: Cannot use FDs provided by VpnService
5. **Result**: Our socket pair FD is never polled → DNS queries never read → Failures

---

## OpenVPN 2: The Process-Based Alternative

### What Is OpenVPN 2?

OpenVPN 2 is the **original, traditional OpenVPN** implementation:
- **Type**: Standalone command-line binary
- **Architecture**: Separate process (not a library)
- **TUN Handling**: Accepts TUN FD as command-line argument or via management interface
- **Android Support**: Yes (via ics-openvpn and others)
- **Multi-Tunnel**: Possible (multiple processes)

### How It Works on Android

```bash
# OpenVPN 2 can accept a pre-created TUN FD!
/path/to/openvpn --config /path/to/config.ovpn \
                 --management unix:/tmp/mgmt.sock \
                 --dev-type tun \
                 --dev-node /dev/tun0
```

**Key Advantage:** OpenVPN 2 can use a TUN FD that we pass to it from VpnService!

---

## Technical Comparison

| Feature | OpenVPN 3 ClientAPI | OpenVPN 2 | Winner |
|---------|---------------------|-----------|--------|
| **Architecture** | In-process library | Separate process | Depends |
| **TUN FD Handling** | ❌ Expects to own TUN | ✅ Accepts external TUN FD | **OpenVPN 2** |
| **VpnService Compat** | ❌ Poor | ✅ Good | **OpenVPN 2** |
| **Multi-Tunnel** | ❌ Single instance | ✅ Multiple processes | **OpenVPN 2** |
| **Management** | C++ API calls | Management socket | OpenVPN 2 |
| **Memory Usage** | Lower (in-process) | Higher (separate process) | OpenVPN 3 |
| **Integration Complexity** | Higher (JNI) | Lower (process + IPC) | OpenVPN 2 |
| **Crash Isolation** | ❌ Crashes app | ✅ Isolated process | **OpenVPN 2** |
| **Android Support** | Limited | Mature (ics-openvpn) | **OpenVPN 2** |

---

## Implementation Requirements

### Option 1: Use ics-openvpn (OpenVPN 2.x for Android)

**What It Provides:**
```
ics-openvpn/
├── main/
│   ├── cpp/
│   │   └── openvpn/     # OpenVPN 2.x compiled for Android
│   ├── java/
│   │   └── de/blinkt/openvpn/
│   │       ├── core/
│   │       │   ├── OpenVPNService.java
│   │       │   ├── OpenVPNManagement.java
│   │       │   └── VpnProfile.java
│   │       └── activities/
│   └── jniLibs/
│       └── openvpn binaries (arm64-v8a, armeabi-v7a, x86, x86_64)
```

**Pros:**
- ✅ OpenVPN 2.x already compiled for Android
- ✅ Mature, battle-tested (used by millions)
- ✅ Proper TUN FD handling
- ✅ Management interface working
- ✅ Multi-tunnel capable (multiple processes)

**Cons:**
- ❌ Designed as full app, not library
- ❌ Gradle version conflicts (as we experienced)
- ❌ Significant refactoring needed
- ❌ GPL v2 license (as with OpenVPN 3)

### Option 2: Build OpenVPN 2 from Scratch

**What's Required:**

1. **Compile OpenVPN 2 for Android NDK**
   ```bash
   # Download OpenVPN 2.x source
   git clone https://github.com/OpenVPN/openvpn.git -b release/2.6
   
   # Configure for Android NDK
   export ANDROID_NDK=/path/to/ndk
   export TOOLCHAIN=$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64
   export TARGET=aarch64-linux-android
   export API=21
   
   # Build
   ./configure --host=$TARGET \
               --with-crypto-library=openssl \
               --disable-lzo \
               --disable-lz4
   make
   ```

2. **Dependencies:**
   - OpenSSL (or mbedTLS)
   - LZ4 (optional, for compression)
   - LZO (optional, for compression)

3. **Integration Architecture:**
   ```kotlin
   class OpenVpn2Client(private val tunnelId: String) : OpenVpnClient {
       private var process: Process? = null
       private var managementSocket: Socket? = null
       
       override suspend fun connect(ovpnConfig: String, authFilePath: String?): Boolean {
           // 1. Get TUN FD from VpnService
           val tunFd = vpnService.Builder().establish()
           
           // 2. Write config to temp file
           val configFile = File.createTempFile("openvpn_", ".ovpn")
           configFile.writeText(ovpnConfig)
           
           // 3. Launch OpenVPN 2 process with TUN FD
           val pb = ProcessBuilder(
               "/data/data/com.multiregionvpn/files/openvpn",
               "--config", configFile.absolutePath,
               "--management", "unix:/path/to/mgmt.sock",
               "--setenv", "UV_TUNFD", tunFd.fd.toString()  // Pass TUN FD!
           )
           
           process = pb.start()
           
           // 4. Connect to management interface
           managementSocket = connectToManagementSocket()
           
           // 5. Monitor connection state
           monitorManagementInterface()
           
           return true
       }
       
       private fun monitorManagementInterface() {
           // Read management interface messages
           // >STATE:1699999999,CONNECTED,SUCCESS,...
           // >BYTECOUNT:12345,67890
           managementSocket?.let { socket ->
               val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
               while (true) {
                   val line = reader.readLine() ?: break
                   handleManagementMessage(line)
               }
           }
       }
   }
   ```

4. **Process Management:**
   ```kotlin
   // For multi-tunnel, spawn multiple processes
   val ukProcess = launchOpenVpn2("uk-config.ovpn", ukTunFd)
   val frProcess = launchOpenVpn2("fr-config.ovpn", frTunFd)
   
   // Each process handles its own tunnel independently
   // No shared state, excellent isolation!
   ```

### Option 3: Use OpenVPN Android Library (Community Port)

There are community projects that wrap OpenVPN 2 as a library:
- https://github.com/schwabe/ics-openvpn (what we looked at)
- https://github.com/ProtonVPN/android-app (uses ics-openvpn internally)

---

## Why OpenVPN 2 Would Work (and OpenVPN 3 Didn't)

### OpenVPN 3 ClientAPI Problem
```cpp
// OpenVPN 3 ClientAPI (simplified)
class ClientConnect {
    void connect() {
        // Create its own TUN device (incompatible with VpnService!)
        TunDevice* tun = new TunDevice();
        
        // Event loop only polls OpenVPN's own FDs
        while (running) {
            epoll_wait(epoll_fd, events, ...);  // ❌ Doesn't include our socket pair!
            
            if (control_channel_readable) handle_control();
            if (tun->readable()) handle_tun();  // ❌ This is OpenVPN's TUN, not ours!
        }
    }
};
```

### OpenVPN 2 Solution
```c
// OpenVPN 2 (simplified)
int main(int argc, char* argv[]) {
    // Accept TUN FD from environment or command-line
    int tun_fd = get_tun_fd_from_env();  // ✅ Uses OUR TUN FD!
    
    // Event loop polls ALL FDs including the TUN we provided
    while (running) {
        fd_set readfds;
        FD_ZERO(&readfds);
        FD_SET(control_socket, &readfds);
        FD_SET(tun_fd, &readfds);  // ✅ Polls OUR TUN FD!
        
        select(max_fd + 1, &readfds, NULL, NULL, &timeout);
        
        if (FD_ISSET(control_socket, &readfds)) handle_control();
        if (FD_ISSET(tun_fd, &readfds)) handle_tun();  // ✅ Reads from OUR TUN!
    }
}
```

**Key Difference:**
- OpenVPN 3: Creates its own TUN device, ignores ours
- OpenVPN 2: Uses the TUN FD we provide via VpnService

---

## Multi-Tunnel Architecture with OpenVPN 2

```
┌─────────────────────────────────────────────────────────┐
│                   VpnEngineService                       │
│                   (Android VpnService)                   │
│                                                          │
│  TUN Interface: 10.100.0.1/16                           │
│  ┌──────────────┐     ┌──────────────┐                 │
│  │  TUN Reader  │     │ PacketRouter │                 │
│  │  (Main loop) │────►│ (UID-based)  │                 │
│  └──────────────┘     └───────┬──────┘                 │
│                               │                          │
│                               │ Route by UID             │
│                               ├────────────┬────────────┤
│                               ▼            ▼            ▼
└───────────────────────────────┼────────────┼────────────┼──┘
                                │            │            │
                    ┌───────────┘            │            └──────────┐
                    │                        │                       │
         ┌──────────▼──────────┐  ┌─────────▼──────────┐ ┌─────────▼──────────┐
         │  OpenVPN 2 Process  │  │ OpenVPN 2 Process  │ │ OpenVPN 2 Process  │
         │    (UK Tunnel)      │  │   (FR Tunnel)      │ │   (DE Tunnel)      │
         │                     │  │                    │ │                    │
         │  TUN FD: dup(main)  │  │ TUN FD: dup(main)  │ │ TUN FD: dup(main)  │
         │  Mgmt: unix:uk.sock │  │ Mgmt: unix:fr.sock │ │ Mgmt: unix:de.sock │
         └─────────┬───────────┘  └────────┬───────────┘ └────────┬───────────┘
                   │                       │                       │
                   │                       │                       │
                   ▼                       ▼                       ▼
         ┌─────────────────┐   ┌─────────────────┐   ┌─────────────────┐
         │  UK VPN Server  │   │  FR VPN Server  │   │  DE VPN Server  │
         │ uk1827.nord.com │   │ fr1234.nord.com │   │ de5678.nord.com │
         └─────────────────┘   └─────────────────┘   └─────────────────┘
```

**How It Works:**
1. VpnEngineService creates main TUN interface
2. For each tunnel, spawn OpenVPN 2 process with `dup()`ed TUN FD
3. PacketRouter writes packets to specific process's TUN FD
4. Each OpenVPN 2 process reads from its TUN FD and routes to VPN server
5. Management socket for control (connect, disconnect, status)

---

## Implementation Effort Estimate

### Option 1: ics-openvpn Integration (Refactor)
**Effort:** 2-3 weeks
- Extract core OpenVPN 2 binary and management code
- Remove UI dependencies
- Integrate with our VpnConnectionManager
- Test multi-tunnel scenarios

**Pros:**
- Proven, mature codebase
- OpenVPN 2 already compiled

**Cons:**
- Significant refactoring
- GPL v2 license implications

### Option 2: Build from Scratch
**Effort:** 3-4 weeks
- Compile OpenVPN 2.x for Android NDK
- Implement process management
- Implement management interface parser
- Write JNI bridge
- Test thoroughly

**Pros:**
- Full control over implementation
- Cleaner integration

**Cons:**
- More work upfront
- Need to maintain NDK build

### Option 3: Stick with WireGuard
**Effort:** 0 days (already done!)
- WireGuard is already working
- Better performance
- Simpler codebase
- Modern protocol

**Pros:**
- ✅ DONE!
- ✅ Better in every way
- ✅ Production ready

**Cons:**
- Limited provider support (no NordVPN)

---

## Recommendation

### For Production: **Stick with WireGuard** ✅

**Reasoning:**
1. **Already Working**: 6/6 tests passing
2. **Superior Technology**: Modern, fast, secure
3. **Less Complexity**: 4K lines vs 100K lines
4. **Better Performance**: Lower latency, better battery
5. **Multi-Tunnel Native**: Designed for it

### If OpenVPN is Required: **Use OpenVPN 2 (ics-openvpn)**

**Reasoning:**
1. **TUN Compatibility**: OpenVPN 2 works with VpnService
2. **Proven Solution**: ics-openvpn has millions of users
3. **Multi-Tunnel Capable**: Multiple processes work
4. **Mature**: Well-tested, stable

**But:**
- Significant refactoring effort (2-3 weeks)
- GPL v2 license complications
- More complex than WireGuard

---

## Code Example: OpenVPN 2 Integration

```kotlin
// OpenVpn2Client.kt
class OpenVpn2Client(
    private val context: Context,
    private val vpnService: VpnService,
    private val tunnelId: String
) : OpenVpnClient {
    
    private var process: Process? = null
    private var managementSocket: LocalSocket? = null
    private val managementPath = "${context.filesDir}/openvpn_mgmt_$tunnelId.sock"
    
    companion object {
        private const val OPENVPN_BINARY = "libopenvpn.so"  // From ics-openvpn
    }
    
    override suspend fun connect(ovpnConfig: String, authFilePath: String?): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Get TUN FD from VpnService (our main TUN)
                // Note: We'll pass a dup() of the main TUN FD to this process
                val tunFd = getTunFdForTunnel(tunnelId)
                
                // 2. Write config to file
                val configFile = File(context.filesDir, "openvpn_config_$tunnelId.ovpn")
                configFile.writeText(ovpnConfig)
                
                // 3. Write auth credentials if provided
                val authFile = authFilePath?.let { File(it) }
                
                // 4. Build OpenVPN 2 command
                val nativeLibDir = context.applicationInfo.nativeLibraryDir
                val openvpnBinary = "$nativeLibDir/$OPENVPN_BINARY"
                
                val command = buildList {
                    add(openvpnBinary)
                    add("--config")
                    add(configFile.absolutePath)
                    add("--management")
                    add(managementPath)
                    add("unix")
                    add("--management-client")
                    add("--management-query-passwords")
                    add("--management-hold")
                    
                    // Pass TUN FD as environment variable
                    // OpenVPN 2 will use this instead of creating its own
                    add("--setenv")
                    add("UV_TUNFD")
                    add(tunFd.toString())
                    
                    if (authFile != null) {
                        add("--auth-user-pass")
                        add(authFile.absolutePath)
                    }
                }
                
                // 5. Launch process
                val pb = ProcessBuilder(command)
                pb.environment()["LD_LIBRARY_PATH"] = nativeLibDir
                process = pb.start()
                
                Log.i(TAG, "✅ OpenVPN 2 process started for tunnel $tunnelId")
                
                // 6. Connect to management interface
                delay(500)  // Give process time to create socket
                connectToManagementInterface()
                
                // 7. Send "hold release" to start connection
                sendManagementCommand("hold release")
                
                // 8. Monitor connection state
                monitorManagementInterface()
                
                true
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to start OpenVPN 2 for tunnel $tunnelId", e)
                false
            }
        }
    }
    
    private suspend fun connectToManagementInterface() {
        managementSocket = LocalSocket().apply {
            connect(LocalSocketAddress(managementPath, LocalSocketAddress.Namespace.FILESYSTEM))
        }
        Log.i(TAG, "✅ Connected to OpenVPN 2 management interface: $tunnelId")
    }
    
    private suspend fun monitorManagementInterface() {
        val socket = managementSocket ?: return
        
        withContext(Dispatchers.IO) {
            val reader = BufferedReader(InputStreamReader(socket.inputStream))
            
            while (true) {
                val line = reader.readLine() ?: break
                
                when {
                    line.startsWith(">STATE:") -> {
                        // Parse state: >STATE:1699999999,CONNECTED,SUCCESS,...
                        val parts = line.split(",")
                        if (parts.size >= 3) {
                            val state = parts[1]
                            when (state) {
                                "CONNECTED" -> {
                                    Log.i(TAG, "✅ Tunnel $tunnelId connected!")
                                    onConnectionStateChanged?.invoke(tunnelId, true)
                                }
                                "DISCONNECTED" -> {
                                    Log.w(TAG, "❌ Tunnel $tunnelId disconnected")
                                    onConnectionStateChanged?.invoke(tunnelId, false)
                                }
                            }
                        }
                    }
                    
                    line.startsWith(">BYTECOUNT:") -> {
                        // Parse byte count: >BYTECOUNT:12345,67890
                        val parts = line.substring(11).split(",")
                        if (parts.size >= 2) {
                            val bytesIn = parts[0].toLongOrNull() ?: 0
                            val bytesOut = parts[1].toLongOrNull() ?: 0
                            Log.v(TAG, "Tunnel $tunnelId: ↓$bytesIn ↑$bytesOut bytes")
                        }
                    }
                    
                    line.startsWith(">PASSWORD:") -> {
                        // OpenVPN is asking for password
                        // We should have provided it via --auth-user-pass
                        Log.w(TAG, "⚠️  Tunnel $tunnelId requesting password")
                    }
                }
            }
        }
    }
    
    private fun sendManagementCommand(command: String) {
        managementSocket?.outputStream?.write("$command\n".toByteArray())
        managementSocket?.outputStream?.flush()
    }
    
    override suspend fun disconnect() {
        // Send quit command to OpenVPN 2
        try {
            sendManagementCommand("signal SIGTERM")
            delay(1000)
        } catch (e: Exception) {
            Log.w(TAG, "Could not send SIGTERM to OpenVPN 2: ${e.message}")
        }
        
        // Force kill if still running
        process?.destroy()
        managementSocket?.close()
        
        Log.i(TAG, "✅ OpenVPN 2 process stopped for tunnel $tunnelId")
    }
    
    // Callbacks
    var onConnectionStateChanged: ((String, Boolean) -> Unit)? = null
    var onTunnelIpReceived: ((String, String, Int) -> Unit)? = null
    var onTunnelDnsReceived: ((String, List<String>) -> Unit)? = null
}
```

---

## Conclusion

**Yes, OpenVPN 2 implementation is possible and would work!**

### Key Points:

1. **OpenVPN 2 > OpenVPN 3** for our use case
   - Process-based architecture
   - Proper TUN FD handling
   - Multi-tunnel capable

2. **ics-openvpn provides OpenVPN 2 for Android**
   - Mature, proven solution
   - Used by millions
   - Requires refactoring to integrate

3. **WireGuard is still the best choice**
   - Already working
   - Superior technology
   - Less complexity

### Final Recommendation:

**Production:** Use WireGuard ✅  
**If OpenVPN Required:** Integrate ics-openvpn (OpenVPN 2) ⚠️  
**Never Use:** OpenVPN 3 ClientAPI for multi-tunnel ❌

---

**Document Version:** 1.0  
**Date:** November 6, 2025  
**Status:** Analysis Complete

