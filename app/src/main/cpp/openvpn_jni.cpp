#include <jni.h>
#include <string>
#include <android/log.h>
#include <unistd.h>    // For close()
#include <sys/socket.h>  // For socketpair()
#include <fcntl.h>     // For fcntl(), O_NONBLOCK
#include <errno.h>     // For errno
#include <cstring>     // For strerror()
#include <map>         // For std::map
#include <mutex>       // For std::mutex
#include "openvpn_wrapper.h"

// Forward declare OpenVpnSession to avoid incomplete type issues
// The actual definition is in openvpn_wrapper.cpp
struct OpenVpnSession;

// Global JavaVM reference (set once at library load)
static JavaVM* g_javaVM = nullptr;

// Global map of sessions keyed by tunnel ID
// Used to retrieve session objects for app FD access
static std::map<std::string, OpenVpnSession*> sessions;
static std::mutex sessions_mutex;

#define LOG_TAG "OpenVPN-JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Forward declarations
extern "C" {
    // JNI functions that will be called from Kotlin
    JNIEXPORT jlong JNICALL
    Java_com_multiregionvpn_core_vpnclient_NativeOpenVpnClient_nativeConnect(
            JNIEnv *env, jobject thiz,
            jstring config, jstring username, jstring password,
            jobject vpnBuilder, jint tunFd, jobject vpnService, jstring tunnelId);
    
    JNIEXPORT void JNICALL
    Java_com_multiregionvpn_core_vpnclient_NativeOpenVpnClient_nativeDisconnect(
            JNIEnv *env, jobject thiz, jlong sessionHandle);
    
    JNIEXPORT jint JNICALL
    Java_com_multiregionvpn_core_vpnclient_NativeOpenVpnClient_nativeSendPacket(
            JNIEnv *env, jobject thiz, jlong sessionHandle, jbyteArray packet);
    
    JNIEXPORT jbyteArray JNICALL
    Java_com_multiregionvpn_core_vpnclient_NativeOpenVpnClient_nativeReceivePacket(
            JNIEnv *env, jobject thiz, jlong sessionHandle);
    
    JNIEXPORT jboolean JNICALL
    Java_com_multiregionvpn_core_vpnclient_NativeOpenVpnClient_nativeIsConnected(
            JNIEnv *env, jobject thiz, jlong sessionHandle);
    
    JNIEXPORT jstring JNICALL
    Java_com_multiregionvpn_core_vpnclient_NativeOpenVpnClient_nativeGetLastError(
            JNIEnv *env, jobject thiz, jlong sessionHandle);
    
    JNIEXPORT void JNICALL
    Java_com_multiregionvpn_core_vpnclient_NativeOpenVpnClient_nativeSetTunnelIdAndCallback(
            JNIEnv *env, jobject thiz,
            jlong sessionHandle, jstring tunnelId, jobject ipCallback, jobject dnsCallback);
    
    JNIEXPORT jint JNICALL
    Java_com_multiregionvpn_core_vpnclient_NativeOpenVpnClient_getAppFd(
            JNIEnv *env, jobject thiz, jstring tunnelId);
    
    // JNI functions for VpnConnectionManager to create pipes
    JNIEXPORT jint JNICALL
    Java_com_multiregionvpn_core_VpnConnectionManager_createPipe(
            JNIEnv *env, jobject thiz, jstring tunnelId);
    
    JNIEXPORT jint JNICALL
    Java_com_multiregionvpn_core_VpnConnectionManager_getPipeWriteFd(
            JNIEnv *env, jobject thiz, jstring tunnelId);
    
    JNIEXPORT jint JNICALL
    Java_com_multiregionvpn_core_VpnConnectionManager_getPipeReadFd(
            JNIEnv *env, jobject thiz, jstring tunnelId);
}

// Implementation using OpenVPN 3 wrapper
// (openvpn_wrapper.cpp will contain the actual OpenVPN 3 integration)

JNIEXPORT jlong JNICALL
Java_com_multiregionvpn_core_vpnclient_NativeOpenVpnClient_nativeConnect(
        JNIEnv *env, jobject thiz,
        jstring config, jstring username, jstring password,
        jobject vpnBuilder, jint tunFd, jobject vpnService, jstring tunnelId) {
    
    LOGI("nativeConnect called - Using OpenVPN 3 ClientAPI service");
    LOGI("TUN file descriptor: %d", tunFd);
    
    // Get strings from JNI
    // GetStringUTFChars converts Java String (UTF-16) to UTF-8 C string
    // This is the correct encoding for OpenVPN 3 ClientAPI which expects UTF-8
    const char *configStr = env->GetStringUTFChars(config, nullptr);
    const char *usernameStr = env->GetStringUTFChars(username, nullptr);
    const char *passwordStr = env->GetStringUTFChars(password, nullptr);
    const char *tunnelIdStr = tunnelId ? env->GetStringUTFChars(tunnelId, nullptr) : nullptr;
    
    if (!configStr || !usernameStr || !passwordStr) {
        LOGE("Failed to get string parameters");
        if (configStr) env->ReleaseStringUTFChars(config, configStr);
        if (usernameStr) env->ReleaseStringUTFChars(username, usernameStr);
        if (passwordStr) env->ReleaseStringUTFChars(password, passwordStr);
        if (tunnelIdStr && tunnelId) env->ReleaseStringUTFChars(tunnelId, tunnelIdStr);
        return 0;
    }
    
    // Log tunnel ID
    if (tunnelIdStr) {
        LOGI("Tunnel ID: %s", tunnelIdStr);
    } else {
        LOGI("Tunnel ID: (not provided)");
    }
    
    // Verify UTF-8 encoding and log credential info (without logging actual password)
    jsize usernameLen = env->GetStringUTFLength(username);
    jsize passwordLen = env->GetStringUTFLength(password);
    LOGI("Credential encoding: username=%d UTF-8 bytes, password=%d UTF-8 bytes", usernameLen, passwordLen);
    
    // Verify strings are valid UTF-8 (GetStringUTFChars ensures this, but log for debugging)
    if (usernameStr && strlen(usernameStr) > 0) {
        LOGI("Username first char: 0x%02x (valid UTF-8)", (unsigned char)usernameStr[0]);
    }
    
    // Create OpenVPN session using wrapper
    OpenVpnSession* session = openvpn_wrapper_create_session();
    if (!session) {
        LOGE("Failed to create OpenVPN session");
        env->ReleaseStringUTFChars(config, configStr);
        env->ReleaseStringUTFChars(username, usernameStr);
        env->ReleaseStringUTFChars(password, passwordStr);
        if (tunnelIdStr && tunnelId) env->ReleaseStringUTFChars(tunnelId, tunnelIdStr);
        return 0;
    }
    
    // Set Android-specific parameters (VpnService.Builder, TUN FD, and VpnService instance)
    openvpn_wrapper_set_android_params(session, env, vpnBuilder, tunFd, vpnService);
    
    // CRITICAL: Set tunnel ID BEFORE connect() is called
    // This ensures AndroidOpenVPNClient has the tunnel ID when new_tun_factory() is called
    // during the connection process in the background thread
    if (tunnelIdStr) {
        openvpn_wrapper_set_tunnel_id_and_callback(session, env, tunnelIdStr, nullptr, nullptr);
        LOGI("✅ Tunnel ID set BEFORE connect: %s", tunnelIdStr);
    }
    
    // Connect using wrapper
    int result = openvpn_wrapper_connect(session, configStr, usernameStr, passwordStr);
    
    // Release string memory
    env->ReleaseStringUTFChars(config, configStr);
    env->ReleaseStringUTFChars(username, usernameStr);
    env->ReleaseStringUTFChars(password, passwordStr);
    if (tunnelIdStr && tunnelId) env->ReleaseStringUTFChars(tunnelId, tunnelIdStr);
    
    if (result != 0) {
        const char* errorMsg = openvpn_wrapper_get_last_error(session);
        LOGE("Failed to connect, error code: %d, error: %s", result, errorMsg ? errorMsg : "Unknown error");
        openvpn_wrapper_destroy_session(session);
        return 0;
    }
    
    // Return session handle (cast pointer to jlong)
    return reinterpret_cast<jlong>(session);
}

JNIEXPORT void JNICALL
Java_com_multiregionvpn_core_vpnclient_NativeOpenVpnClient_nativeDisconnect(
        JNIEnv *env, jobject thiz, jlong sessionHandle) {
    
    LOGI("nativeDisconnect called, handle: %lld", (long long)sessionHandle);
    
    if (sessionHandle == 0) {
        LOGE("Invalid session handle");
        return;
    }
    
    OpenVpnSession* session = reinterpret_cast<OpenVpnSession*>(sessionHandle);
    openvpn_wrapper_disconnect(session);
    openvpn_wrapper_destroy_session(session);
}

JNIEXPORT jint JNICALL
Java_com_multiregionvpn_core_vpnclient_NativeOpenVpnClient_nativeSendPacket(
        JNIEnv *env, jobject thiz, jlong sessionHandle, jbyteArray packet) {
    
    if (!packet) {
        LOGE("Packet is null");
        return -1;
    }
    
    jsize len = env->GetArrayLength(packet);
    jbyte *bytes = env->GetByteArrayElements(packet, nullptr);
    
    if (!bytes) {
        LOGE("Failed to get packet bytes");
        return -1;
    }
    
    LOGI("nativeSendPacket: handle=%lld, size=%d", (long long)sessionHandle, len);
    
    if (sessionHandle == 0) {
        LOGE("Invalid session handle");
        env->ReleaseByteArrayElements(packet, bytes, JNI_ABORT);
        return -1;
    }
    
    OpenVpnSession* session = reinterpret_cast<OpenVpnSession*>(sessionHandle);
    int result = openvpn_wrapper_send_packet(session, (const uint8_t*)bytes, len);
    
    env->ReleaseByteArrayElements(packet, bytes, JNI_ABORT);
    
    return result;
}

JNIEXPORT jbyteArray JNICALL
Java_com_multiregionvpn_core_vpnclient_NativeOpenVpnClient_nativeReceivePacket(
        JNIEnv *env, jobject thiz, jlong sessionHandle) {
    
    if (sessionHandle == 0) {
        return nullptr;
    }
    
    OpenVpnSession* session = reinterpret_cast<OpenVpnSession*>(sessionHandle);
    uint8_t* packet = nullptr;
    size_t len = 0;
    
    int result = openvpn_wrapper_receive_packet(session, &packet, &len);
    
    if (result != 0 || packet == nullptr || len == 0) {
        return nullptr; // No packet available
    }
    
    jbyteArray jpacket = env->NewByteArray(len);
    if (jpacket != nullptr && packet != nullptr) {
        env->SetByteArrayRegion(jpacket, 0, len, (jbyte*)packet);
        // Free the packet buffer allocated by wrapper
        free(packet);
    }
    
    return jpacket;
}

JNIEXPORT jboolean JNICALL
Java_com_multiregionvpn_core_vpnclient_NativeOpenVpnClient_nativeIsConnected(
        JNIEnv *env, jobject thiz, jlong sessionHandle) {
    
    if (sessionHandle == 0) {
        return JNI_FALSE;
    }
    
    OpenVpnSession* session = reinterpret_cast<OpenVpnSession*>(sessionHandle);
    return openvpn_wrapper_is_connected(session) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_multiregionvpn_core_vpnclient_NativeOpenVpnClient_nativeGetLastError(
        JNIEnv *env, jobject thiz, jlong sessionHandle) {
    
    if (sessionHandle == 0) {
        return env->NewStringUTF("Invalid session handle");
    }
    
    OpenVpnSession* session = reinterpret_cast<OpenVpnSession*>(sessionHandle);
    const char* errorMsg = openvpn_wrapper_get_last_error(session);
    return env->NewStringUTF(errorMsg ? errorMsg : "No error");
}

JNIEXPORT void JNICALL
Java_com_multiregionvpn_core_vpnclient_NativeOpenVpnClient_nativeSetTunnelIdAndCallback(
        JNIEnv *env, jobject thiz,
        jlong sessionHandle, jstring tunnelId, jobject ipCallback, jobject dnsCallback) {
    
    if (sessionHandle == 0) {
        LOGE("Invalid session handle for setTunnelIdAndCallback");
        return;
    }
    
    OpenVpnSession* session = reinterpret_cast<OpenVpnSession*>(sessionHandle);
    
    // Convert tunnel ID from Java string to C string
    const char* tunnelIdStr = nullptr;
    if (tunnelId) {
        tunnelIdStr = env->GetStringUTFChars(tunnelId, nullptr);
    }
    
    // Set tunnel ID and callbacks
    openvpn_wrapper_set_tunnel_id_and_callback(session, env, tunnelIdStr, ipCallback, dnsCallback);
    
    // Register session in global map for app FD retrieval
    if (tunnelIdStr) {
        std::lock_guard<std::mutex> lock(sessions_mutex);
        sessions[std::string(tunnelIdStr)] = session;
        LOGI("Registered session for tunnel: %s", tunnelIdStr);
    }
    
    // Release string
    if (tunnelIdStr && tunnelId) {
        env->ReleaseStringUTFChars(tunnelId, tunnelIdStr);
    }
}

// CRITICAL: Get the app FD from External TUN Factory
// This FD is created by CustomTunClient and is used for packet I/O
// Our app writes plaintext packets to this FD, OpenVPN reads and encrypts them
// OpenVPN writes decrypted packets to this FD, our app reads them
JNIEXPORT jint JNICALL
Java_com_multiregionvpn_core_vpnclient_NativeOpenVpnClient_getAppFd(
        JNIEnv *env, jobject thiz, jstring tunnelId) {
    
    if (!tunnelId) {
        LOGE("getAppFd: tunnelId is null");
        return -1;
    }
    
    const char* tunnelIdStr = env->GetStringUTFChars(tunnelId, nullptr);
    if (!tunnelIdStr) {
        LOGE("getAppFd: failed to get tunnelId string");
        return -1;
    }
    
    // Get session for this tunnel
    OpenVpnSession* session = nullptr;
    {
        std::lock_guard<std::mutex> lock(sessions_mutex);
        auto it = sessions.find(tunnelIdStr);
        if (it == sessions.end()) {
            LOGE("getAppFd: No session found for tunnel: %s", tunnelIdStr);
            env->ReleaseStringUTFChars(tunnelId, tunnelIdStr);
            return -1;
        }
        session = it->second;
    }
    
    if (!session) {
        LOGE("getAppFd: Session pointer is null for tunnel: %s", tunnelIdStr);
        env->ReleaseStringUTFChars(tunnelId, tunnelIdStr);
        return -1;
    }
    
    // Use wrapper function to get app FD (avoids incomplete type issues)
    int appFd = openvpn_wrapper_get_app_fd(session);
    
    if (appFd < 0) {
        LOGE("getAppFd: Failed to get app FD for tunnel: %s", tunnelIdStr);
        env->ReleaseStringUTFChars(tunnelId, tunnelIdStr);
        return -1;
    }
    
    LOGI("getAppFd: Retrieved app FD %d for tunnel: %s", appFd, tunnelIdStr);
    env->ReleaseStringUTFChars(tunnelId, tunnelIdStr);
    return appFd;
}

// Structure to hold socket pair FDs for a tunnel
struct TunnelSockets {
    int openvpnFd;      // OpenVPN 3 uses this (bidirectional - reads packets, writes responses)
    int kotlinFd;       // Kotlin uses this (bidirectional - writes packets, reads responses)
};

// Global map to store socket pair FDs per tunnel
static std::map<std::string, TunnelSockets> tunnel_sockets;
static std::mutex sockets_mutex;

JNIEXPORT jint JNICALL
Java_com_multiregionvpn_core_VpnConnectionManager_createPipe(
        JNIEnv *env, jobject thiz, jstring tunnelId) {
    
    if (!tunnelId) {
        LOGE("createPipe: tunnelId is null");
        return -1;
    }
    
    const char* tunnelIdStr = env->GetStringUTFChars(tunnelId, nullptr);
    if (!tunnelIdStr) {
        LOGE("createPipe: failed to get tunnelId string");
        return -1;
    }
    
    std::lock_guard<std::mutex> lock(sockets_mutex);
    
    // Check if socket pair already exists for this tunnel
    if (tunnel_sockets.find(tunnelIdStr) != tunnel_sockets.end()) {
        LOGI("createPipe: Socket pair already exists for tunnel %s, reusing", tunnelIdStr);
        env->ReleaseStringUTFChars(tunnelId, tunnelIdStr);
        return tunnel_sockets[tunnelIdStr].openvpnFd;
    }
    
    // Create socket pair with SOCK_SEQPACKET - bidirectional communication with packet boundaries
    // CRITICAL: SOCK_SEQPACKET preserves message boundaries, which is essential for TUN emulation
    // - TUN devices are packet-oriented: one write() = one packet, one read() = one packet
    // - SOCK_STREAM is stream-oriented: writes can be merged, reads can be split
    // - SOCK_SEQPACKET is packet-oriented: each write is a discrete message
    //
    // This better emulates TUN behavior and should prevent OpenVPN 3 from closing the FD
    int sockets[2];
    if (socketpair(AF_UNIX, SOCK_SEQPACKET, 0, sockets) == -1) {
        LOGE("createPipe: failed to create SEQPACKET socket pair: %s", strerror(errno));
        env->ReleaseStringUTFChars(tunnelId, tunnelIdStr);
        return -1;
    }
    LOGI("createPipe: Created SEQPACKET socket pair (packet-oriented, emulates TUN behavior)");
    
    // CRITICAL: Set OpenVPN 3's FD to non-blocking mode
    // OpenVPN 3's event loop expects non-blocking I/O
    // Without this, OpenVPN 3 might block on read() when no data is available,
    // or close the connection if it times out
    int flags = fcntl(sockets[0], F_GETFL, 0);
    if (flags >= 0) {
        fcntl(sockets[0], F_SETFL, flags | O_NONBLOCK);
        LOGI("createPipe: Set OpenVPN 3 FD (%d) to non-blocking mode", sockets[0]);
    } else {
        LOGW("createPipe: Failed to get flags for OpenVPN 3 FD: %s", strerror(errno));
    }
    
    // IMPORTANT: Keep Kotlin FD in BLOCKING mode
    // FileInputStream.read() expects blocking I/O and will throw exceptions or return -1
    // if the FD is non-blocking and no data is available (EAGAIN/EWOULDBLOCK)
    // We want to block until data is available or EOF occurs
    LOGI("createPipe: Kotlin FD (%d) remains in BLOCKING mode for FileInputStream", sockets[1]);
    
    // Store socket FDs
    TunnelSockets sockPair;
    sockPair.openvpnFd = sockets[0];  // OpenVPN 3 uses this (bidirectional)
    sockPair.kotlinFd = sockets[1];  // Kotlin uses this (bidirectional)
    
    tunnel_sockets[tunnelIdStr] = sockPair;
    
    LOGI("createPipe: Created SEQPACKET socket pair for tunnel %s", tunnelIdStr);
    LOGI("   OpenVPN 3 FD: %d (packet-oriented, non-blocking, emulates TUN read/write)", sockPair.openvpnFd);
    LOGI("   Kotlin FD: %d (packet-oriented, blocking, for PacketRouter)", sockPair.kotlinFd);
    LOGI("   Each write() = one packet, each read() = one packet (preserves boundaries)");
    
    // Return OpenVPN 3 FD (it will read packets from and write responses to this)
    env->ReleaseStringUTFChars(tunnelId, tunnelIdStr);
    return sockPair.openvpnFd;
}

// JNI function to get the Kotlin FD (bidirectional - for writing packets and reading responses)
JNIEXPORT jint JNICALL
Java_com_multiregionvpn_core_VpnConnectionManager_getPipeWriteFd(
        JNIEnv *env, jobject thiz, jstring tunnelId) {
    
    if (!tunnelId) {
        LOGE("getPipeWriteFd: tunnelId is null");
        return -1;
    }
    
    const char* tunnelIdStr = env->GetStringUTFChars(tunnelId, nullptr);
    if (!tunnelIdStr) {
        return -1;
    }
    
    std::lock_guard<std::mutex> lock(sockets_mutex);
    auto it = tunnel_sockets.find(tunnelIdStr);
    if (it == tunnel_sockets.end()) {
        LOGE("getPipeWriteFd: No socket pair found for tunnel %s", tunnelIdStr);
        env->ReleaseStringUTFChars(tunnelId, tunnelIdStr);
        return -1;
    }
    
    int kotlinFd = it->second.kotlinFd;
    env->ReleaseStringUTFChars(tunnelId, tunnelIdStr);
    return kotlinFd;
}

// JNI function to get the Kotlin FD (same as write FD - socket pair is bidirectional)
JNIEXPORT jint JNICALL
Java_com_multiregionvpn_core_VpnConnectionManager_getPipeReadFd(
        JNIEnv *env, jobject thiz, jstring tunnelId) {
    // Socket pair is bidirectional - same FD for reading and writing
    return Java_com_multiregionvpn_core_VpnConnectionManager_getPipeWriteFd(env, thiz, tunnelId);
}

