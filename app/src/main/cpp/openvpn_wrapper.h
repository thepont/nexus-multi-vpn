#ifndef OPENVPN_WRAPPER_H
#define OPENVPN_WRAPPER_H

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

// Forward declaration of OpenVPN session
struct OpenVpnSession;

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

// Connection management
// Returns error code (see above), error message is stored in session->last_error
int openvpn_wrapper_connect(OpenVpnSession* session,
                           const char* config,
                           const char* username,
                           const char* password);

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

#ifdef __cplusplus
}
#endif

#endif // OPENVPN_WRAPPER_H

