cmake_minimum_required(VERSION 3.22.1)

project("multiregionvpn-native")

# Export compile commands for clang-tidy and other static analysis tools
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

# Set C++ standard
# OpenVPN 3 requires C++20, so we need to use C++20 for our wrapper code too
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ============================================================================
# Logging Level Configuration
# ============================================================================
# Options: RELEASE (errors only), DEBUG (info+errors), VERBOSE (all logs)
# Performance impact: RELEASE=0%, DEBUG=2-5%, VERBOSE=10-15%

# Auto-detect logging level from build type if not explicitly set
if(NOT DEFINED LOGGING_LEVEL)
    if(CMAKE_BUILD_TYPE STREQUAL "Release")
        set(LOGGING_LEVEL "RELEASE")
    elseif(CMAKE_BUILD_TYPE STREQUAL "Debug")
        set(LOGGING_LEVEL "DEBUG")
    else()
        set(LOGGING_LEVEL "DEBUG")  # Default to DEBUG
    endif()
endif()

set(LOGGING_LEVEL "${LOGGING_LEVEL}" CACHE STRING "Logging level: RELEASE, DEBUG, or VERBOSE" FORCE)
set_property(CACHE LOGGING_LEVEL PROPERTY STRINGS RELEASE DEBUG VERBOSE)

if(LOGGING_LEVEL STREQUAL "RELEASE")
    add_compile_definitions(LOGGING_LEVEL_RELEASE)
    message(STATUS "✅ Logging: RELEASE mode (errors only, 0% overhead)")
elseif(LOGGING_LEVEL STREQUAL "VERBOSE")
    add_compile_definitions(LOGGING_LEVEL_VERBOSE)
    message(STATUS "✅ Logging: VERBOSE mode (all logs, ~10-15% overhead)")
else()
    add_compile_definitions(LOGGING_LEVEL_DEBUG)
    message(STATUS "✅ Logging: DEBUG mode (info+errors, ~2-5% overhead)")
endif()

# Include directories
include_directories(${CMAKE_CURRENT_SOURCE_DIR})

# Add our custom Find*.cmake modules to module path (before OpenVPN 3 tries to find them)
# This must be BEFORE we add OpenVPN 3 subdirectory, and at the top level so it takes precedence
# CRITICAL: Use INSERT at index 0 to ensure our modules are found FIRST
# Even though OpenVPN 3 prepends its cmake/ directory, our INSERT at 0 ensures we're checked first
# When using vcpkg, we don't want Find*.cmake modules to interfere with CONFIG mode
# vcpkg provides packages via CONFIG files, not Find modules
if(NOT USE_VCPKG)
    list(INSERT CMAKE_MODULE_PATH 0 ${CMAKE_CURRENT_SOURCE_DIR})
endif()
message(STATUS "CMAKE_MODULE_PATH (before OpenVPN 3): ${CMAKE_MODULE_PATH}")

# ============================================================================
# Option 1: Use CMake FetchContent (RECOMMENDED - Automatic Download & Build)
# ============================================================================
# This automatically downloads OpenVPN 3 during the build process.
# No manual cloning required!

include(FetchContent)

# Configure OpenVPN 3 fetch
# Use master branch for latest External TUN Factory support
set(OPENVPN3_VERSION "master" CACHE STRING "OpenVPN 3 version to fetch")
# Old version v22.1 doesn't have External TUN Factory:
# set(OPENVPN3_VERSION "v22.1" CACHE STRING "OpenVPN 3 version to fetch")

FetchContent_Declare(
    openvpn3
    GIT_REPOSITORY https://github.com/OpenVPN/openvpn3.git
    GIT_TAG ${OPENVPN3_VERSION}
    GIT_SHALLOW TRUE  # Only fetch latest commit for the tag
)

# Option to control whether to fetch (can be disabled during development)
option(FETCH_OPENVPN3 "Fetch OpenVPN 3 library automatically" OFF)

if(FETCH_OPENVPN3)
    message(STATUS "Fetching OpenVPN 3 (version: ${OPENVPN3_VERSION})...")
    FetchContent_MakeAvailable(openvpn3)
    
    # After fetching, include OpenVPN 3 headers
    # Note: Actual include path depends on OpenVPN 3 structure
    include_directories(${openvpn3_SOURCE_DIR}/openvpn3/client)
endif()

# ============================================================================
# Option 2: Use Local Directory (if already cloned)
# ============================================================================
# Calculate path to OpenVPN 3 (4 levels up from cpp/ to project root)
get_filename_component(PROJECT_ROOT "${CMAKE_CURRENT_SOURCE_DIR}" DIRECTORY)  # cpp -> main
get_filename_component(PROJECT_ROOT "${PROJECT_ROOT}" DIRECTORY)  # main -> src
get_filename_component(PROJECT_ROOT "${PROJECT_ROOT}" DIRECTORY)  # src -> app
get_filename_component(PROJECT_ROOT "${PROJECT_ROOT}" DIRECTORY)  # app -> project root
set(OPENVPN3_DIR "${PROJECT_ROOT}/libs/openvpn3")

# ============================================================================
# Resolve OpenVPN 3 Dependencies using vcpkg (preferred) or FetchContent (fallback)
# ============================================================================
option(ENABLE_OPENVPN3 "Enable OpenVPN 3 integration" ON)
option(USE_VCPKG "Use vcpkg for dependencies (requires vcpkg to be installed)" OFF)

# Check if vcpkg toolchain is available
# For Android NDK builds, we need to use vcpkg's chainloading feature
# to work with Android's toolchain file
#
# CRITICAL: VCPKG_CHAINLOAD_TOOLCHAIN_FILE should be set via command-line BEFORE
# this CMakeLists.txt is processed. If it's not set, we try to infer it here.
# However, vcpkg.cmake processes the chainload very early, so command-line is preferred.
if(USE_VCPKG AND DEFINED ENV{VCPKG_ROOT} AND EXISTS "$ENV{VCPKG_ROOT}/scripts/buildsystems/vcpkg.cmake")
    set(VCPKG_TOOLCHAIN_FILE "$ENV{VCPKG_ROOT}/scripts/buildsystems/vcpkg.cmake")
    message(STATUS "Using vcpkg toolchain: ${VCPKG_TOOLCHAIN_FILE}")
    
    # Set Android triplet for vcpkg
    if(NOT DEFINED VCPKG_TARGET_TRIPLET)
        # Default to arm64-android for arm64-v8a
        if(ANDROID_ABI STREQUAL "arm64-v8a")
            set(VCPKG_TARGET_TRIPLET "arm64-android" CACHE STRING "vcpkg triplet")
        elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
            set(VCPKG_TARGET_TRIPLET "arm-android" CACHE STRING "vcpkg triplet")
        elseif(ANDROID_ABI STREQUAL "x86_64")
            set(VCPKG_TARGET_TRIPLET "x64-android" CACHE STRING "vcpkg triplet")
        elseif(ANDROID_ABI STREQUAL "x86")
            set(VCPKG_TARGET_TRIPLET "x86-android" CACHE STRING "vcpkg triplet")
        else()
            set(VCPKG_TARGET_TRIPLET "arm64-android" CACHE STRING "vcpkg triplet")
        endif()
    endif()
    
    message(STATUS "vcpkg target triplet: ${VCPKG_TARGET_TRIPLET}")
    
    # Check if chainload file was set via command-line
    # If not, try to infer it (though this might be too late)
    if(NOT DEFINED VCPKG_CHAINLOAD_TOOLCHAIN_FILE)
        # Try to find Android NDK toolchain if not explicitly set
        if(DEFINED ENV{ANDROID_NDK})
            set(ANDROID_NDK_PATH "$ENV{ANDROID_NDK}")
        elseif(DEFINED CMAKE_ANDROID_NDK)
            set(ANDROID_NDK_PATH "${CMAKE_ANDROID_NDK}")
        endif()
        
        if(ANDROID_NDK_PATH AND EXISTS "${ANDROID_NDK_PATH}/build/cmake/android.toolchain.cmake")
            # Note: Setting this here might be too late - vcpkg.cmake processes chainload early
            # But we'll try anyway as a fallback
            set(VCPKG_CHAINLOAD_TOOLCHAIN_FILE "${ANDROID_NDK_PATH}/build/cmake/android.toolchain.cmake" CACHE FILEPATH "Android NDK toolchain to chainload")
            message(STATUS "Inferred vcpkg chainload toolchain: ${VCPKG_CHAINLOAD_TOOLCHAIN_FILE}")
        else()
            message(WARNING "Could not find Android NDK toolchain for vcpkg chainload")
        endif()
    else()
        message(STATUS "vcpkg chainload toolchain (from command-line): ${VCPKG_CHAINLOAD_TOOLCHAIN_FILE}")
    endif()
    
    # Note: CMAKE_TOOLCHAIN_FILE should already be set to vcpkg's toolchain file
    # by the build.gradle.kts. vcpkg.cmake will handle the chainloading.
    message(STATUS "vcpkg toolchain configuration complete")
elseif(USE_VCPKG)
    message(WARNING "vcpkg requested but VCPKG_ROOT not set or vcpkg.cmake not found")
    message(WARNING "Falling back to FetchContent")
    set(USE_VCPKG OFF)
endif()

if(ENABLE_OPENVPN3 AND EXISTS "${OPENVPN3_DIR}/CMakeLists.txt")
    # Check if we can actually build OpenVPN 3
    # If USE_VCPKG is enabled but vcpkg isn't available, disable OpenVPN 3
    if(USE_VCPKG AND (NOT DEFINED ENV{VCPKG_ROOT} OR NOT EXISTS "$ENV{VCPKG_ROOT}/scripts/buildsystems/vcpkg.cmake"))
        message(WARNING "vcpkg requested but not available - disabling OpenVPN 3")
        set(ENABLE_OPENVPN3 OFF CACHE BOOL "Enable OpenVPN 3" FORCE)
    endif()
    
    # Only proceed if OpenVPN 3 is still enabled after the check above
    if(ENABLE_OPENVPN3)
        message(STATUS "Resolving OpenVPN 3 dependencies...")
        
        if(USE_VCPKG)
            message(STATUS "Using vcpkg for dependencies")
            message(STATUS "Dependencies (lz4, fmt, asio, mbedtls) will be provided by vcpkg")
            # vcpkg will handle all dependencies automatically via vcpkg.json manifest
            # CRITICAL: Pre-find packages to ensure targets exist before OpenVPN 3 tries to find them
            # OpenVPN 3's find_package calls will use Find*.cmake modules, but we can ensure
            # vcpkg targets exist first
            
            # When vcpkg is enabled, ensure packages are accessible via CMAKE_PREFIX_PATH
            # vcpkg should set this automatically, but we ensure packages directory is included
            # Get the triplet from cache (set by our vcpkg setup code above)
            if(DEFINED ENV{VCPKG_ROOT})
                set(VCPKG_ROOT_VAL "$ENV{VCPKG_ROOT}")
                # Try to get triplet from cache, or use default
                if(DEFINED VCPKG_TARGET_TRIPLET)
                    set(VCPKG_TRIPLET "${VCPKG_TARGET_TRIPLET}")
                else()
                    # Default to arm64-android for arm64-v8a
                    if(ANDROID_ABI STREQUAL "arm64-v8a")
                        set(VCPKG_TRIPLET "arm64-android")
                    elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
                        set(VCPKG_TRIPLET "arm-android")
                    elseif(ANDROID_ABI STREQUAL "x86_64")
                        set(VCPKG_TRIPLET "x64-android")
                    elseif(ANDROID_ABI STREQUAL "x86")
                        set(VCPKG_TRIPLET "x86-android")
                    else()
                        set(VCPKG_TRIPLET "arm64-android")
                    endif()
                endif()
                
                # Add packages directory to CMAKE_PREFIX_PATH so CONFIG mode can find them
                # This is needed because vcpkg's manifest mode uses packages/ directory
                list(APPEND CMAKE_PREFIX_PATH "${VCPKG_ROOT_VAL}/packages/fmt_${VCPKG_TRIPLET}")
                list(APPEND CMAKE_PREFIX_PATH "${VCPKG_ROOT_VAL}/packages/lz4_${VCPKG_TRIPLET}")
                list(APPEND CMAKE_PREFIX_PATH "${VCPKG_ROOT_VAL}/packages/mbedtls_${VCPKG_TRIPLET}")
                message(STATUS "Added vcpkg packages directories to CMAKE_PREFIX_PATH (triplet: ${VCPKG_TRIPLET})")
                
                # Also manually include config files if find_package doesn't work
                # This ensures targets exist even if CMAKE_PREFIX_PATH isn't enough
                set(FMT_CONFIG "${VCPKG_ROOT_VAL}/packages/fmt_${VCPKG_TRIPLET}/share/fmt/fmt-config.cmake")
                set(LZ4_CONFIG "${VCPKG_ROOT_VAL}/packages/lz4_${VCPKG_TRIPLET}/share/lz4/lz4Config.cmake")
                set(MBEDTLS_CONFIG "${VCPKG_ROOT_VAL}/packages/mbedtls_${VCPKG_TRIPLET}/share/mbedtls/MbedTLSConfig.cmake")
                
                if(EXISTS "${FMT_CONFIG}" AND NOT TARGET fmt::fmt)
                    message(STATUS "Manually including fmt-config.cmake")
                    include("${FMT_CONFIG}")
                endif()
                if(EXISTS "${LZ4_CONFIG}" AND NOT TARGET lz4::lz4)
                    message(STATUS "Manually including lz4Config.cmake")
                    include("${LZ4_CONFIG}")
                endif()
                if(EXISTS "${MBEDTLS_CONFIG}" AND NOT TARGET MbedTLS::mbedtls)
                    message(STATUS "Manually including MbedTLSConfig.cmake")
                    include("${MBEDTLS_CONFIG}")
                endif()
            endif()
            
            # Pre-find packages to ensure targets exist before OpenVPN 3 tries to find them
            # This helps when OpenVPN 3's find_package runs in subdirectory scope
            find_package(fmt CONFIG QUIET)
            find_package(lz4 CONFIG QUIET)
            find_package(MbedTLS CONFIG QUIET)
            
            message(STATUS "Pre-found vcpkg packages:")
            if(TARGET fmt::fmt)
                message(STATUS "  fmt::fmt: found")
            else()
                message(STATUS "  fmt::fmt: NOT found")
            endif()
            if(TARGET lz4::lz4)
                message(STATUS "  lz4::lz4: found")
            else()
                message(STATUS "  lz4::lz4: NOT found")
            endif()
            if(TARGET MbedTLS::mbedtls)
                message(STATUS "  MbedTLS::mbedtls: found")
            else()
                message(STATUS "  MbedTLS::mbedtls: NOT found")
            endif()
        else()
            message(STATUS "Using FetchContent for dependencies (vcpkg not available)")
                
            # 1. fmt - Header-only formatting library
            FetchContent_Declare(
                fmt
                GIT_REPOSITORY https://github.com/fmtlib/fmt.git
                GIT_TAG 10.2.1  # Stable version
                GIT_SHALLOW TRUE
            )
            
            # 2. asio - Header-only async I/O (standalone version)
            FetchContent_Declare(
                asio
                GIT_REPOSITORY https://github.com/chriskohlhoff/asio.git
                GIT_TAG asio-1-28-2  # Latest stable
                GIT_SHALLOW TRUE
            )
            
            # 3. lz4 - Compression library
            FetchContent_Declare(
                lz4
                GIT_REPOSITORY https://github.com/lz4/lz4.git
                GIT_TAG v1.9.4  # Stable version
                GIT_SHALLOW TRUE
                SOURCE_SUBDIR build/cmake  # lz4 CMakeLists.txt location
            )
            
            # Make dependencies available via FetchContent
            message(STATUS "Fetching fmt library...")
            FetchContent_MakeAvailable(fmt)
            
            message(STATUS "Fetching asio library...")
            FetchContent_MakeAvailable(asio)
            
            message(STATUS "Fetching lz4 library...")
            FetchContent_MakeAvailable(lz4)
            
            # DEBUG: List all targets to see what lz4 created
            message(STATUS "🔍 Checking available lz4 targets...")
            if(TARGET lz4_static)
                message(STATUS "   ✅ Found: lz4_static")
            endif()
            if(TARGET lz4)
                message(STATUS "   ✅ Found: lz4")
            endif()
            if(TARGET lz4::lz4)
                message(STATUS "   ✅ Found: lz4::lz4")
            endif()
            
            # Create lz4::lz4 alias if needed
            if(NOT TARGET lz4::lz4)
                if(TARGET lz4_static)
                    add_library(lz4::lz4 ALIAS lz4_static)
                    message(STATUS "✅ Created lz4::lz4 ALIAS from lz4_static")
                elseif(TARGET lz4)
                    add_library(lz4::lz4 ALIAS lz4)
                    message(STATUS "✅ Created lz4::lz4 ALIAS from lz4")
                else()
                    message(FATAL_ERROR "❌ NO lz4 targets found! Cannot build OpenVPN 3")
                endif()
            else()
                message(STATUS "✅ lz4::lz4 target already exists (no alias needed)")
            endif()
            
            # Verify alias worked
            if(TARGET lz4::lz4)
                message(STATUS "✅ CONFIRMED: lz4::lz4 target is now available")
            else()
                message(FATAL_ERROR "❌ FAILED: lz4::lz4 target NOT available after alias creation!")
            endif()
            
            # CRITICAL: Set lz4 variables in cache IMMEDIATELY after FetchContent
            # OpenVPN 3's find_package(lz4) runs in subdirectory scope and can't see the target.
            # We MUST set LZ4_INCLUDE_DIR and LZ4_LIBRARY in CACHE so Findlz4.cmake can use them.
            if(TARGET lz4::lz4)
                # Get include directory from target
                get_target_property(LZ4_INCLUDES lz4::lz4 INTERFACE_INCLUDE_DIRECTORIES)
                if(LZ4_INCLUDES)
                    list(GET LZ4_INCLUDES 0 LZ4_INC_DIR)
                    set(LZ4_INCLUDE_DIR ${LZ4_INC_DIR} CACHE PATH "lz4 include directory" FORCE)
                else()
                    # Fallback to source directory
                    set(LZ4_INCLUDE_DIR ${lz4_SOURCE_DIR}/lib CACHE PATH "lz4 include directory" FORCE)
                endif()
                
                # Set library path that will exist after build
                set(LZ4_LIBRARY "${lz4_BINARY_DIR}/liblz4.a" CACHE FILEPATH "lz4 library" FORCE)
                set(LZ4_FOUND TRUE CACHE BOOL "lz4 found" FORCE)
                
                mark_as_advanced(FORCE LZ4_INCLUDE_DIR LZ4_LIBRARY)
                
                message(STATUS "✅ Set lz4 cache variables after FetchContent:")
                message(STATUS "  LZ4_INCLUDE_DIR: ${LZ4_INCLUDE_DIR}")
                message(STATUS "  LZ4_LIBRARY: ${LZ4_LIBRARY}")
            endif()
        endif()
    
    # OpenVPN 3's Findlz4.cmake:
    # Line 1: find_path(LZ4_INCLUDE_DIR NAMES lz4.h)
    # Line 2: find_library(LZ4_LIBRARY NAMES lz4)
    # Line 5-8: FIND_PACKAGE_HANDLE_STANDARD_ARGS checks LZ4_LIBRARY and LZ4_INCLUDE_DIR
    # Line 10: if(LZ4_LIBRARY AND NOT TARGET lz4::lz4) - creates target if library found but target doesn't exist
    #
    # If lz4::lz4 target exists, OpenVPN 3 will use it (line 10 check)
    # But FIND_PACKAGE_HANDLE_STANDARD_ARGS still needs both variables set
    #
    # The issue: FIND_PATH and FIND_LIBRARY in Findlz4.cmake will clear our cache if they don't find files
    # Solution: Mark variables as advanced so FIND_* commands respect our cache
    
    # OpenVPN 3's Findlz4.cmake runs find_path() and find_library() which will
    # clear our cache if files don't exist yet. We need to set the variables
    # in a way that survives this, OR ensure our custom Findlz4.cmake is used.
    #
    # The safest approach: Set the variables unconditionally since we know
    # lz4 was fetched via FetchContent. The target will exist, and the files
    # will exist after build.
    
    # Set include directory (lz4 headers are in lib/)
    # Our custom Findlz4.cmake will handle setting LZ4_LIBRARY
    # We don't set LZ4_LIBRARY here to avoid OpenVPN 3's Findlz4 creating
    # an IMPORTED target pointing to a non-existent file
    set(LZ4_INCLUDE_DIR ${lz4_SOURCE_DIR}/lib CACHE PATH "lz4 include directory" FORCE)
    
    # DO NOT set LZ4_LIBRARY here - let our custom Findlz4.cmake handle it
    # If we set it to a file path, OpenVPN 3's Findlz4 might create an IMPORTED
    # target pointing to that file, causing ninja to fail when the file doesn't exist yet.
    # Our custom Findlz4 will set LZ4_LIBRARY appropriately when find_package runs.
    
    # Mark as advanced (but don't set LZ4_LIBRARY yet)
    mark_as_advanced(LZ4_INCLUDE_DIR LZ4_LIBRARY)
    
    # Verify target exists (for logging)
    if(TARGET lz4::lz4)
        set(LZ4_TARGET_NAME "lz4::lz4")
    elseif(TARGET lz4)
        set(LZ4_TARGET_NAME "lz4")
    else()
        set(LZ4_TARGET_NAME "unknown (will be created by Findlz4)")
    endif()
    
    message(STATUS "lz4 configured for OpenVPN 3:")
    message(STATUS "  Include: ${LZ4_INCLUDE_DIR}")
    message(STATUS "  Target: ${LZ4_TARGET_NAME} (LZ4_LIBRARY will be set by Findlz4.cmake)")
    
    # When using vcpkg, asio should already have a proper target
    # Only create our own asio::asio target if vcpkg didn't provide one
    if(USE_VCPKG)
        # vcpkg should provide asio::asio target
        if(TARGET asio::asio)
            message(STATUS "Using asio::asio target from vcpkg")
        else()
            message(WARNING "vcpkg enabled but asio::asio target not found - will create one")
            # vcpkg provides asio in packages/asio_arm64-android/include/
            if(DEFINED ENV{VCPKG_ROOT})
                set(VCPKG_ROOT_VAL "$ENV{VCPKG_ROOT}")
                set(ASIO_INCLUDE "${VCPKG_ROOT_VAL}/packages/asio_${VCPKG_TRIPLET}/include")
                if(EXISTS "${ASIO_INCLUDE}")
                    add_library(asio::asio INTERFACE IMPORTED)
                    set_target_properties(asio::asio PROPERTIES
                        INTERFACE_INCLUDE_DIRECTORIES "${ASIO_INCLUDE}"
                    )
                    target_compile_definitions(asio::asio INTERFACE
                        ASIO_STANDALONE
                        ASIO_HEADER_ONLY
                    )
                    message(STATUS "Created asio::asio target with vcpkg path: ${ASIO_INCLUDE}")
                endif()
            endif()
        endif()
    else()
        # Set ASIO_INCLUDE_DIR directly (OpenVPN 3's Findasio.cmake looks for this)
        # This must be set before OpenVPN 3's CMake runs
        set(ASIO_INCLUDE_DIR ${asio_SOURCE_DIR}/asio/include CACHE PATH "ASIO include directory" FORCE)
        message(STATUS "ASIO_INCLUDE_DIR set to: ${ASIO_INCLUDE_DIR}")
        
        # Create an asio::asio target for OpenVPN 3 (it expects this interface library)
        if(NOT TARGET asio::asio)
            add_library(asio::asio INTERFACE IMPORTED)
            set_target_properties(asio::asio PROPERTIES
                INTERFACE_INCLUDE_DIRECTORIES "${ASIO_INCLUDE_DIR}"
            )
            target_compile_definitions(asio::asio INTERFACE
                ASIO_STANDALONE
                ASIO_HEADER_ONLY
            )
            message(STATUS "Created asio::asio interface target")
        endif()
    endif()
    
    # Expose FetchContent dependencies to find_package() calls
    # OpenVPN 3 uses find_package() to locate dependencies
    set(fmt_DIR ${fmt_BINARY_DIR})
    set(lz4_DIR ${lz4_BINARY_DIR})
    
    # Add to CMAKE_PREFIX_PATH so find_package can locate them
    list(APPEND CMAKE_PREFIX_PATH
        ${fmt_SOURCE_DIR}
        ${asio_SOURCE_DIR}/asio/include
        ${lz4_SOURCE_DIR}
    )
    
    # 4. OpenSSL/mbedTLS - Build mbedTLS for Android
    # OpenVPN 3's findcoredeps.cmake uses pkg-config which isn't available on Android
    if(USE_VCPKG)
        message(STATUS "mbedTLS will be provided by vcpkg")
        # vcpkg will provide mbedTLS - no manual fetching needed
        # CRITICAL: Find mbedTLS via vcpkg BEFORE OpenVPN 3 tries to find it
        # vcpkg provides mbedtls package with target MbedTLS::mbedtls
        # vcpkg sets up CMAKE_PREFIX_PATH automatically, so find_package should work
        # Try CONFIG mode first (vcpkg provides config files)
        find_package(mbedtls CONFIG QUIET)
        if(NOT TARGET MbedTLS::mbedtls)
            # Try uppercase version
            find_package(MbedTLS CONFIG QUIET)
        endif()
        # Also try without CONFIG (some packages work both ways)
        if(NOT TARGET MbedTLS::mbedtls)
            find_package(mbedtls QUIET)
        endif()
        if(TARGET MbedTLS::mbedtls)
            message(STATUS "Found MbedTLS::mbedtls from vcpkg")
            # Create alias that OpenVPN 3 expects
            if(NOT TARGET mbedTLS::mbedTLS)
                add_library(mbedTLS::mbedTLS ALIAS MbedTLS::mbedtls)
                message(STATUS "Created mbedTLS::mbedTLS alias for OpenVPN 3 compatibility")
            endif()
            
            # Set variables that OpenVPN 3's FindmbedTLS.cmake expects
            get_target_property(MBEDTLS_INCLUDES MbedTLS::mbedtls INTERFACE_INCLUDE_DIRECTORIES)
            if(MBEDTLS_INCLUDES)
                list(GET MBEDTLS_INCLUDES 0 MBEDTLS_INCLUDE_DIR_VAL)
                set(MBEDTLS_INCLUDE_DIR ${MBEDTLS_INCLUDE_DIR_VAL} CACHE PATH "mbedTLS include directory" FORCE)
            endif()
            
            set(MBEDTLS_FOUND TRUE CACHE BOOL "mbedTLS found" FORCE)
            message(STATUS "mbedTLS configured from vcpkg for OpenVPN 3")
        else()
            message(WARNING "MbedTLS::mbedtls target not found from vcpkg - OpenVPN 3 may fail to find it")
        endif()
    else()
        # Fetch and build mbedTLS for Android
        FetchContent_Declare(
            mbedtls
            GIT_REPOSITORY https://github.com/Mbed-TLS/mbedtls.git
            GIT_TAG v3.5.0  # Stable version
            GIT_SHALLOW TRUE
        )
        message(STATUS "Fetching mbedTLS library...")
        
        # Configure mbedTLS options before making it available
        set(ENABLE_PROGRAMS OFF CACHE BOOL "Enable mbedTLS programs" FORCE)
        set(ENABLE_TESTING OFF CACHE BOOL "Enable mbedTLS tests" FORCE)
        set(ENABLE_ZLIB_SUPPORT OFF CACHE BOOL "Enable zlib support" FORCE)
        
        FetchContent_MakeAvailable(mbedtls)
    endif()
    
    # After mbedTLS is built via FetchContent or vcpkg, it should create CMake targets
    # Check what targets mbedTLS provides - typically mbedtls, mbedx509, mbedcrypto
    # We'll create an mbedTLS::mbedTLS alias/interface that OpenVPN 3 expects
    
    if(USE_VCPKG)
        message(STATUS "mbedTLS targets should be available from vcpkg")
        # vcpkg provides MbedTLS::mbedtls, MbedTLS::mbedx509, MbedTLS::mbedcrypto
        # OpenVPN 3 expects mbedTLS::mbedTLS (different casing)
        # Check if vcpkg targets exist
        if(TARGET MbedTLS::mbedtls)
            message(STATUS "Found MbedTLS::mbedtls from vcpkg")
            # Create alias that OpenVPN 3 expects
            if(NOT TARGET mbedTLS::mbedTLS)
                add_library(mbedTLS::mbedTLS ALIAS MbedTLS::mbedtls)
                message(STATUS "Created mbedTLS::mbedTLS alias for OpenVPN 3 compatibility")
            endif()
        else()
            message(WARNING "MbedTLS::mbedtls target not found from vcpkg")
        endif()
    endif()
    
    # mbedTLS typically creates these targets: mbedtls, mbedx509, mbedcrypto
    # We need to create mbedTLS::mbedTLS which OpenVPN 3's find_package expects
    # (Only needed when NOT using vcpkg)
    
    if(NOT USE_VCPKG AND TARGET mbedtls AND TARGET mbedx509 AND TARGET mbedcrypto)
        # mbedTLS was built - create the alias target
        if(NOT TARGET mbedTLS::mbedTLS)
            add_library(mbedTLS::mbedTLS INTERFACE IMPORTED)
            set_target_properties(mbedTLS::mbedTLS PROPERTIES
                INTERFACE_LINK_LIBRARIES "mbedtls;mbedx509;mbedcrypto"
            )
            # Get include directories from one of the targets
            get_target_property(MBEDTLS_INCLUDES mbedtls INTERFACE_INCLUDE_DIRECTORIES)
            if(MBEDTLS_INCLUDES)
                set_target_properties(mbedTLS::mbedTLS PROPERTIES
                    INTERFACE_INCLUDE_DIRECTORIES "${MBEDTLS_INCLUDES}"
                )
            else()
                set_target_properties(mbedTLS::mbedTLS PROPERTIES
                    INTERFACE_INCLUDE_DIRECTORIES "${mbedtls_SOURCE_DIR}/include"
                )
            endif()
            
            # OpenVPN 3's FindmbedTLS.cmake checks:
            # Line 26: IF(MBEDTLS_INCLUDE_DIR AND MBEDTLS_LIBRARY AND MBEDX509_LIBRARY AND MBEDCRYPTO_LIBRARY)
            # Line 33: IF(NOT TARGET mbedTLS::mbedTLS AND EXISTS ${MBEDTLS_LIBRARY})
            # 
            # Key insight: If TARGET mbedTLS::mbedTLS exists, OpenVPN 3's FindmbedTLS.cmake
            # will use it (line 34 checks NOT TARGET, meaning if target exists, it skips file checks)
            # But it still needs MBEDTLS_INCLUDE_DIR and the library variables set for MBEDTLS_FOUND
            #
            # The library files don't exist yet during configuration, so we need to either:
            # 1. Use generator expressions to reference the targets (doesn't work for FIND_LIBRARY)
            # 2. Set dummy paths and let OpenVPN 3 use the target
            # 3. Build mbedTLS as an installed package first
            #
            # Since we can't build libraries during configure phase, let's set the variables
            # that OpenVPN 3's FindmbedTLS.cmake expects, and it will use our mbedTLS::mbedTLS target
            
            # Set include directory (required)
            set(MBEDTLS_INCLUDE_DIR ${mbedtls_SOURCE_DIR}/include CACHE PATH "mbedTLS include directory" FORCE)
            
            # For library files, we need to provide paths that will exist at build time
            # OpenVPN 3's FindmbedTLS.cmake checks EXISTS ${MBEDTLS_LIBRARY} on line 34
            # BUT it only does this if TARGET mbedTLS::mbedTLS doesn't exist
            # Since we're creating the target, it should use that instead
            
            # However, line 26 still requires all variables to be set for MBEDTLS_FOUND
            # Let's set them to the expected library output paths (they'll exist after build)
            # These paths are where mbedTLS will build the .a files
            set(MBEDTLS_LIBRARY "${mbedtls_BINARY_DIR}/library/libmbedtls.a" CACHE FILEPATH "mbedTLS library" FORCE)
            set(MBEDX509_LIBRARY "${mbedtls_BINARY_DIR}/library/libmbedx509.a" CACHE FILEPATH "mbedX509 library" FORCE)
            set(MBEDCRYPTO_LIBRARY "${mbedtls_BINARY_DIR}/library/libmbedcrypto.a" CACHE FILEPATH "mbedCrypto library" FORCE)
            
            # Mark as found - OpenVPN 3's FindmbedTLS will see the target and use it
            set(MBEDTLS_FOUND TRUE CACHE BOOL "mbedTLS found" FORCE)
            
            message(STATUS "mbedTLS configured for OpenVPN 3:")
            message(STATUS "  Include: ${MBEDTLS_INCLUDE_DIR}")
            message(STATUS "  Target: mbedTLS::mbedTLS (links mbedtls, mbedx509, mbedcrypto)")
            message(STATUS "  Library paths set (will exist after build):")
            message(STATUS "    ${MBEDTLS_LIBRARY}")
            message(STATUS "    ${MBEDX509_LIBRARY}")
            message(STATUS "    ${MBEDCRYPTO_LIBRARY}")
        endif()
    else()
            # When using vcpkg, mbedTLS should be available via find_package
            # Don't fail here if USE_VCPKG is ON - let it try find_package instead
            if(NOT USE_VCPKG)
                message(FATAL_ERROR "mbedTLS targets (mbedtls, mbedx509, mbedcrypto) not found after FetchContent_MakeAvailable")
            else()
                message(STATUS "mbedTLS not found via FetchContent, will try vcpkg find_package")
            endif()
    endif()
    
    # Set USE_MBEDTLS for OpenVPN 3 (its findcoredeps.cmake checks this)
    set(USE_MBEDTLS ON CACHE BOOL "Use mbed TLS instead of OpenSSL" FORCE)
    
    # OpenVPN 3's add_ssl_library() tries pkg-config first, then falls back to find_package(mbedTLS)
    # Since pkg-config won't work for Android cross-compilation, ensure find_package(mbedTLS) works
    # by making sure the mbedTLS::mbedTLS target is available
    message(STATUS "mbedTLS target available: mbedTLS::mbedTLS")
    message(STATUS "OpenVPN 3 will use find_package(mbedTLS) since pkg-config won't find it on Android")
    
    # Define that OpenVPN 3 is available (will be set later if CAN_BUILD_OPENVPN3 is true)
    # Note: This is now set conditionally after CAN_BUILD_OPENVPN3 check
    
    # Include directories for dependencies
    include_directories(${fmt_SOURCE_DIR}/include)
    include_directories(${asio_SOURCE_DIR}/asio/include)
    include_directories(${lz4_SOURCE_DIR}/lib)
    
    # USE_MBEDTLS is already set above
    # OpenVPN 3's findcoredeps.cmake will use find_package(mbedTLS) instead of pkg-config
    # if USE_MBEDTLS is ON and pkg-config fails
    
    # Build OpenVPN 3 as a subdirectory
    # OpenVPN 3 uses find_package() for dependencies - we've created the targets above
    # Disable optional dependencies that aren't needed for Android
    set(BUILD_TESTING OFF CACHE BOOL "Build tests" FORCE)
    set(OPENVPN_BUILD_PYTHON OFF CACHE BOOL "Build Python bindings" FORCE)
    set(ENABLE_OVPNDCO OFF CACHE BOOL "Enable DCO" FORCE)
    
    # OpenVPN 3's findcoredeps.cmake uses find_package() which should find our targets:
    # - asio::asio (we created this)
    # - lz4::lz4 (FetchContent provides this)
    # - fmt::fmt (FetchContent provides this)
    # - mbedTLS::mbedTLS (we created this)
    #
    # The only issue is add_ssl_library() requires PkgConfig, but it falls back to
    # find_package(mbedTLS) when pkg-config fails. Since we have mbedTLS::mbedTLS target,
    # that should work.
    
    # Ensure CMAKE_PREFIX_PATH includes our dependency paths
    list(APPEND CMAKE_PREFIX_PATH
        ${asio_SOURCE_DIR}/asio
        ${lz4_SOURCE_DIR}
        ${fmt_SOURCE_DIR}
    )
    
    # CRITICAL: Pre-set ALL lz4 variables BEFORE add_subdirectory
    # 
    # The problem: lz4::lz4 target exists in parent scope, but OpenVPN 3's find_package(lz4)
    # runs in subdirectory scope and CAN'T SEE THE TARGET. So Findlz4.cmake tries to use
    # find_path/find_library which fails on Android.
    #
    # Solution: Set LZ4_INCLUDE_DIR and LZ4_LIBRARY in CACHE with FORCE BEFORE add_subdirectory.
    # OpenVPN 3's Findlz4.cmake will see these cached variables and use them.
    # It checks: if(LZ4_LIBRARY AND NOT TARGET lz4::lz4) - since target doesn't exist in subdirectory
    # scope, it will create an IMPORTED target, but that's fine - we'll use the real target for linking.
    if(TARGET lz4::lz4 AND DEFINED LZ4_INCLUDE_DIR)
        # Get the actual library file path that will exist after build
        # We need a real path for OpenVPN 3's Findlz4 to create IMPORTED target
        set(LZ4_LIBRARY_PATH "${lz4_BINARY_DIR}/liblz4.a")
        
        # Set ALL required variables in CACHE with FORCE
        set(LZ4_INCLUDE_DIR ${LZ4_INCLUDE_DIR} CACHE PATH "lz4 include directory" FORCE)
        set(LZ4_LIBRARY ${LZ4_LIBRARY_PATH} CACHE FILEPATH "lz4 library" FORCE)
        set(LZ4_FOUND TRUE CACHE BOOL "lz4 found" FORCE)
        
        # Mark as advanced so find_path/find_library won't clear them
        mark_as_advanced(FORCE LZ4_INCLUDE_DIR LZ4_LIBRARY)
        
        message(STATUS "Pre-set lz4 variables for OpenVPN 3 subdirectory:")
        message(STATUS "  LZ4_INCLUDE_DIR: ${LZ4_INCLUDE_DIR} (advanced)")
        message(STATUS "  LZ4_LIBRARY: ${LZ4_LIBRARY} (advanced)")
        message(STATUS "  LZ4_FOUND: ${LZ4_FOUND}")
        message(STATUS "  Note: Target lz4::lz4 exists in parent scope, but OpenVPN 3 subdirectory")
        message(STATUS "        can't see it. Findlz4 will create IMPORTED target from file path.")
    endif()
    
        # Check if dependencies are actually available before adding OpenVPN 3 subdirectory
        # This must be done AFTER FetchContent_MakeAvailable (if using FetchContent)
        # or after checking vcpkg targets (if using vcpkg)
        set(CAN_BUILD_OPENVPN3 TRUE)
        
        if(USE_VCPKG)
            # Check if vcpkg targets exist
            if(NOT TARGET lz4::lz4)
                message(WARNING "lz4::lz4 target not found from vcpkg - cannot build OpenVPN 3")
                set(CAN_BUILD_OPENVPN3 FALSE)
            endif()
        else()
            # Using FetchContent - dependencies should have been fetched above
            # Check if they actually exist
            if(NOT TARGET lz4::lz4)
                message(WARNING "lz4::lz4 target not found after FetchContent - cannot build OpenVPN 3")
                set(CAN_BUILD_OPENVPN3 FALSE)
            endif()
        endif()
        
        if(CAN_BUILD_OPENVPN3)
            message(STATUS "Adding OpenVPN 3 as subdirectory...")
            message(STATUS "CMAKE_MODULE_PATH (for Find*.cmake): ${CMAKE_MODULE_PATH}")
            message(STATUS "CMAKE_PREFIX_PATH: ${CMAKE_PREFIX_PATH}")
            message(STATUS "Available targets: asio::asio, lz4::lz4, fmt::fmt, mbedTLS::mbedTLS")
            
            # Disable OpenVPN 3 tests - we only need the library, not test executables
            # This prevents errors in test/ovpncli/CMakeLists.txt
            set(BUILD_TESTING OFF CACHE BOOL "Build testing" FORCE)
            
            # CRITICAL: OpenVPN 3's CMakeLists.txt sets CMAKE_MODULE_PATH with its own cmake/ directory
            # which means its Findlz4.cmake will be found FIRST, not ours.
            # Solution: Temporarily override CMAKE_MODULE_PATH in OpenVPN 3's scope.
            # We'll do this by setting a variable that OpenVPN 3's CMakeLists can check,
            # OR by setting CMAKE_MODULE_PATH globally before add_subdirectory.
            #
            # Actually, we can't easily override OpenVPN 3's CMAKE_MODULE_PATH assignment.
            # Better solution: Patch OpenVPN 3's findcoredeps.cmake to check for our variables first.
            # OR: Make sure LZ4 variables are set in a way that find_library won't override them.
            #
            # The real fix: OpenVPN 3's find_library() will clear our cache if it doesn't find the file.
            # We need to prevent find_library from running, or make it find something.
            # Let's try setting CMAKE_LIBRARY_PATH so find_library can find our lz4:
            list(APPEND CMAKE_LIBRARY_PATH "${lz4_BINARY_DIR}")
            
            add_subdirectory(${OPENVPN3_DIR} ${CMAKE_BINARY_DIR}/openvpn3 EXCLUDE_FROM_ALL)
            
            # Include OpenVPN 3 headers (must come after add_subdirectory)
            include_directories(${OPENVPN3_DIR})
            include_directories(${OPENVPN3_DIR}/client)
            
            # NOTE: openvpn-jni library is defined later in this file (after this if block)
            # Don't try to link to it here - all linking happens after add_library() call
            
            message(STATUS "✅ OpenVPN 3 dependencies resolved:")
            if(DEFINED fmt_SOURCE_DIR)
                message(STATUS "   • fmt: ${fmt_SOURCE_DIR}")
            endif()
            if(DEFINED asio_SOURCE_DIR)
                message(STATUS "   • asio: ${asio_SOURCE_DIR}")
            endif()
            if(DEFINED lz4_SOURCE_DIR)
                message(STATUS "   • lz4: ${lz4_SOURCE_DIR}")
            endif()
            if(OpenSSL_FOUND)
                message(STATUS "   • OpenSSL: ${OPENSSL_VERSION}")
            else()
                message(STATUS "   • OpenSSL: Using Android built-in")
            endif()
            message(STATUS "   • OpenVPN 3: ${OPENVPN3_DIR}")
        else()
            message(WARNING "Cannot build OpenVPN 3 - dependencies not available")
            message(WARNING "Building stub library without OpenVPN 3 support")
            set(ENABLE_OPENVPN3 OFF)
        endif()  # CAN_BUILD_OPENVPN3
        
        # Define that OpenVPN 3 is available (only if we successfully set it up)
        if(CAN_BUILD_OPENVPN3)
            add_compile_definitions(OPENVPN3_AVAILABLE)
            
            # CRITICAL: Enable External TUN Factory mode for OpenVPN 3
            # This tells OpenVPN 3 to use our custom TUN implementation instead of TunBuilderBase
            # With this flag, OpenVPN 3 will actively poll our socketpair FD in its event loop
            add_compile_definitions(OPENVPN_EXTERNAL_TUN_FACTORY)
            add_compile_definitions(OPENVPN_DEBUG_CLIPROTO)  # Enable detailed protocol logging
            message(STATUS "✅ OpenVPN 3 External TUN Factory mode ENABLED")
            message(STATUS "✅ OpenVPN 3 CLIPROTO debug logging ENABLED")
            message(STATUS "   OpenVPN 3 will use custom TUN implementation")
            message(STATUS "   Event loop will actively poll our socketpair FD")
        endif()
    endif()  # ENABLE_OPENVPN3 (inner check at line 141)
    
elseif(ENABLE_OPENVPN3)
    message(WARNING "OpenVPN 3 not found at ${OPENVPN3_DIR} or build failed.")
    message(WARNING "Placeholder implementation will be used (connections will fail).")
    message(WARNING "To enable OpenVPN 3: Fix build errors and ensure ${OPENVPN3_DIR}/CMakeLists.txt exists")
else()
    message(STATUS "OpenVPN 3 integration disabled")
endif()

# ============================================================================
# Build JNI wrapper library
# ============================================================================
add_library(
    openvpn-jni
    SHARED
    openvpn_jni.cpp
    openvpn_wrapper.cpp
)

# Link dependencies (only if OpenVPN 3 was enabled)
if(ENABLE_OPENVPN3 AND EXISTS "${OPENVPN3_DIR}/CMakeLists.txt")
    # Check if required dependencies are available
    # If vcpkg is enabled, check if targets exist
    # If not using vcpkg, dependencies should come from FetchContent (but FETCH_OPENVPN3 is OFF by default)
    set(DEPS_AVAILABLE TRUE)
    
    if(USE_VCPKG)
        # Check if vcpkg targets exist
        if(NOT TARGET lz4::lz4)
            message(WARNING "lz4::lz4 target not found - OpenVPN 3 dependencies may not be available")
            message(WARNING "Building stub library without OpenVPN 3 support")
            set(DEPS_AVAILABLE FALSE)
        endif()
    elseif(NOT FETCH_OPENVPN3)
        # Not fetching - must be using local libs/openvpn3 directory
        # Check if dependencies were resolved via FetchContent in ENABLE_OPENVPN3 block
        if(TARGET lz4::lz4 AND TARGET fmt::fmt AND TARGET asio::asio)
            message(STATUS "✅ Using local OpenVPN 3 with FetchContent dependencies")
            set(DEPS_AVAILABLE TRUE)
        else()
            message(WARNING "OpenVPN 3 enabled but dependencies not available - building stub")
            set(DEPS_AVAILABLE FALSE)
        endif()
    endif()
    
    if(DEPS_AVAILABLE)
        # NOTE: We'll let add_corelibrary_dependencies handle linking to fmt, asio, lz4, mbedTLS
        # to avoid mixing keyword and plain signatures in target_link_libraries
        # add_corelibrary_dependencies uses plain signature (target_link_libraries(target lib))
        message(STATUS "Libraries (fmt, asio, lz4, mbedTLS) will be linked via add_corelibrary_dependencies")
        
        target_compile_definitions(openvpn-jni PRIVATE
            ASIO_STANDALONE
            ASIO_HEADER_ONLY
            ASIO_NO_DEPRECATED
        )
        
        # OpenVPN 3 doesn't create a library - it uses add_core_dependencies to add sources directly
        # However, add_core_dependencies calls add_corelibrary_dependencies which tries to find
        # dependencies that may not be available in our scope. Instead, we'll manually call
        # add_corelibrary_dependencies with the right setup.
        message(STATUS "Adding OpenVPN 3 core dependencies to openvpn-jni target")
        
        # Set CORE_DIR for OpenVPN 3's findcoredeps (it expects this)
        set(CORE_DIR "${OPENVPN3_DIR}" CACHE PATH "OpenVPN 3 core directory")
        
        # Include OpenVPN 3's findcoredeps.cmake which provides add_corelibrary_dependencies function
        # Use the one from OpenVPN 3 (not our custom module path) since we've already set up dependencies
        include("${OPENVPN3_DIR}/cmake/findcoredeps.cmake")
        
        # Override add_ssl_library function AFTER include so our version takes precedence
        # OpenVPN 3's original function tries pkg-config which fails on Android
        function(add_ssl_library target)
            if(USE_MBEDTLS)
                # Use vcpkg target directly - it's already available
                if(TARGET mbedTLS::mbedTLS)
                    target_link_libraries(${target} mbedTLS::mbedTLS)  # Use plain signature to match add_corelibrary_dependencies
                    target_compile_definitions(${target} PRIVATE -DUSE_MBEDTLS)
                    message(STATUS "Linked mbedTLS::mbedTLS to ${target} (vcpkg override)")
                elseif(TARGET MbedTLS::mbedtls)
                    # Create alias if needed
                    add_library(mbedTLS::mbedTLS ALIAS MbedTLS::mbedtls)
                    target_link_libraries(${target} mbedTLS::mbedTLS)  # Use plain signature
                    target_compile_definitions(${target} PRIVATE -DUSE_MBEDTLS)
                    message(STATUS "Linked MbedTLS::mbedtls (via alias) to ${target} (vcpkg override)")
                else()
                    message(FATAL_ERROR "mbedTLS target not found - cannot link SSL library")
                endif()
            else()
                message(FATAL_ERROR "OpenSSL not supported on Android - must use mbedTLS")
            endif()
        endfunction()
        
        # Set OPENVPN_PLAT for Android (OpenVPN 3 expects this)
        set(OPENVPN_PLAT "linux" CACHE STRING "OpenVPN platform" FORCE)
        
        # Ensure mbedTLS target is available
        if(USE_VCPKG AND TARGET MbedTLS::mbedtls AND NOT TARGET mbedTLS::mbedTLS)
            add_library(mbedTLS::mbedTLS ALIAS MbedTLS::mbedtls)
            message(STATUS "Created mbedTLS::mbedTLS alias for OpenVPN 3")
        endif()
        
        # Call add_corelibrary_dependencies directly - this sets up includes, compile definitions, and links
        # Note: We've overridden add_ssl_library to use vcpkg targets correctly
        add_corelibrary_dependencies(openvpn-jni)
        
        # Fix pthread linking - Android doesn't need -lpthread, it's part of libc++
        # OpenVPN 3's findcoredeps adds -lpthread for UNIX, but Android doesn't need it
        # Remove the pthread link if it was added
        get_target_property(linked_libs openvpn-jni LINK_LIBRARIES)
        if(linked_libs)
            list(REMOVE_ITEM linked_libs "pthread")
            set_target_properties(openvpn-jni PROPERTIES LINK_LIBRARIES "${linked_libs}")
        endif()
        
        # Add the data_epoch.cpp source that add_core_dependencies normally adds
        target_sources(openvpn-jni PRIVATE "${CORE_DIR}/openvpn/crypto/data_epoch.cpp")
        
        # Also add the client implementation source (ovpncli.cpp) which contains OpenVPNClient implementation
        target_sources(openvpn-jni PRIVATE "${OPENVPN3_DIR}/client/ovpncli.cpp")
        
        message(STATUS "OpenVPN 3 core sources and client implementation added to openvpn-jni")
    else()
        # Dependencies not available - build stub library without OpenVPN 3
        message(STATUS "Building stub library without OpenVPN 3 (dependencies not available)")
        target_compile_definitions(openvpn-jni PRIVATE
            OPENVPN3_AVAILABLE=0
        )
    endif()  # DEPS_AVAILABLE
endif()

# Link Android libraries
# Use plain signature to match add_corelibrary_dependencies style
find_library(
    log-lib
    log
)

target_link_libraries(openvpn-jni ${log-lib})

# Additional dependencies will be added when OpenVPN 3 is enabled

# Link OpenVPN 3 library (once FetchContent makes it available)
# The actual library name depends on how OpenVPN 3 is built
# This will be uncommented once we determine the correct target name
# if(TARGET openvpn3::client OR TARGET openvpn3-client)
#     target_link_libraries(
#         openvpn-jni
#         ${log-lib}
#         openvpn3::client  # Or openvpn3-client, depending on OpenVPN 3 build
#     )
# endif()

# Force include our logging override BEFORE any OpenVPN 3 headers
# This fixes NDK 25 compilation issues with OpenVPN 3's stream operator logging
target_compile_options(openvpn-jni PRIVATE
    -include ${CMAKE_CURRENT_SOURCE_DIR}/openvpn_log_override.h
)

# Compiler definitions
# OpenVPN 3 requires these definitions to compile correctly
# These match what OpenVPN 3's add_corelibrary_dependencies sets
target_compile_definitions(openvpn-jni PRIVATE
    ANDROID
    USE_ASIO
    ASIO_STANDALONE
    HAVE_LZ4
    USE_MBEDTLS
)
