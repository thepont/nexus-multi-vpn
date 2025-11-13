# Custom FindmbedTLS.cmake for Android
# This bypasses the file-finding logic and uses CMake targets directly
# This module takes precedence over OpenVPN 3's FindmbedTLS.cmake

# Check if mbedTLS::mbedTLS target already exists (created by our CMakeLists.txt)
if(TARGET mbedTLS::mbedTLS)
    # Get include directory from the target property
    get_target_property(MBEDTLS_INCLUDES mbedTLS::mbedTLS INTERFACE_INCLUDE_DIRECTORIES)
    
    # Extract actual path (may have generator expressions)
    if(MBEDTLS_INCLUDES)
        # If it's a list, get first item
        if(MBEDTLS_INCLUDES MATCHES "\\$<")
            # Has generator expressions - we need to look elsewhere
            # Try finding via find_path using known mbedTLS source location
            # This will be set by our CMakeLists.txt before this module runs
        else()
            # Direct path
            list(GET MBEDTLS_INCLUDES 0 MBEDTLS_INCLUDE_DIR_TEMP)
            if(EXISTS "${MBEDTLS_INCLUDE_DIR_TEMP}/mbedtls/version.h")
                set(MBEDTLS_INCLUDE_DIR ${MBEDTLS_INCLUDE_DIR_TEMP} CACHE PATH "mbedTLS include directory" FORCE)
            endif()
        endif()
    endif()
    
    # If still not found, use FIND_PATH like OpenVPN 3's FindmbedTLS.cmake does
    # We'll search in the mbedTLS source directory (set by FetchContent)
    if(NOT MBEDTLS_INCLUDE_DIR)
        find_path(MBEDTLS_INCLUDE_DIR 
            NAMES mbedtls/version.h
            PATH_SUFFIXES include
        )
    endif()
    
    # Set library variables to use the target for linking
    # These will be used by OpenVPN 3's FindmbedTLS.cmake compatibility code
    set(MBEDTLS_LIBRARY "mbedTLS::mbedTLS" CACHE INTERNAL "mbedTLS library (target)" FORCE)
    set(MBEDX509_LIBRARY "mbedTLS::mbedTLS" CACHE INTERNAL "mbedX509 library (target)" FORCE)
    set(MBEDCRYPTO_LIBRARY "mbedTLS::mbedTLS" CACHE INTERNAL "mbedCrypto library (target)" FORCE)
    set(MBEDTLS_LIBRARIES "mbedTLS::mbedTLS" CACHE INTERNAL "mbedTLS libraries" FORCE)
    
    # OpenVPN 3's FindmbedTLS.cmake expects MBEDTLS_INCLUDE_DIR and library files
    # But we can bypass that by setting MBEDTLS_FOUND and providing the target
    # The key is: if TARGET mbedTLS::mbedTLS exists, OpenVPN 3 will use it
    
    if(MBEDTLS_INCLUDE_DIR)
        # Set MBEDTLS_FOUND so FindmbedTLS.cmake doesn't fail
        # We'll provide dummy library paths since we're using targets
        set(MBEDTLS_LIBRARY "mbedTLS::mbedTLS" CACHE INTERNAL "mbedTLS library" FORCE)
        set(MBEDX509_LIBRARY "mbedTLS::mbedTLS" CACHE INTERNAL "mbedX509 library" FORCE)
        set(MBEDCRYPTO_LIBRARY "mbedTLS::mbedTLS" CACHE INTERNAL "mbedCrypto library" FORCE)
        
        message(STATUS "Found mbedTLS via target: mbedTLS::mbedTLS")
        message(STATUS "  Include: ${MBEDTLS_INCLUDE_DIR}")
        message(STATUS "  Using target-based linking (libraries built during build phase)")
        return()
    else()
        message(WARNING "mbedTLS::mbedTLS target found but could not determine include directory")
        # Still try to set MBEDTLS_FOUND - FindmbedTLS.cmake may work with just the target
        set(MBEDTLS_FOUND TRUE CACHE BOOL "mbedTLS found" FORCE)
    endif()
endif()

# If target not found or include dir not determined, fall back to standard logic
# But only if not already cached as found
if(NOT MBEDTLS_FOUND)
    # Include OpenVPN 3's standard FindmbedTLS.cmake
    set(FIND_MBEDTLS_PATH "${CMAKE_CURRENT_LIST_DIR}/../../libs/openvpn3/cmake/FindmbedTLS.cmake")
    if(EXISTS "${FIND_MBEDTLS_PATH}")
        include("${FIND_MBEDTLS_PATH}")
    else()
        message(FATAL_ERROR "Could not find mbedTLS and OpenVPN 3's FindmbedTLS.cmake not found at ${FIND_MBEDTLS_PATH}")
    endif()
endif()

