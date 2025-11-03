# Using vcpkg for OpenVPN 3 Dependencies

Instead of using FetchContent (which requires custom Find*.cmake modules), you can use vcpkg which OpenVPN 3 is designed to work with.

## Setup vcpkg

### 1. Install vcpkg

```bash
# Clone vcpkg
cd ~
git clone https://github.com/Microsoft/vcpkg.git
cd vcpkg

# Bootstrap vcpkg
./bootstrap-vcpkg.sh

# Set environment variable
export VCPKG_ROOT=$HOME/vcpkg
echo 'export VCPKG_ROOT=$HOME/vcpkg' >> ~/.bashrc
```

### 2. Install Android triplet support (if needed)

vcpkg supports Android via triplets. The Android triplet is typically:
- `arm64-android` for arm64-v8a
- `arm-android` for armeabi-v7a
- `x64-android` for x86_64
- `x86-android` for x86

However, for Android NDK integration, you may need to create a custom triplet or use vcpkg's manifest mode.

### 3. Configure Android NDK for vcpkg

vcpkg needs to know where your Android NDK is. You can either:

**Option A: Set ANDROID_NDK environment variable**
```bash
export ANDROID_NDK=/path/to/your/android/ndk
# e.g., export ANDROID_NDK=$HOME/Android/Sdk/ndk/25.1.8937393
```

**Option B: Install dependencies manually for each ABI**
```bash
# For arm64-v8a
$VCPKG_ROOT/vcpkg install --triplet=arm64-android lz4 fmt asio mbedtls

# For armeabi-v7a  
$VCPKG_ROOT/vcpkg install --triplet=arm-android lz4 fmt asio mbedtls

# For x86_64
$VCPKG_ROOT/vcpkg install --triplet=x64-android lz4 fmt asio mbedtls

# For x86
$VCPKG_ROOT/vcpkg install --triplet=x86-android lz4 fmt asio mbedtls
```

### 4. Build with vcpkg

```bash
cd /home/pont/projects/multi-region-vpn

# Set VCPKG_ROOT and ANDROID_NDK
export VCPKG_ROOT=$HOME/vcpkg
export ANDROID_NDK=$HOME/Android/Sdk/ndk/25.1.8937393

# Build - CMake will automatically use vcpkg.json manifest
./gradlew :app:externalNativeBuildDebug
```

**Note:** For Android, vcpkg requires dependencies to be installed for each ABI triplet you want to build. The build system will automatically detect vcpkg and use it if `VCPKG_ROOT` is set.

## Benefits of vcpkg

1. **Native OpenVPN 3 support**: OpenVPN 3 is designed to work with vcpkg
2. **No custom Find*.cmake hacks**: vcpkg provides proper CMake targets
3. **Consistent dependencies**: Same dependency versions as OpenVPN 3 expects
4. **Better cross-platform**: Works on Windows, macOS, Linux, and Android

## Fallback to FetchContent

If vcpkg is not available or not configured, the build will automatically fall back to FetchContent (the current approach).

To explicitly disable vcpkg:
```bash
./gradlew :app:externalNativeBuildDebug -DUSE_VCPKG=OFF
```

