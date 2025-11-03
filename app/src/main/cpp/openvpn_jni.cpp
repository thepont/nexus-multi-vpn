#include <jni.h>
#include <string>
#include <android/log.h>
#include "openvpn_wrapper.h"

#define LOG_TAG "OpenVPN-JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Forward declarations
extern "C" {
    // JNI functions that will be called from Kotlin
    JNIEXPORT jlong JNICALL
    Java_com_multiregionvpn_core_vpnclient_NativeOpenVpnClient_nativeConnect(
            JNIEnv *env, jobject thiz,
            jstring config, jstring username, jstring password);
    
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
}

// Implementation using OpenVPN 3 wrapper
// (openvpn_wrapper.cpp will contain the actual OpenVPN 3 integration)

JNIEXPORT jlong JNICALL
Java_com_multiregionvpn_core_vpnclient_NativeOpenVpnClient_nativeConnect(
        JNIEnv *env, jobject thiz,
        jstring config, jstring username, jstring password) {
    
    LOGI("nativeConnect called - Using OpenVPN 3 ClientAPI service");
    
    // Get strings from JNI
    const char *configStr = env->GetStringUTFChars(config, nullptr);
    const char *usernameStr = env->GetStringUTFChars(username, nullptr);
    const char *passwordStr = env->GetStringUTFChars(password, nullptr);
    
    if (!configStr || !usernameStr || !passwordStr) {
        LOGE("Failed to get string parameters");
        if (configStr) env->ReleaseStringUTFChars(config, configStr);
        if (usernameStr) env->ReleaseStringUTFChars(username, usernameStr);
        if (passwordStr) env->ReleaseStringUTFChars(password, passwordStr);
        return 0;
    }
    
    // Create OpenVPN session using wrapper
    OpenVpnSession* session = openvpn_wrapper_create_session();
    if (!session) {
        LOGE("Failed to create OpenVPN session");
        env->ReleaseStringUTFChars(config, configStr);
        env->ReleaseStringUTFChars(username, usernameStr);
        env->ReleaseStringUTFChars(password, passwordStr);
        return 0;
    }
    
    // Connect using wrapper
    int result = openvpn_wrapper_connect(session, configStr, usernameStr, passwordStr);
    
    // Release string memory
    env->ReleaseStringUTFChars(config, configStr);
    env->ReleaseStringUTFChars(username, usernameStr);
    env->ReleaseStringUTFChars(password, passwordStr);
    
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

