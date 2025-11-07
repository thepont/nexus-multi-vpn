/**
 * Compile-Time Logging Configuration
 * 
 * Controls logging levels to optimize production performance.
 * 
 * Logging Levels:
 * - RELEASE: Minimal logging (errors only)
 * - DEBUG: Standard logging (info + errors)
 * - VERBOSE: All logging including packet-level details
 * 
 * Performance Impact:
 * - RELEASE: ~0% overhead (only errors)
 * - DEBUG: ~2-5% overhead (conditional checks)
 * - VERBOSE: ~10-15% overhead (packet logging)
 * 
 * Usage:
 * - Production builds: Use RELEASE
 * - Development: Use DEBUG
 * - Troubleshooting: Use VERBOSE
 */

#ifndef MULTIREGIONVPN_LOGGING_CONFIG_H
#define MULTIREGIONVPN_LOGGING_CONFIG_H

#include <android/log.h>

// ============================================================================
// Logging Level Configuration
// ============================================================================

// Define exactly ONE of these (or use CMake to define):
// #define LOGGING_LEVEL_RELEASE   // Errors only (production)
// #define LOGGING_LEVEL_DEBUG     // Info + Errors (development)
// #define LOGGING_LEVEL_VERBOSE   // All logs (troubleshooting)

// Default to DEBUG if nothing specified
#if !defined(LOGGING_LEVEL_RELEASE) && !defined(LOGGING_LEVEL_DEBUG) && !defined(LOGGING_LEVEL_VERBOSE)
    #define LOGGING_LEVEL_DEBUG
#endif

// ============================================================================
// Logging Macros
// ============================================================================

// Error logging (always enabled)
#define LOG_ERROR(tag, ...) \
    __android_log_print(ANDROID_LOG_ERROR, tag, __VA_ARGS__)

// Info logging (enabled in DEBUG and VERBOSE)
#if defined(LOGGING_LEVEL_DEBUG) || defined(LOGGING_LEVEL_VERBOSE)
    #define LOG_INFO(tag, ...) \
        __android_log_print(ANDROID_LOG_INFO, tag, __VA_ARGS__)
    #define LOG_DEBUG(tag, ...) \
        __android_log_print(ANDROID_LOG_DEBUG, tag, __VA_ARGS__)
#else
    #define LOG_INFO(tag, ...) ((void)0)
    #define LOG_DEBUG(tag, ...) ((void)0)
#endif

// Verbose logging (enabled only in VERBOSE mode)
#ifdef LOGGING_LEVEL_VERBOSE
    #define LOG_VERBOSE(tag, ...) \
        __android_log_print(ANDROID_LOG_VERBOSE, tag, __VA_ARGS__)
    
    // Packet-level logging (very verbose, hot path)
    #define LOG_PACKET(tag, ...) \
        __android_log_print(ANDROID_LOG_VERBOSE, tag, __VA_ARGS__)
    
    // Transport-level logging (TCP/UDP/OpenVPN protocol details)
    #define LOG_TRANSPORT(tag, ...) \
        __android_log_print(ANDROID_LOG_INFO, tag, __VA_ARGS__)
#else
    #define LOG_VERBOSE(tag, ...) ((void)0)
    #define LOG_PACKET(tag, ...) ((void)0)
    #define LOG_TRANSPORT(tag, ...) ((void)0)
#endif

// ============================================================================
// Convenience Macros (backward compatibility)
// ============================================================================

// For error messages (always logged)
#define LOGE(...) LOG_ERROR("OpenVPN", __VA_ARGS__)

// For info messages (logged in DEBUG and VERBOSE)
#define LOGI(...) LOG_INFO("OpenVPN", __VA_ARGS__)

// For debug messages (logged in DEBUG and VERBOSE)
#define LOGD(...) LOG_DEBUG("OpenVPN", __VA_ARGS__)

// For verbose messages (logged only in VERBOSE)
#define LOGV(...) LOG_VERBOSE("OpenVPN", __VA_ARGS__)

// ============================================================================
// Performance-Critical Path Logging
// ============================================================================

// For hot paths (packet processing, encryption, etc.)
// Only enabled in VERBOSE mode to minimize performance impact
#ifdef LOGGING_LEVEL_VERBOSE
    #define LOG_HOT_PATH(tag, ...) \
        __android_log_print(ANDROID_LOG_VERBOSE, tag, __VA_ARGS__)
#else
    #define LOG_HOT_PATH(tag, ...) ((void)0)
#endif

// ============================================================================
// Logging Level Info
// ============================================================================

#if defined(LOGGING_LEVEL_RELEASE)
    #pragma message("Logging: RELEASE mode (errors only)")
#elif defined(LOGGING_LEVEL_VERBOSE)
    #pragma message("Logging: VERBOSE mode (all logging enabled)")
#else
    #pragma message("Logging: DEBUG mode (info + errors)")
#endif

#endif // MULTIREGIONVPN_LOGGING_CONFIG_H

