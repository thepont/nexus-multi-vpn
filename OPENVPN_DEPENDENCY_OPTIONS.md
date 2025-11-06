# OpenVPN 3 Dependency Integration Options

## Comparison of Approaches

### Option 1: CMake FetchContent ⭐ (RECOMMENDED)

**How it works:**
- CMake automatically downloads OpenVPN 3 during build
- No manual cloning needed
- Version controlled in `CMakeLists.txt`

**Pros:**
- ✅ Automatic - no manual steps
- ✅ Version controlled - specify exact version/tag
- ✅ Clean - no repository clutter
- ✅ CI/CD friendly - works in automated builds
- ✅ Modern CMake approach

**Cons:**
- ⚠️ Requires internet during first build
- ⚠️ Longer first build time (downloads + compiles)

**Usage:**
Already configured in `CMakeLists.txt`. Just build:
```bash
./gradlew assembleDebug
```

---

### Option 2: Git Submodule

**How it works:**
- Add OpenVPN 3 as a Git submodule
- Cloned into `libs/openvpn3` directory
- Version controlled via submodule reference

**Pros:**
- ✅ Version controlled
- ✅ Can modify locally
- ✅ Offline builds possible

**Cons:**
- ❌ Manual submodule management
- ❌ Extra repository size
- ❌ Need to update submodule separately

**Usage:**
```bash
git submodule add https://github.com/OpenVPN/openvpn3.git libs/openvpn3
git submodule update --init --recursive
```

Then in `CMakeLists.txt`:
```cmake
add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../../libs/openvpn3)
```

---

### Option 3: Manual Clone

**How it works:**
- Manually clone repository
- Add to `.gitignore`
- Reference in CMake

**Pros:**
- ✅ Full control
- ✅ Can modify freely

**Cons:**
- ❌ Manual steps required
- ❌ Not version controlled
- ❌ Each developer must clone separately
- ❌ CI/CD must handle cloning

**Usage:**
```bash
git clone https://github.com/OpenVPN/openvpn3.git libs/openvpn3
cd libs/openvpn3
git checkout v22.1
```

---

### Option 4: Pre-built Libraries (Not Available)

**Status:** ❌ Not available for Android NDK

OpenVPN 3 doesn't provide pre-built Android NDK libraries. You must build from source.

---

## Recommendation: Use FetchContent

The `CMakeLists.txt` is already configured with FetchContent. This is the best approach because:

1. **Zero manual steps** - Just build and it works
2. **Version control** - Specify version in CMakeLists.txt
3. **Team-friendly** - Everyone gets the same version automatically
4. **CI/CD ready** - Works in automated build systems

### How to Use

The build will automatically:
1. Download OpenVPN 3 during first build
2. Cache it for subsequent builds
3. Link it to your JNI library

To change the version, edit `CMakeLists.txt`:
```cmake
set(OPENVPN3_VERSION "v22.1" CACHE STRING "OpenVPN 3 version to fetch")
```

Or use a specific commit:
```cmake
set(OPENVPN3_VERSION "abc123def456..." CACHE STRING "OpenVPN 3 commit")
```

---

## Current Configuration

✅ **FetchContent is already configured** in `app/src/main/cpp/CMakeLists.txt`

The build system will:
- Automatically download OpenVPN 3 v22.1 on first build
- Build it as part of your native library
- Link it to `openvpn-jni`

**No action required!** Just run:
```bash
./gradlew assembleDebug
```

On first build, it will download OpenVPN 3 (may take a few minutes), then compile everything.

---

## Troubleshooting

### FetchContent fails
If automatic download fails, you can:
1. Manually clone to `libs/openvpn3`
2. Set `FETCH_OPENVPN3=OFF` in CMake
3. The build will use the local directory instead

### Slow first build
This is normal. FetchContent downloads and compiles OpenVPN 3 on first build.
Subsequent builds are fast (uses cache).

### Want to disable auto-fetch
In `CMakeLists.txt`, set:
```cmake
option(FETCH_OPENVPN3 "Fetch OpenVPN 3 library automatically" OFF)
```
Then manually clone to `libs/openvpn3`.


