# OpenVPN 3 C++ Integration Setup Guide

## Current Status

✅ **Completed:**
- JNI bridge structure created (`openvpn_jni.cpp`, `openvpn_wrapper.cpp`)
- Kotlin interface (`NativeOpenVpnClient.kt`)
- CMake configuration
- Build system updated

⚠️ **Next Steps:**
- Clone and build OpenVPN 3 library
- Integrate OpenVPN 3 API into `openvpn_wrapper.cpp`
- Test connection

## Architecture

```
┌─────────────────────────────────────┐
│   Kotlin (NativeOpenVpnClient)     │
│   - connect()                       │
│   - sendPacket()                    │
│   - receivePacket()                 │
└──────────────┬──────────────────────┘
               │ JNI
               ▼
┌─────────────────────────────────────┐
│   C++ JNI Bridge (openvpn_jni.cpp) │
│   - nativeConnect()                 │
│   - nativeSendPacket()             │
└──────────────┬──────────────────────┘
               │ C function calls
               ▼
┌─────────────────────────────────────┐
│   OpenVPN Wrapper (openvpn_wrapper)│
│   - openvpn_wrapper_connect()      │
│   - openvpn_wrapper_send_packet()  │
└──────────────┬──────────────────────┘
               │ OpenVPN 3 C++ API
               ▼
┌─────────────────────────────────────┐
│   OpenVPN 3 Library                 │
│   (client/ovpncli.hpp)              │
└─────────────────────────────────────┘
```

## Step 1: Install Android NDK

If NDK is not installed:

```bash
# Via Android Studio SDK Manager, or:
sdkmanager "ndk;26.1.10909125"

# Or check available versions:
sdkmanager --list | grep ndk
```

Update `app/build.gradle.kts` if needed:
```kotlin
ndkVersion = "26.1.10909125" // Your installed version
```

## Step 2: Clone OpenVPN 3

```bash
cd /home/pont/projects/multi-region-vpn
git clone https://github.com/OpenVPN/openvpn3.git libs/openvpn3
cd libs/openvpn3
git checkout <latest-stable-tag>  # e.g., v22.1
```

## Step 3: Build OpenVPN 3 for Android

OpenVPN 3 needs to be built as a static or shared library for Android.

### Option A: Build as Submodule (Recommended)

Add to `CMakeLists.txt`:
```cmake
# Add OpenVPN 3 as subdirectory
add_subdirectory(${CMAKE_SOURCE_DIR}/../../libs/openvpn3/openvpn3 openvpn3-build)

# Link against OpenVPN 3 client library
target_link_libraries(
    openvpn-jni
    ${log-lib}
    openvpn3::client  # Or the actual library target name
)
```

### Option B: Pre-built Static Library

1. Build OpenVPN 3 separately using Android NDK
2. Place `.a` files in `app/src/main/cpp/libs/<abi>/`
3. Link in CMakeLists.txt

## Step 4: Implement OpenVPN 3 Integration

Update `openvpn_wrapper.cpp` to use actual OpenVPN 3 API:

```cpp
#include <client/ovpncli.hpp>

struct OpenVpnSession {
    OpenVPN::ClientAPI::OpenVPNClient* client;
    OpenVPN::ClientAPI::Config config;
    bool connected;
};

int openvpn_wrapper_connect(OpenVpnSession* session,
                           const char* config_str,
                           const char* username,
                           const char* password) {
    // Parse config
    session->config.content = std::string(config_str);
    session->config.content = parseConfigForCredentials(
        session->config.content, username, password);
    
    // Create client
    session->client = new OpenVPN::ClientAPI::OpenVPNClient();
    
    // Connect
    OpenVPN::ClientAPI::Status status = session->client->connect(session->config);
    
    if (status.error) {
        LOGE("Connection failed: %s", status.message.c_str());
        return -1;
    }
    
    session->connected = true;
    return 0;
}
```

## Step 5: Handle Packets

OpenVPN 3 uses callbacks for packet I/O:

```cpp
// In openvpn_wrapper, implement callback class
class PacketReceiveCallback : public OpenVPN::ClientAPI::OpenVPNClient {
    // Override virtual methods for packet reception
    void transport_recv(OpenVPN::ClientAPI::TransportClient&) override;
    void transport_send(OpenVPN::ClientAPI::TransportClient&) override;
};
```

## Step 6: Update CMakeLists.txt

Once OpenVPN 3 is built, update to link:

```cmake
# Include OpenVPN 3
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/../../libs/openvpn3)

# Link libraries
target_link_libraries(
    openvpn-jni
    ${log-lib}
    openvpn3-client  # Or actual library name
    ssl               # OpenSSL (if used)
    crypto
)
```

## Step 7: Test

1. Build the app:
   ```bash
   ./gradlew assembleDebug
   ```

2. Check native library is included:
   ```bash
   unzip -l app/build/outputs/apk/debug/app-debug.apk | grep libopenvpn-jni
   ```

3. Run unit tests:
   ```bash
   ./gradlew testDebugUnitTest
   ```

## Troubleshooting

### NDK Not Found
- Ensure NDK is installed via SDK Manager
- Set `ndkVersion` in `build.gradle.kts`
- Or set `ANDROID_NDK_HOME` environment variable

### CMake Errors
- Check CMake version: `cmake --version` (need 3.22+)
- Verify `CMakeLists.txt` syntax

### OpenVPN 3 Build Issues
- Ensure C++17 support
- Check OpenSSL/mbedTLS dependencies
- Review OpenVPN 3 build documentation

### JNI Linking Errors
- Verify function names match exactly (Java package path matters)
- Check `System.loadLibrary("openvpn-jni")` matches CMake target name

## References

- OpenVPN 3 GitHub: https://github.com/OpenVPN/openvpn3
- OpenVPN 3 Documentation: https://github.com/OpenVPN/openvpn3/tree/master/doc
- Android NDK Guide: https://developer.android.com/ndk/guides
- JNI Guide: https://developer.android.com/training/articles/perf-jni

## Next Actions

1. ✅ Basic structure complete
2. ⏳ Clone OpenVPN 3 repository
3. ⏳ Build OpenVPN 3 for Android
4. ⏳ Integrate API into wrapper
5. ⏳ Test connection

