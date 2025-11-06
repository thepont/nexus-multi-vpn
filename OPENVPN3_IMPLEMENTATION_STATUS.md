# OpenVPN 3 External TUN Implementation Status

## ✅ Completed Steps (1-3 of 7)

### Step 1: Enable OPENVPN_EXTERNAL_TUN_FACTORY ✅
**File:** `app/src/main/cpp/CMakeLists.txt`

```cmake
add_compile_definitions(OPENVPN_EXTERNAL_TUN_FACTORY)
```

**Status:** DONE ✅  
**Commit:** 1dae0db

### Step 2: CustomExternalTunFactory ✅
**File:** `app/src/main/cpp/external_tun_factory.h`

- Implements `ExternalTun::Factory::new_tun_factory()`
- Returns `CustomTunClientFactory`
- Provides `getAppFd()` for packet I/O

**Status:** DONE ✅  
**Commit:** cb6816f

### Step 3: CustomTunClient ✅
**File:** `app/src/main/cpp/custom_tun_client.h`

- Implements `TunClient` interface
- Creates socketpair in `tun_start()`
- OpenVPN 3 polls `lib_fd`
- Our app uses `app_fd`
- Implements `tun_send()`, `stop()`, etc.

**Status:** DONE ✅  
**Commit:** cb6816f

---

## 🚧 Remaining Steps (4-7)

### Step 4: Update openvpn_wrapper.cpp (IN PROGRESS)

**Changes Needed:**

1. **Include headers:**
```cpp
#ifdef OPENVPN_EXTERNAL_TUN_FACTORY
#include "external_tun_factory.h"
#include "custom_tun_client.h"
#endif
```

2. **Update OpenVpnSession structure:**
```cpp
struct OpenVpnSession {
    // ...
    #ifdef OPENVPN_EXTERNAL_TUN_FACTORY
    openvpn::CustomExternalTunFactory::Ptr tunFactory;
    #endif
};
```

3. **Create factory in session constructor:**
```cpp
OpenVpnSession::OpenVpnSession() : ... {
    #ifdef OPENVPN_EXTERNAL_TUN_FACTORY
    tunFactory = new openvpn::CustomExternalTunFactory(tunnelId);
    LOGI("Created CustomExternalTunFactory for tunnel: %s", tunnelId.c_str());
    #endif
}
```

4. **Key Challenge: AndroidOpenVPNClient**

Current code:
```cpp
class AndroidOpenVPNClient : public OpenVPNClient {
    // ... TunBuilderBase methods ...
};
```

**Problem:** With `OPENVPN_EXTERNAL_TUN_FACTORY` enabled, OpenVPN 3's `OpenVPNClient` class does NOT inherit from `TunBuilderBase`. Instead, we need to provide external TUN via factory.

**Solution Options:**

**Option A:** Conditional compilation
```cpp
#ifdef OPENVPN_EXTERNAL_TUN_FACTORY
// Don't inherit from TunBuilderBase
class AndroidOpenVPNClient : public OpenVPNClient {
    // Remove all tun_builder_* methods
};
#else
// Keep current TunBuilderBase approach
class AndroidOpenVPNClient : public OpenVPNClient {
    // Keep tun_builder_* methods
};
#endif
```

**Option B:** Remove TunBuilderBase entirely
- Since we're committing to external TUN, remove all `tun_builder_*` methods
- Always use `CustomExternalTunFactory`

**Recommendation:** Option B (clean break, commit to external TUN)

5. **Pass factory to OpenVPN 3:**

Looking at OpenVPN 3 ClientAPI source (cliopt.hpp):
```cpp
struct Config {
    #ifdef OPENVPN_EXTERNAL_TUN_FACTORY
    ExternalTun::Factory *extern_tun_factory = nullptr;
    #endif
};
```

So we need to set:
```cpp
session->config.extern_tun_factory = session->tunFactory.get();
```

**Where to add:** In `openvpn_wrapper_connect()`, before `eval_config()`:

```cpp
// Around line 1076, before eval_config()
#ifdef OPENVPN_EXTERNAL_TUN_FACTORY
session->config.extern_tun_factory = session->tunFactory.get();
LOGI("Set external TUN factory on config");
#endif
```

**Status:** NEEDS IMPLEMENTATION  
**Estimated Time:** 2-3 hours

---

### Step 5: Update JNI (openvpn_jni.cpp)

**Changes Needed:**

1. **Add getAppFd() function:**
```cpp
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
        #ifdef OPENVPN_EXTERNAL_TUN_FACTORY
        return it->second->tunFactory->getAppFd();
        #else
        return -1;
        #endif
    }
    
    return -1;
}
```

2. **Remove setTunFileDescriptor() (no longer needed):**
- With external TUN, socketpair is created internally
- No need to pass TUN FD from Kotlin

3. **Remove createPipe() (no longer needed):**
- Socketpair created by CustomTunClient

**Status:** NEEDS IMPLEMENTATION  
**Estimated Time:** 1 hour

---

### Step 6: Update NativeOpenVpnClient.kt

**Changes Needed:**

1. **Remove tunFd parameter from connect():**
```kotlin
// OLD:
external fun nativeConnect(
    tunnelId: String,
    ovpnConfig: String,
    authFilePath: String?,
    tunFd: Int  // ❌ Remove
): Long

// NEW:
external fun nativeConnect(
    tunnelId: String,
    ovpnConfig: String,
    authFilePath: String?
): Long
```

2. **Add getAppFd() native method:**
```kotlin
external fun getAppFd(tunnelId: String): Int
```

3. **Update connect() implementation:**
```kotlin
override suspend fun connect(ovpnConfig: String, authFilePath: String?): Boolean {
    return withContext(Dispatchers.IO) {
        // Connect (creates socketpair internally)
        val handle = nativeConnect(tunnelId, ovpnConfig, authFilePath)
        
        if (handle == 0L) {
            Log.e(TAG, "❌ Native connect returned invalid handle")
            return@withContext false
        }
        
        // Get the app FD for packet I/O
        val appFd = getAppFd(tunnelId)
        if (appFd < 0) {
            Log.e(TAG, "❌ Failed to get app FD")
            return@withContext false
        }
        
        Log.i(TAG, "✅ Got app FD: $appFd for tunnel $tunnelId")
        
        // TODO: Start reading from appFd
        // This will be handled by VpnConnectionManager
        
        true
    }
}
```

**Status:** NEEDS IMPLEMENTATION  
**Estimated Time:** 1 hour

---

### Step 7: Update VpnConnectionManager.kt

**Changes Needed:**

1. **Remove createPipe() call:**
```kotlin
// OLD:
val (kotlinFd, openvpnFd) = createPipe(tunnelId)
client.connect(config, authFile, openvpnFd)

// NEW:
client.connect(config, authFile)  // No FD needed!
val appFd = (client as NativeOpenVpnClient).getAppFd(tunnelId)
```

2. **Use appFd for reading:**
```kotlin
// Start reading from app FD
startPipeReader(tunnelId, appFd)
```

3. **No changes to packet writing:**
- `sendPacketToTunnel()` already writes to FD
- Just use appFd instead of kotlinFd

**Status:** NEEDS IMPLEMENTATION  
**Estimated Time:** 1 hour

---

## 🎯 Implementation Plan

### Phase 1: Code Changes (5-7 hours)

1. **Step 4:** Update openvpn_wrapper.cpp (2-3 hours)
   - Remove TunBuilderBase methods
   - Add external TUN factory setup
   - Pass factory to config

2. **Step 5:** Update JNI (1 hour)
   - Add getAppFd()
   - Remove setTunFileDescriptor()
   - Remove createPipe()

3. **Step 6:** Update NativeOpenVpnClient.kt (1 hour)
   - Remove tunFd parameter
   - Add getAppFd()
   - Update connect() logic

4. **Step 7:** Update VpnConnectionManager.kt (1 hour)
   - Remove createPipe()
   - Get appFd from client
   - Use appFd for I/O

### Phase 2: Testing (2-3 hours)

1. **Compilation Test:**
   ```bash
   ./gradlew :app:assembleDebug
   ```
   Expected: Compiles without errors

2. **Single Tunnel Test:**
   ```bash
   ./gradlew :app:connectedDebugAndroidTest \
     -Pandroid.testInstrumentationRunnerArguments.class=com.multiregionvpn.NordVpnE2ETest#test_routesToUK
   ```
   Expected: DNS resolves, HTTP succeeds

3. **Multi-Tunnel Test:**
   ```bash
   ./gradlew :app:connectedDebugAndroidTest \
     -Pandroid.testInstrumentationRunnerArguments.class=com.multiregionvpn.NordVpnE2ETest#test_multiTunnel_BothUKandFRActive
   ```
   Expected: Both tunnels active

4. **WireGuard Still Works:**
   ```bash
   ./gradlew :app:connectedDebugAndroidTest \
     -Pandroid.testInstrumentationRunnerArguments.class=com.multiregionvpn.WireGuardDockerE2ETest
   ```
   Expected: 6/6 tests passing

---

## 📊 Expected Outcome

### Before:
```
❌ OpenVPN: DNS fails (UnknownHostException)
✅ WireGuard: Works perfectly
```

### After:
```
✅ OpenVPN: DNS works! HTTP succeeds!
✅ WireGuard: Still works perfectly!
✅ Both protocols: Multi-tunnel support!
✅ NordVPN: Fully functional with multi-tunnel!
```

---

## 🚀 Next Action

**Continue with Step 4:** Update openvpn_wrapper.cpp

Key tasks:
1. Remove TunBuilderBase methods from AndroidOpenVPNClient
2. Add tunFactory to OpenVpnSession
3. Set extern_tun_factory on config
4. Test compilation

---

**Current Status:** 3/7 steps complete (43%)  
**Estimated Remaining Time:** 7-10 hours  
**Blocker:** None - ready to continue  
**Date:** November 6, 2025
