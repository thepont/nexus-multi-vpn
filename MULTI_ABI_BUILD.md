# Building for Multiple Architectures (arm64 + arm32)

## Current Status
- ✅ **arm64-v8a** (64-bit ARM): Fully working with OpenVPN 3 + vcpkg dependencies
- ❌ **armeabi-v7a** (32-bit ARM): Not supported - would require rebuilding all vcpkg dependencies

## Why Only arm64-v8a?

The app uses OpenVPN 3 which depends on:
- **mbedtls** (cryptography)
- **lz4** (compression)  
- **asio** (async I/O)

These are built via vcpkg for the `arm64-android` triplet. Building for `armeabi-v7a` requires:
1. Setting up vcpkg Android NDK toolchain for arm32
2. Rebuilding mbedtls, lz4, asio for `arm-android` triplet (~20-30 min build time)
3. Maintaining both sets of libraries

## Options to Support 32-bit ARM Devices

### Option 1: Build vcpkg for arm32 (Most Complete)
```bash
# Install vcpkg dependencies for 32-bit ARM
export ANDROID_NDK_HOME=~/Android/Sdk/ndk/25.1.8937393
cd ~/vcpkg
./vcpkg install mbedtls lz4 asio --triplet=arm-android

# Update build.gradle.kts
ndk {
    abiFilters += listOf("arm64-v8a", "armeabi-v7a")
}
```

**Pros**: Full OpenVPN 3 support on 32-bit devices  
**Cons**: Long build time, larger APK size

### Option 2: WireGuard-only for arm32 (Recommended)
Use WireGuard (which has simpler dependencies) for 32-bit ARM and OpenVPN for 64-bit:

```kotlin
// In CMakeLists.txt
if(ANDROID_ABI STREQUAL "armeabi-v7a")
    # Build WireGuard-only (Go backend, no C++ deps)
    add_library(openvpn-jni SHARED stub_openvpn.cpp)
else()
    # Build full OpenVPN 3
    # ... existing OpenVPN 3 setup
endif()
```

**Pros**: Fast build, smaller APK for 32-bit  
**Cons**: 32-bit devices can only use WireGuard

### Option 3: Separate APKs (Play Store Approach)
Build two APKs and let Google Play choose:

```kotlin
// build.gradle.kts
android {
    splits {
        abi {
            enable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            universalApk = false
        }
    }
}
```

Then only build vcpkg for arm64, ship a lighter arm32 APK.

**Pros**: Optimal size per device  
**Cons**: Need to maintain two build variants

### Option 4: Drop 32-bit Support (Current Approach)
Most modern Android devices (2018+) are 64-bit. Google Play requires 64-bit support as of August 2019.

**Market Coverage**:
- ✅ 95%+ of active Android devices (2023 data)
- ❌ Older devices (pre-2018)
- ❌ Some budget Android TV boxes

## Recommendation

**For production**: Use **Option 2** (WireGuard for arm32, OpenVPN for arm64)
- Covers all devices
- Reasonable build time
- Users on older devices still get VPN functionality (via WireGuard)

**For development/testing**: Stick with **arm64-v8a only** (current approach)
- Faster builds
- Most test devices are 64-bit
- Can always add arm32 later if needed

## Your Google TV (32-bit ARM)

Your specific Google TV (`armeabi-v7a only`) would need one of the above solutions. The fastest path:
1. Test on a 64-bit emulator or device (current approach)
2. OR implement Option 2 (WireGuard fallback for 32-bit)
3. OR wait ~30min for vcpkg arm32 build (Option 1)

