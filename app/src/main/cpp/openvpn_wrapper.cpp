// OpenVPN 3 C++ wrapper implementation
// This file contains the integration with the OpenVPN 3 library

#include "openvpn_wrapper.h"
#include <android/log.h>
#include <string>
#include <cstring>
#include <memory>
#include <vector>
#include <cstdlib>

#define LOG_TAG "OpenVPN-Wrapper"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// OpenVPN 3 API includes
#ifdef OPENVPN3_AVAILABLE
// CRITICAL: Include system headers that OpenVPN 3 expects to be available
// These are normally included transitively through OpenVPN 3's build, but when
// compiling headers directly we need to include them explicitly
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>

// CRITICAL: Include asio BEFORE OpenVPN 3 headers so openvpn_io namespace is defined
// OpenVPN 3's io.hpp requires USE_ASIO to be defined and asio.hpp to be included
#if defined(USE_ASIO) && defined(ASIO_STANDALONE)
    #include <asio.hpp>
#endif

// Using OpenVPN 3 ClientAPI - This is the real OpenVPN 3 service/library
#include <client/ovpncli.hpp>
using namespace openvpn::ClientAPI;

// OpenVPN 3 ClientAPI is now included and ready to use

#include <thread>
#include <mutex>
#include <atomic>
#include <chrono>

// Custom OpenVPN client implementation for Android
// This class implements the abstract virtual methods required by OpenVPNClient
class AndroidOpenVPNClient : public OpenVPNClient {
public:
    AndroidOpenVPNClient() : OpenVPNClient() {
        LOGI("AndroidOpenVPNClient created");
    }
    
    virtual ~AndroidOpenVPNClient() {
        LOGI("AndroidOpenVPNClient destroyed");
    }
    
    // Implement LogReceiver::log
    virtual void log(const LogInfo &log_info) override {
        LOGI("OpenVPN: %s", log_info.text.c_str());
    }
    
    // Implement event callback
    virtual void event(const Event &evt) override {
        if (evt.error) {
            LOGE("OpenVPN Event [%s]: %s %s", evt.name.c_str(), 
                 evt.fatal ? "(FATAL)" : "(non-fatal)", evt.info.c_str());
        } else {
            LOGI("OpenVPN Event [%s]: %s", evt.name.c_str(), evt.info.c_str());
        }
        
        // Handle CONNECTED event to update connection state
        if (evt.name == "CONNECTED") {
            LOGI("OpenVPN connection established");
        } else if (evt.name == "DISCONNECTED") {
            LOGI("OpenVPN disconnected: %s", evt.info.c_str());
        }
    }
    
    // Implement app custom control message event callback
    virtual void acc_event(const AppCustomControlMessageEvent &evt) override {
        LOGI("OpenVPN AppControl: protocol=%s, payload=%s", 
             evt.protocol.c_str(), evt.payload.c_str());
    }
    
    // Implement external PKI certificate request callback
    virtual void external_pki_cert_request(ExternalPKICertRequest &req) override {
        LOGI("OpenVPN ExternalPKI cert request for alias: %s", req.alias.c_str());
        // Not using external PKI, so mark as error
        req.error = true;
        req.errorText = "External PKI not supported";
        req.invalidAlias = true;
    }
    
    // Implement external PKI sign request callback
    virtual void external_pki_sign_request(ExternalPKISignRequest &req) override {
        LOGI("OpenVPN ExternalPKI sign request for alias: %s", req.alias.c_str());
        // Not using external PKI, so mark as error
        req.error = true;
        req.errorText = "External PKI not supported";
        req.invalidAlias = true;
    }
    
    // Implement pause on connection timeout callback
    virtual bool pause_on_connection_timeout() override {
        LOGI("OpenVPN connection timeout - pausing");
        // Return true to pause instead of disconnecting
        return true;
    }
    
private:
    // Additional state can be added here if needed
};
#endif

// OpenVPN session structure
struct OpenVpnSession {
    bool connected;
    std::string last_error;
    
#ifdef OPENVPN3_AVAILABLE
    AndroidOpenVPNClient* client;
    Config config;
    ProvideCreds creds;
    std::thread connection_thread;
    std::mutex state_mutex;
    std::atomic<bool> should_stop;
    ConnectionInfo connection_info;
    
    // Packet buffers for send/receive
    std::mutex packet_mutex;
    std::vector<uint8_t> send_buffer;
    std::vector<uint8_t> receive_buffer;
    
    OpenVpnSession() : connected(false), client(nullptr), should_stop(false) {
        // Initialize Android-specific OpenVPN 3 Client - This implements all required virtual methods
        client = new AndroidOpenVPNClient();
        if (!client) {
            throw std::runtime_error("Failed to create Android OpenVPN 3 client");
        }
    }
    
    ~OpenVpnSession() {
        should_stop = true;
        
        // Stop connection if running
        if (client && connected) {
            try {
                client->stop();
            } catch (...) {
                // Ignore errors during cleanup
            }
        }
        
        // Wait for connection thread to finish
        if (connection_thread.joinable()) {
            connection_thread.join();
        }
        
        // Delete client
        if (client) {
            delete client;
            client = nullptr;
        }
    }
#else
    void* client; // Placeholder until OpenVPN 3 is integrated
    
    // Packet buffers for send/receive
    std::vector<uint8_t> send_buffer;
    std::vector<uint8_t> receive_buffer;
    
    OpenVpnSession() : connected(false), client(nullptr) {}
    
    ~OpenVpnSession() {}
#endif
};

extern "C" {

OpenVpnSession* openvpn_wrapper_create_session() {
    LOGI("Creating OpenVPN session");
    try {
        OpenVpnSession* session = new OpenVpnSession();
        return session;
    } catch (const std::exception& e) {
        LOGE("Failed to create session: %s", e.what());
        return nullptr;
    }
}

int openvpn_wrapper_connect(OpenVpnSession* session, 
                           const char* config_str,
                           const char* username,
                           const char* password) {
    if (!session || !config_str || !username || !password) {
        LOGE("Invalid parameters for connect");
        if (session) {
            session->last_error = "Invalid parameters: session, config, username, or password is null";
        }
        return OPENVPN_ERROR_INVALID_PARAMS;
    }
    
    LOGI("openvpn_wrapper_connect called");
    LOGI("Using OpenVPN 3 ClientAPI service");
    LOGI("Username: %s", username);
    
#ifdef OPENVPN3_AVAILABLE
    try {
        if (!session->client) {
            LOGE("OpenVPN 3 client not initialized");
            return -1;
        }
        
        // Using OpenVPN 3 ClientAPI - Real service integration
        // 1. Parse and set OpenVPN config
        session->config.content = std::string(config_str);
        session->config.connTimeout = 30;  // Connection timeout in seconds
        session->config.tunPersist = false; // Don't persist TUN interface
        
        LOGI("Evaluating OpenVPN 3 config using ClientAPI...");
        
        // 2. Evaluate the config using OpenVPN 3 service
        EvalConfig eval = session->client->eval_config(session->config);
        if (eval.error) {
            session->last_error = eval.message;
            LOGE("OpenVPN config evaluation failed: %s", eval.message.c_str());
            return OPENVPN_ERROR_CONFIG_FAILED;
        }
        
        LOGI("Config evaluated successfully. Profile: %s", eval.profileName.c_str());
        
        // 3. Set credentials
        session->creds.username = std::string(username);
        session->creds.password = std::string(password);
        
        LOGI("Providing credentials...");
        
        // 4. Provide credentials to OpenVPN 3 service
        Status credsStatus = session->client->provide_creds(session->creds);
        if (credsStatus.error) {
            session->last_error = credsStatus.message;
            LOGE("OpenVPN 3 authentication failed: %s", credsStatus.message.c_str());
            // Check if it's an auth-specific error
            std::string errorMsg = credsStatus.message;
            std::transform(errorMsg.begin(), errorMsg.end(), errorMsg.begin(), ::tolower);
            if (errorMsg.find("auth") != std::string::npos || 
                errorMsg.find("credential") != std::string::npos ||
                errorMsg.find("password") != std::string::npos ||
                errorMsg.find("username") != std::string::npos ||
                errorMsg.find("invalid") != std::string::npos) {
                return OPENVPN_ERROR_AUTH_FAILED;
            }
            return OPENVPN_ERROR_INVALID_PARAMS;
        }
        
        LOGI("Credentials provided to OpenVPN 3 service successfully");
        
        // 5. Start connection using OpenVPN 3 service (connect() is blocking)
        // The connect() method blocks and manages the connection loop via OpenVPN 3
        session->should_stop = false;
        
        LOGI("Starting OpenVPN 3 service connection in background thread...");
        
        session->connection_thread = std::thread([session]() {
            try {
                // This calls OpenVPN 3 service connect() - blocks until disconnect
                Status connectStatus = session->client->connect();
                
                std::lock_guard<std::mutex> lock(session->state_mutex);
                if (connectStatus.error) {
                    session->last_error = connectStatus.message;
                    session->connected = false;
                    LOGE("OpenVPN 3 service connection failed: %s", connectStatus.message.c_str());
                    // Check if it's an auth error that occurred during connection
                    std::string errorMsg = connectStatus.message;
                    std::transform(errorMsg.begin(), errorMsg.end(), errorMsg.begin(), ::tolower);
                    if (errorMsg.find("auth") != std::string::npos || 
                        errorMsg.find("credential") != std::string::npos ||
                        errorMsg.find("password") != std::string::npos ||
                        errorMsg.find("username") != std::string::npos) {
                        // Auth error occurred during connection phase
                        LOGE("Authentication error detected during connection: %s", connectStatus.message.c_str());
                    }
                } else {
                    session->connected = true;
                    LOGI("OpenVPN 3 service connection established successfully");
                }
            } catch (const std::exception& e) {
                std::lock_guard<std::mutex> lock(session->state_mutex);
                session->last_error = e.what();
                session->connected = false;
                LOGE("Exception in connection thread: %s", e.what());
            }
        });
        
        // Wait a bit for connection to start, then check status
        std::this_thread::sleep_for(std::chrono::milliseconds(500));
        
        {
            std::lock_guard<std::mutex> lock(session->state_mutex);
            if (session->connected) {
                return 0;
            }
        }
        
        // Connection is in progress, return success (connection will complete asynchronously)
        LOGI("Connection initiated, waiting for completion...");
        return OPENVPN_ERROR_SUCCESS;
        
    } catch (const std::exception& e) {
        session->last_error = e.what();
        LOGE("Exception during connect: %s", e.what());
        return OPENVPN_ERROR_UNKNOWN;
    }
#else
    // OpenVPN 3 not available - this should not happen in production
    LOGE("OpenVPN 3 not available - build must include OpenVPN 3 library");
    LOGE("Config length: %zu bytes", strlen(config_str));
    session->last_error = "OpenVPN 3 library not compiled into this build. Rebuild with OpenVPN 3 enabled.";
    session->connected = false;
    return OPENVPN_ERROR_UNKNOWN;
#endif
}

void openvpn_wrapper_disconnect(OpenVpnSession* session) {
    if (!session) {
        return;
    }
    
    LOGI("openvpn_wrapper_disconnect called");
    
#ifdef OPENVPN3_AVAILABLE
    try {
        std::lock_guard<std::mutex> lock(session->state_mutex);
        
        if (session->client && session->connected) {
            LOGI("Stopping OpenVPN 3 service connection...");
            session->should_stop = true;
            
            // Stop the OpenVPN 3 service (this will cause connect() to return)
            session->client->stop();
            
            LOGI("OpenVPN 3 service disconnected");
        }
        
        session->connected = false;
        
        // Wait for connection thread to finish
        if (session->connection_thread.joinable()) {
            // Give it a moment to finish
            std::this_thread::sleep_for(std::chrono::milliseconds(100));
            if (session->connection_thread.joinable()) {
                session->connection_thread.detach(); // Detach if still running
            }
        }
    } catch (const std::exception& e) {
        LOGE("Exception during disconnect: %s", e.what());
        std::lock_guard<std::mutex> lock(session->state_mutex);
        session->connected = false;
    }
#else
    session->connected = false;
    LOGI("OpenVPN disconnected (placeholder)");
#endif
}

int openvpn_wrapper_send_packet(OpenVpnSession* session,
                                const uint8_t* packet,
                                size_t len) {
    if (!session || !packet || len == 0) {
        LOGE("Invalid parameters for send_packet");
        return -1;
    }
    
    if (!session->connected) {
        LOGE("Cannot send packet: not connected");
        return -1;
    }
    
#ifdef OPENVPN3_AVAILABLE
    try {
        std::lock_guard<std::mutex> lock(session->packet_mutex);
        
        if (!session->connected) {
            LOGE("Cannot send packet: not connected");
            return -1;
        }
        
        // OpenVPN 3 handles packet I/O through its TunBuilder interface
        // In Android, we need to implement a custom TunBuilder that writes
        // packets to the VpnService's TUN file descriptor.
        // 
        // For now, we queue packets. The actual implementation would require
        // implementing TunBuilderBase callbacks to interface with Android's VpnService.
        
        // Queue packet for processing
        session->send_buffer.insert(session->send_buffer.end(), packet, packet + len);
        
        // TODO: Implement custom TunBuilder that writes to Android VpnService TUN fd
        // This requires implementing the TunBuilderBase virtual methods to:
        // 1. Capture the TUN file descriptor from tun_builder_establish()
        // 2. Write packets directly to that fd
        
        return 0; // Success (packet queued)
    } catch (const std::exception& e) {
        LOGE("Exception during send_packet: %s", e.what());
        return -1;
    }
#else
    // Placeholder: log that we would send the packet
    LOGI("Would send %zu bytes (placeholder)", len);
    return 0;
#endif
}

int openvpn_wrapper_receive_packet(OpenVpnSession* session,
                                   uint8_t** packet,
                                   size_t* len) {
    if (!session || !packet || !len) {
        LOGE("Invalid parameters for receive_packet");
        return -1;
    }
    
    if (!session->connected) {
        return 0; // No packet available
    }
    
    *packet = nullptr;
    *len = 0;
    
#ifdef OPENVPN3_AVAILABLE
    try {
        std::lock_guard<std::mutex> lock(session->packet_mutex);
        
        if (!session->connected) {
            return 0; // No packet available
        }
        
        // OpenVPN 3 receives packets through its TunBuilder interface
        // Packets are read from the TUN file descriptor by OpenVPN 3's internal loop
        // and should be made available through event callbacks.
        //
        // For now, check if we have any packets in our receive buffer
        // In a full implementation, packets would be received through
        // TunBuilderBase callbacks and stored in the receive buffer.
        
        if (!session->receive_buffer.empty()) {
            *len = session->receive_buffer.size();
            *packet = (uint8_t*)malloc(*len);
            if (*packet) {
                memcpy(*packet, session->receive_buffer.data(), *len);
                session->receive_buffer.clear();
                return 1; // Packet available
            }
        }
        
        return 0; // No packet available
    } catch (const std::exception& e) {
        LOGE("Exception during receive_packet: %s", e.what());
        return 0;
    }
#else
    // Placeholder: no packet available
    return 0;
#endif
}

int openvpn_wrapper_is_connected(OpenVpnSession* session) {
    if (!session) {
        return 0;
    }
    
#ifdef OPENVPN3_AVAILABLE
    try {
        std::lock_guard<std::mutex> lock(session->state_mutex);
        
        if (!session->connected || !session->client) {
            return 0;
        }
        
        // Check connection status via OpenVPN 3 service API
        ConnectionInfo info = session->client->connection_info();
        
        // Return true if OpenVPN 3 service reports connection is active
        return info.defined ? 1 : 0;
    } catch (const std::exception& e) {
        LOGE("Exception checking connection: %s", e.what());
        return 0;
    }
#else
    return session->connected ? 1 : 0;
#endif
}

const char* openvpn_wrapper_get_last_error(OpenVpnSession* session) {
    if (!session) {
        return "Session is null";
    }
    
#ifdef OPENVPN3_AVAILABLE
    std::lock_guard<std::mutex> lock(session->state_mutex);
    return session->last_error.empty() ? "No error" : session->last_error.c_str();
#else
    return session->last_error.empty() ? "No error" : session->last_error.c_str();
#endif
}

void openvpn_wrapper_destroy_session(OpenVpnSession* session) {
    if (!session) {
        return;
    }
    
    LOGI("Destroying OpenVPN session");
    
    // Disconnect if still connected
    if (session->connected) {
        openvpn_wrapper_disconnect(session);
    }
    
    delete session;
}

} // extern "C"
