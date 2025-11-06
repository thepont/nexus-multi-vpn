# OpenVPN 3 External TUN Implementation Plan

## Overview

Based on expert feedback, we need to properly implement OpenVPN 3's `ExternalTun::Factory` interface instead of hacking `TunBuilderBase`. This is the **official, documented way** to provide custom TUN implementations to OpenVPN 3 Core.

---

## The Problem We're Solving

### What Went Wrong Before

```cpp
// Our previous approach (WRONG):
class OpenVPNClient : public TunBuilderBase {
    virtual int tun_builder_establish() override {
        // Return socketpair FD
        return socketpairFd;  // ❌ OpenVPN 3 wasn't polling this!
    }
};
```

**Issue:** OpenVPN 3 ClientAPI expects to own the TUN device. When we returned a socket pair FD, it accepted it but didn't actively poll it in its event loop. Result: DNS queries sent but never read → failures.

### The Correct Approach

```cpp
// Correct approach (ExternalTun::Factory):
class CustomExternalTunFactory : public ExternalTun::Factory {
    virtual TunClientFactory* new_tun_factory(const Config&, const OptionList&) override {
        // Create socketpair
        // Return custom TunClient that OpenVPN 3 will actively poll
        return customTunClient;  // ✅ OpenVPN 3 WILL poll this!
    }
};
```

**Why This Works:** `ExternalTun::Factory` explicitly tells OpenVPN 3 that we're providing an external TUN implementation. OpenVPN 3's event loop is designed to actively poll external TUN file descriptors.

---

## Architecture

### Current Architecture (TunBuilderBase - BROKEN)

```
┌────────────────────────────────────────────────────────┐
│         OpenVPN 3 ClientAPI                            │
│                                                        │
│   ┌──────────────────────────────────────┐            │
│   │  Event Loop (epoll/select)           │            │
│   │                                      │            │
│   │  Polls:                              │            │
│   │  • Control socket (to VPN server)  │            │
│   │  • Internal timers                  │            │
│   │  • ❌ NOT our socketpair FD!        │            │
│   └──────────────────────────────────────┘            │
│                                                        │
│   TunBuilderBase (our OpenVPNClient)                   │
│   • tun_builder_establish() returns FD                 │
│   • ❌ But OpenVPN doesn't poll it!                    │
└────────────────────────────────────────────────────────┘
                    │
                    │ Socket Pair FD (ignored!)
                    ▼
┌────────────────────────────────────────────────────────┐
│         VpnConnectionManager                           │
│         • Waiting for packets                          │
│         • ❌ Never receives anything!                   │
└────────────────────────────────────────────────────────┘
```

### New Architecture (ExternalTun::Factory - CORRECT)

```
┌────────────────────────────────────────────────────────┐
│         OpenVPN 3 ClientAPI                            │
│         (OPENVPN_EXTERNAL_TUN_FACTORY enabled)         │
│                                                        │
│   ┌──────────────────────────────────────┐            │
│   │  Event Loop (epoll/select)           │            │
│   │                                      │            │
│   │  Polls:                              │            │
│   │  • Control socket (to VPN server)  │            │
│   │  • Internal timers                  │            │
│   │  • ✅ External TUN FD (our FD!)     │            │
│   └──────────────────────────────────────┘            │
│                                                        │
│   ExternalTun::Factory                                 │
│   • new_tun_factory() creates TunClient                │
│   • TunClient provides FD for I/O                      │
│   • ✅ OpenVPN ACTIVELY polls this FD!                 │
└────────────────────────────────────────────────────────┘
                    │
                    │ Socket Pair FD (actively polled!)
                    │ Library End: OpenVPN reads/writes
                    │ App End: Our code reads/writes
                    ▼
┌────────────────────────────────────────────────────────┐
│         VpnConnectionManager                           │
│         • Receives decrypted packets                   │
│         • Sends plaintext packets                      │
│         • ✅ Everything works!                          │
└────────────────────────────────────────────────────────┘
```

---

## Implementation Steps

### Step 1: Enable OPENVPN_EXTERNAL_TUN_FACTORY

**File:** `app/src/main/cpp/CMakeLists.txt`

```cmake
# Add compilation flag to enable external TUN factory
add_compile_definitions(OPENVPN_EXTERNAL_TUN_FACTORY)

# Remove USE_TUN_BUILDER (conflicts with external TUN)
# (If it's defined, remove it)
```

**Why:** This tells OpenVPN 3 to use external TUN mode instead of standard TUN builder mode.

### Step 2: Implement CustomExternalTunFactory

**File:** `app/src/main/cpp/external_tun_factory.h` (DONE ✅)

Key methods:
- `new_tun_factory()`: Create and return a `TunClientFactory`
- The returned factory creates `TunClient` instances
- `TunClient` provides the FD and handles I/O

### Step 3: Implement CustomTunClient

**File:** `app/src/main/cpp/external_tun_client.h` (TODO)

```cpp
class CustomTunClient : public TunClient {
public:
    CustomTunClient(int app_fd, int lib_fd) 
        : app_fd_(app_fd), lib_fd_(lib_fd) {
    }
    
    // OpenVPN 3 calls this to get the FD to poll
    virtual int tun_fd() const override {
        return lib_fd_;  // OpenVPN will poll THIS FD!
    }
    
    // OpenVPN 3 calls this when data is available
    virtual void tun_read_handler() override {
        // Read from lib_fd_
        // This is where OpenVPN receives packets FROM us
    }
    
    // OpenVPN 3 calls this to write decrypted packets
    virtual void tun_write_handler(const Buffer& buf) override {
        // Write to lib_fd_
        // This is where OpenVPN sends decrypted packets TO us
    }
    
private:
    int app_fd_;  // Our application's end
    int lib_fd_;  // OpenVPN's end
};
```

### Step 4: Update OpenVPNClient Class

**File:** `app/src/main/cpp/openvpn_wrapper.cpp`

Changes needed:
1. Remove inheritance from `TunBuilderBase`
2. Create `CustomExternalTunFactory` instance
3. Pass factory to `ClientOptions::Config`

```cpp
// OLD (TunBuilderBase):
class OpenVPNClient : public TunBuilderBase {
    // ...
};

// NEW (ExternalTun::Factory):
class OpenVPNClient {
    CustomExternalTunFactory::Ptr tunFactory_;
    
    void connect() {
        // Create factory
        tunFactory_ = new CustomExternalTunFactory(tunnelId_);
        
        // Pass to ClientOptions
        ClientOptions::Config config;
        config.extern_tun_factory = tunFactory_.get();
        
        // ... rest of connection logic
    }
};
```

### Step 5: Update JNI Interface

**File:** `app/src/main/cpp/openvpn_jni.cpp`

Changes needed:
1. Remove `setTunFileDescriptor()` (no longer needed)
2. Add `getAppFd()` to retrieve the app end of socketpair
3. Update `createPipe()` to return app FD

```cpp
// NEW: Get the app FD for packet I/O
JNIEXPORT jint JNICALL
Java_com_multiregionvpn_core_vpnclient_NativeOpenVpnClient_getAppFd(
    JNIEnv* env,
    jobject thiz,
    jstring tunnelId) {
    
    const char* tunnelIdStr = env->GetStringUTFChars(tunnelId, nullptr);
    std::string tid(tunnelIdStr);
    env->ReleaseStringUTFChars(tunnelId, tunnelIdStr);
    
    auto it = sessions.find(tid);
    if (it != sessions.end()) {
        return it->second->tunFactory->getAppFd();
    }
    
    return -1;
}
```

### Step 6: Update NativeOpenVpnClient.kt

**File:** `app/src/main/java/com/multiregionvpn/core/vpnclient/NativeOpenVpnClient.kt`

Changes needed:
1. Remove TUN FD parameter from `connect()`
2. Add `getAppFd()` native method
3. Get app FD AFTER connection starts

```kotlin
// OLD:
external fun nativeConnect(
    tunnelId: String,
    ovpnConfig: String,
    authFilePath: String?,
    tunFd: Int  // ❌ Remove this
): Long

// NEW:
external fun nativeConnect(
    tunnelId: String,
    ovpnConfig: String,
    authFilePath: String?
): Long

external fun getAppFd(tunnelId: String): Int  // ✅ Add this

override suspend fun connect(ovpnConfig: String, authFilePath: String?): Boolean {
    // Connect (this creates socketpair internally)
    val handle = nativeConnect(tunnelId, ovpnConfig, authFilePath)
    
    if (handle == 0L) {
        return false
    }
    
    // Get the app FD for packet I/O
    val appFd = getAppFd(tunnelId)
    
    // Start reading from appFd
    startPacketReading(appFd)
    
    return true
}
```

### Step 7: Update VpnConnectionManager

**File:** `app/src/main/java/com/multiregionvpn/core/VpnConnectionManager.kt`

Changes needed:
1. Remove `createPipe()` call (socketpair created by C++)
2. Get app FD from OpenVPN client
3. Use app FD for packet I/O

```kotlin
// OLD:
val (kotlinFd, openvpnFd) = createPipe(tunnelId)
client.connect(config, authFile, openvpnFd)  // Pass OpenVPN FD

// NEW:
client.connect(config, authFile)  // No FD needed!
val appFd = client.getAppFd()  // Get FD from OpenVPN client
startPipeReader(tunnelId, appFd)  // Use app FD for reading
```

---

## Testing Plan

### Phase 1: Compilation

```bash
cd /home/pont/projects/multi-region-vpn
./gradlew :app:assembleDebug
```

**Expected:** Compiles without errors

**Common Issues:**
- Missing includes for `ExternalTun::Factory`
- Incorrect interface implementation
- Linking errors

### Phase 2: Single Tunnel Test

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.multiregionvpn.NordVpnE2ETest#test_routesToUK
```

**Expected:** DNS resolution works, HTTP requests succeed

**Key Logs to Watch:**
```
CustomExternalTunFactory::tun_builder_establish()
✅ Socket pair created successfully
✅ Returning library FD to OpenVPN 3 Core
OpenVPN will read/write encrypted packets here
Our app will read/write decrypted packets to FD X
```

### Phase 3: Multi-Tunnel Test

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.multiregionvpn.NordVpnE2ETest#test_multiTunnel_BothUKandFRActive
```

**Expected:** Both tunnels active, packets routed correctly

### Phase 4: WireGuard + OpenVPN Comparison

```bash
# Test WireGuard (should still work)
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.multiregionvpn.WireGuardDockerE2ETest

# Test OpenVPN (should now work!)
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.multiregionvpn.NordVpnE2ETest
```

**Expected:** BOTH protocols working!

---

## Expected Outcomes

### Before (TunBuilderBase)
```
❌ OpenVPN: DNS fails (UnknownHostException)
✅ WireGuard: Works perfectly
```

### After (ExternalTun::Factory)
```
✅ OpenVPN: DNS works! HTTP succeeds!
✅ WireGuard: Still works perfectly
✅ Both protocols: Multi-tunnel support
✅ Protocol detection: Automatic switching
```

---

## Risks & Mitigation

### Risk 1: Complex Interface

**Risk:** `ExternalTun::Factory` is more complex than `TunBuilderBase`

**Mitigation:** 
- Study OpenVPN 3 examples
- Start with minimal implementation
- Add features incrementally

### Risk 2: Breaking WireGuard

**Risk:** Changes to `VpnConnectionManager` might break WireGuard

**Mitigation:**
- Keep WireGuard path separate
- Test WireGuard after each change
- Use protocol detection to isolate changes

### Risk 3: Compilation Flags

**Risk:** `OPENVPN_EXTERNAL_TUN_FACTORY` might conflict with other flags

**Mitigation:**
- Check CMakeLists.txt for conflicts
- Remove `USE_TUN_BUILDER` if present
- Test compilation thoroughly

---

## Success Criteria

✅ **Compilation:** No errors with `OPENVPN_EXTERNAL_TUN_FACTORY` enabled  
✅ **OpenVPN DNS:** Resolves hostnames correctly  
✅ **OpenVPN HTTP:** Requests succeed  
✅ **OpenVPN Multi-Tunnel:** Both UK + FR tunnels active  
✅ **WireGuard:** Still works (not broken)  
✅ **Protocol Detection:** Correctly chooses OpenVPN vs WireGuard  
✅ **E2E Tests:** All tests passing

---

## Timeline Estimate

- **Step 1-2:** 1 hour (CMake + header files)
- **Step 3-4:** 2-3 hours (TunClient + OpenVPNClient refactor)
- **Step 5-6:** 1-2 hours (JNI + Kotlin updates)
- **Step 7:** 1 hour (VpnConnectionManager updates)
- **Testing:** 2-3 hours (debugging + validation)

**Total:** 7-10 hours

---

## References

- OpenVPN 3 Core: `/home/pont/projects/multi-region-vpn/libs/openvpn3/`
- External TUN Interface: `openvpn/tun/extern/fw.hpp`
- External TUN Config: `openvpn/tun/extern/config.hpp`
- Client Options: `openvpn/client/cliopt.hpp`

---

**Status:** Planning Complete  
**Next Action:** Implement Step 1 (Enable OPENVPN_EXTERNAL_TUN_FACTORY)  
**Date:** November 6, 2025

