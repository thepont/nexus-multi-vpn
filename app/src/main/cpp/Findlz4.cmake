# Custom Findlz4.cmake for Android
# This uses CMake targets directly instead of file-finding

# IMPORTANT: This module must be found BEFORE OpenVPN 3's Findlz4.cmake
# We check for the target first, and if found, set all required variables

# First, check if variables are already set (parent CMakeLists may have set them)
# But we MUST ensure LZ4_LIBRARY is set (even if cached) for FIND_PACKAGE_HANDLE_STANDARD_ARGS
if(DEFINED LZ4_INCLUDE_DIR)
    if(DEFINED LZ4_LIBRARY)
        # Both set - use them and mark as found
        set(LZ4_FOUND TRUE CACHE BOOL "lz4 found" FORCE)
        message(STATUS "Using cached lz4 configuration: ${LZ4_INCLUDE_DIR}, ${LZ4_LIBRARY}")
        return()
    else()
        # Include dir set but library not - set it if target exists
        if(TARGET lz4::lz4)
            set(LZ4_LIBRARY "lz4::lz4" CACHE INTERNAL "lz4 library (target, not file)" FORCE)
            set(LZ4_FOUND TRUE CACHE BOOL "lz4 found" FORCE)
            message(STATUS "Using cached lz4 include dir, set library to target: ${LZ4_INCLUDE_DIR}")
            return()
        endif()
    endif()
endif()

# Check if lz4::lz4 target already exists (from FetchContent)
if(TARGET lz4::lz4)
    message(STATUS "Found lz4 target: lz4::lz4")
    
    # Get include directory from target
    get_target_property(LZ4_INCLUDES lz4::lz4 INTERFACE_INCLUDE_DIRECTORIES)
    
    if(LZ4_INCLUDES)
        # Extract actual path (may have generator expressions)
        list(GET LZ4_INCLUDES 0 LZ4_INCLUDE_DIR_TEMP)
        if(EXISTS "${LZ4_INCLUDE_DIR_TEMP}/lz4.h")
            set(LZ4_INCLUDE_DIR ${LZ4_INCLUDE_DIR_TEMP} CACHE PATH "lz4 include directory" FORCE)
        endif()
    endif()
    
    # If not found from target, try finding lz4.h in source directory
    if(NOT LZ4_INCLUDE_DIR)
        # Try to find lz4.h relative to target location
        # FetchContent usually puts lz4 in _deps/lz4-src
        get_target_property(LZ4_LOCATION lz4::lz4 LOCATION)
        if(LZ4_LOCATION)
            get_filename_component(LZ4_POSSIBLE_DIR "${LZ4_LOCATION}" DIRECTORY)
            get_filename_component(LZ4_POSSIBLE_DIR "${LZ4_POSSIBLE_DIR}" DIRECTORY)
            if(EXISTS "${LZ4_POSSIBLE_DIR}/lib/lz4.h")
                set(LZ4_INCLUDE_DIR "${LZ4_POSSIBLE_DIR}/lib" CACHE PATH "lz4 include directory" FORCE)
            endif()
        endif()
        
        # Last resort: find_path
        if(NOT LZ4_INCLUDE_DIR)
            find_path(LZ4_INCLUDE_DIR 
                NAMES lz4.h
                PATH_SUFFIXES lib include
                NO_CMAKE_PATH
                NO_CMAKE_ENVIRONMENT_PATH
            )
        endif()
    endif()
    
    # CRITICAL: We MUST set both LZ4_INCLUDE_DIR and LZ4_LIBRARY for FIND_PACKAGE_HANDLE_STANDARD_ARGS
    # But we must NOT set LZ4_LIBRARY to a file path that doesn't exist, or OpenVPN 3's
    # Findlz4 will create an IMPORTED target pointing to that file, causing ninja to fail.
    #
    # OpenVPN 3's Findlz4 checks: if(LZ4_LIBRARY AND NOT TARGET lz4::lz4)
    # Since lz4::lz4 target EXISTS, this will be FALSE, so it won't create IMPORTED target.
    # But FIND_PACKAGE_HANDLE_STANDARD_ARGS still needs LZ4_LIBRARY to be non-empty.
    #
    # Solution: Use a generator expression that references the target's location.
    # Since we can't use generator expressions in cache variables, we'll use
    # the target name itself as a marker. OpenVPN 3 will see the target exists
    # and use it for linking (via target_link_libraries), ignoring LZ4_LIBRARY.
    if(LZ4_INCLUDE_DIR)
        # Set LZ4_LIBRARY to satisfy FIND_PACKAGE_HANDLE_STANDARD_ARGS
        # OpenVPN 3 will use the target, not this variable
        if(NOT DEFINED LZ4_LIBRARY)
            # Use a special marker - OpenVPN 3 won't use this since target exists
            # But we need something non-empty for FIND_PACKAGE_HANDLE_STANDARD_ARGS
            # We'll use the target name as a marker (won't be used as a file)
            set(LZ4_LIBRARY "lz4::lz4" CACHE INTERNAL "lz4 library (target, not file)" FORCE)
        endif()
        
        # CRITICAL: Set LZ4_FOUND and return IMMEDIATELY
        # This prevents OpenVPN 3's Findlz4 from running
        set(LZ4_FOUND TRUE CACHE BOOL "lz4 found" FORCE)
        message(STATUS "Found lz4 via target: lz4::lz4")
        message(STATUS "  Include: ${LZ4_INCLUDE_DIR}")
        message(STATUS "  Library marker: ${LZ4_LIBRARY} (target lz4::lz4 will be used for linking)")
        
        # Return immediately - don't let OpenVPN 3's Findlz4 run
        return()
    else()
        message(WARNING "lz4::lz4 target found but could not determine include directory")
    endif()
elseif(DEFINED LZ4_INCLUDE_DIR AND DEFINED LZ4_LIBRARY)
    # Variables set by parent CMakeLists but target check failed
    # Use the cached values
    if(LZ4_INCLUDE_DIR AND LZ4_LIBRARY)
        set(LZ4_FOUND TRUE CACHE BOOL "lz4 found" FORCE)
        message(STATUS "Found lz4 via cached variables")
        message(STATUS "  Include: ${LZ4_INCLUDE_DIR}")
        message(STATUS "  Library: ${LZ4_LIBRARY}")
        return()
    endif()
endif()

# Fallback to standard Findlz4 logic (only if we haven't set variables)
# But first, check if parent set them in cache
if(NOT DEFINED LZ4_FOUND AND (DEFINED LZ4_INCLUDE_DIR OR DEFINED LZ4_LIBRARY))
    # Parent set some variables, try to use them
    set(LZ4_FOUND TRUE CACHE BOOL "lz4 found" FORCE)
    return()
endif()

# Final fallback to OpenVPN 3's Findlz4.cmake
# WARNING: This should NOT run if target exists - we should have returned above
# But if we reach here, OpenVPN 3's Findlz4 will create an IMPORTED target
# pointing to a file that might not exist yet, causing ninja errors.
# 
# To prevent this, we should ensure lz4::lz4 target is always built before
# OpenVPN 3's find_package(lz4) runs.
message(WARNING "Custom Findlz4.cmake falling back to OpenVPN 3's Findlz4.cmake")
message(WARNING "This might cause issues if lz4::lz4 target exists but wasn't detected")
set(FIND_LZ4_PATH "${CMAKE_CURRENT_LIST_DIR}/../../libs/openvpn3/cmake/Findlz4.cmake")
if(EXISTS "${FIND_LZ4_PATH}")
    include("${FIND_LZ4_PATH}")
endif()

