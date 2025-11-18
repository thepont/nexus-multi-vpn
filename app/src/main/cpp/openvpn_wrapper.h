#pragma once

#include <stddef.h>
#include <stdint.h>
#include <jni.h>

#define OPENVPN_ERROR_SUCCESS 0
#define OPENVPN_ERROR_INVALID_PARAMS -1
#define OPENVPN_ERROR_CONFIG_FAILED -2
#define OPENVPN_ERROR_AUTH_FAILED -3
#define OPENVPN_ERROR_CONNECTION_FAILED -4
#define OPENVPN_ERROR_UNKNOWN -5
#define OPENVPN_ERROR_INTERNAL -6

#ifdef __cplusplus
extern "C" {
#endif

typedef struct OpenVpnSession OpenVpnSession;

OpenVpnSession* openvpn_wrapper_create_session();
void openvpn_wrapper_destroy_session(OpenVpnSession* session);

void openvpn_wrapper_set_android_params(OpenVpnSession* session,
                                        JNIEnv* env,
                                        jobject vpnBuilder,
                                        jint tunFd,
                                        jobject vpnService);

void openvpn_wrapper_set_tunnel_id_and_callback(OpenVpnSession* session,
                                                JNIEnv* env,
                                                const char* tunnelId,
                                                jobject ipCallback,
                                                jobject dnsCallback,
                                                jobject routeCallback);

int openvpn_wrapper_connect(OpenVpnSession* session,
                            const char* config,
                            const char* username,
                            const char* password);

void openvpn_wrapper_disconnect(OpenVpnSession* session);

int openvpn_wrapper_send_packet(OpenVpnSession* session,
                                const uint8_t* data,
                                size_t length);

int openvpn_wrapper_receive_packet(OpenVpnSession* session,
                                   uint8_t** data,
                                   size_t* length);

int openvpn_wrapper_is_connected(OpenVpnSession* session);

int openvpn_wrapper_get_app_fd(OpenVpnSession* session);

const char* openvpn_wrapper_get_last_error(OpenVpnSession* session);

#ifdef __cplusplus
}
#endif
