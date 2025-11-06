# vcpkg Setup for OpenVPN 3 Native Build

## Problem
The native library (`libopenvpn-jni.so`) was building as a **410K stub** without OpenVPN 3 support, causing `openvpn_wrapper_connect()` to return null handle (0).

## Root Cause
The `vcpkg/installed/` directory was missing, so CMake couldn't find the required dependencies (lz4, mbedtls, fmt, asio) and fell back to building a stub library.

## Solution

### 1. Set Environment Variables
Add these to your shell profile or `.env` file (not committed to git):

```bash
export VCPKG_ROOT=/home/pont/vcpkg
export ANDROID_NDK_HOME=/home/pont/Android/Sdk/ndk/25.1.8937393
```

### 2. Install Dependencies
```bash
cd $VCPKG_ROOT
./vcpkg install lz4:arm64-android mbedtls:arm64-android fmt:arm64-android asio:arm64-android
```

### 3. Rebuild
```bash
cd /path/to/multi-region-vpn
source .env  # If using .env file
./gradlew clean
./gradlew :app:assembleDebug
```

## Verification

Check the native library size:
```bash
find app/build -name "libopenvpn-jni.so" -exec ls -lh {} \;
```

**Expected:**
- `18M` - Full OpenVPN 3 library ✅
- `410K` - Stub library (missing dependencies) ❌

## Test Results

With correct build:
- ✅ `test_routesToDirectInternet` - PASSED
- ✅ `test_routesToUK` - PASSED (GB country code)
- ✅ Multi-tunnel VPN routing functional

## Architecture

The working setup includes:
1. **SOCK_SEQPACKET** - Packet-oriented socketpair for TUN emulation
2. **Package Registration** - ConnectionTracker knows which apps to route
3. **Packet Routing** - Correct inbound/outbound flow
4. **Global VPN Mode** - All apps have internet + per-app tunnel routing
