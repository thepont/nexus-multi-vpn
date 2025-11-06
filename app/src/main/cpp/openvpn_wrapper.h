#ifndef OPENVPN_WRAPPER_H
#define OPENVPN_WRAPPER_H

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

// Forward declaration of OpenVPN session
struct OpenVpnSession;

#ifdef __cplusplus
// Forward declaration for C++ code
#ifdef OPENVPN3_AVAILABLE
class AndroidOpenVPNClient;
#endif
#endif

// Session management
OpenVpnSession* openvpn_wrapper_create_session();
void openvpn_wrapper_destroy_session(OpenVpnSession* session);

// Error codes
#define OPENVPN_ERROR_SUCCESS 0
#define OPENVPN_ERROR_INVALID_PARAMS -1
#define OPENVPN_ERROR_CONFIG_FAILED -2
#define OPENVPN_ERROR_AUTH_FAILED -3
#define OPENVPN_ERROR_CONNECTION_FAILED -4
#define OPENVPN_ERROR_UNKNOWN -5
#define OPENVPN_ERROR_INTERNAL -6

// Connection management
// Returns error code (see above), error message is stored in session->last_error
int openvpn_wrapper_connect(OpenVpnSession* session,
                           const char* config,
                           const char* username,
                           const char* password);

// Set tunnel ID and IP address callback
// This must be called before connect() to receive tunnel IP addresses
#ifdef __cplusplus
#include <jni.h>
void openvpn_wrapper_set_tunnel_id_and_callback(OpenVpnSession* session,
                                                 JNIEnv* env,
                                                 const char* tunnelId,
                                                 jobject ipCallback,
                                                 jobject dnsCallback);
#endif

// Set Android-specific parameters (VpnService.Builder and TUN file descriptor)
// This must be called before connect() if using Android VpnService
#ifdef __cplusplus
#include <jni.h>
void openvpn_wrapper_set_android_params(OpenVpnSession* session,
                                        JNIEnv* env,
                                        jobject vpnBuilder,
                                        jint tunFd,
                                        jobject vpnService);
#endif

// Get last error message from session
const char* openvpn_wrapper_get_last_error(OpenVpnSession* session);

void openvpn_wrapper_disconnect(OpenVpnSession* session);

// Packet operations
int openvpn_wrapper_send_packet(OpenVpnSession* session,
                                const uint8_t* packet,
                                size_t len);

int openvpn_wrapper_receive_packet(OpenVpnSession* session,
                                   uint8_t** packet,
                                   size_t* len);

// Status
int openvpn_wrapper_is_connected(OpenVpnSession* session);

// Get app FD from External TUN Factory (for OPENVPN_EXTERNAL_TUN_FACTORY mode)
int openvpn_wrapper_get_app_fd(OpenVpnSession* session);

#ifdef __cplusplus
}
#endif

#endif // OPENVPN_WRAPPER_H

