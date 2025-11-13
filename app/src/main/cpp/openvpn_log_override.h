#ifndef OPENVPN_LOG_OVERRIDE_H
#define OPENVPN_LOG_OVERRIDE_H

#include <android/log.h>
#include <sstream>

// Override OpenVPN 3's logging macros to use Android logging
// This fixes compilation issues with Android NDK 25 where OpenVPN 3's
// stream operator chaining doesn't compile correctly

#ifdef OPENVPN_LOG
#undef OPENVPN_LOG
#endif

#ifdef OPENVPN_LOG_NTNL
#undef OPENVPN_LOG_NTNL
#endif

// Define OPENVPN_LOG to use Android logging with proper string conversion
#define OPENVPN_LOG(x) \
    do { \
        std::ostringstream __openvpn_log_ss; \
        __openvpn_log_ss << x; \
        __android_log_print(ANDROID_LOG_INFO, "OpenVPN3", "%s", __openvpn_log_ss.str().c_str()); \
    } while(0)

// Define OPENVPN_LOG_NTNL (no-terminate-newline) the same way
#define OPENVPN_LOG_NTNL(x) OPENVPN_LOG(x)

// Also define OPENVPN_LOG_STRING for string-only logging
#define OPENVPN_LOG_STRING(x) __android_log_print(ANDROID_LOG_INFO, "OpenVPN3", "%s", (x).c_str())

#endif // OPENVPN_LOG_OVERRIDE_H

