// OpenVPN 3 C++ wrapper implementation
// This file contains the integration with the OpenVPN 3 library

#include "openvpn_wrapper.h"
#include <jni.h>  // For JNI types (JavaVM, JNIEnv, jobject)
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
#include <openvpn/client/dns_options.hpp>  // For DnsOptions
using namespace openvpn::ClientAPI;

// OpenVPN 3 ClientAPI is now included and ready to use

#include <thread>
#include <mutex>
#include <atomic>
#include <chrono>

// Custom OpenVPN client implementation for Android
// This class implements the abstract virtual methods required by OpenVPNClient
// AND overrides TunBuilderBase methods to use Android's VpnService.Builder
class AndroidOpenVPNClient : public OpenVPNClient {
public:
    AndroidOpenVPNClient() : OpenVPNClient(), javaVM_(nullptr), vpnBuilder_(nullptr), tunFd_(-1) {
        LOGI("AndroidOpenVPNClient created");
    }
    
    virtual ~AndroidOpenVPNClient() {
        // Cleanup will be done by OpenVPNClient destructor
        LOGI("AndroidOpenVPNClient destroyed");
    }
    
    // Set JavaVM for getting JNIEnv in any thread
    void setJavaVM(JavaVM* vm) {
        javaVM_ = vm;
        LOGI("JavaVM set in AndroidOpenVPNClient");
    }
    
    // Set the Android VpnService.Builder instance (called from JNI)
    // This is public so it can be called from openvpn_wrapper_set_android_params
    void setVpnServiceBuilder(JNIEnv* env, jobject vpnBuilder) {
        if (!env) {
            LOGE("Cannot set VpnService.Builder: JNI env not available");
            return;
        }
        
        // Store JavaVM for later use (JNIEnv is thread-local)
        if (!javaVM_) {
            env->GetJavaVM(&javaVM_);
        }
        
        if (vpnBuilder_) {
            JNIEnv* currentEnv = getJNIEnv();
            if (currentEnv) {
                currentEnv->DeleteGlobalRef(vpnBuilder_);
            }
        }
        
        if (vpnBuilder) {
            vpnBuilder_ = env->NewGlobalRef(vpnBuilder);
            LOGI("VpnService.Builder set in AndroidOpenVPNClient");
        } else {
            vpnBuilder_ = nullptr;
        }
    }
    
    // Method to set the TUN file descriptor (called from JNI after establishing VpnService)
    // This is public so it can be called from openvpn_wrapper_set_android_params
    void setTunFileDescriptor(int fd) {
        tunFd_ = fd;
        LOGI("TUN file descriptor set to: %d", fd);
    }
    
    // Get JNIEnv for current thread
    JNIEnv* getJNIEnv() {
        if (!javaVM_) {
            return nullptr;
        }
        JNIEnv* env = nullptr;
        int status = javaVM_->GetEnv((void**)&env, JNI_VERSION_1_6);
        if (status != JNI_OK) {
            // Attach current thread if needed
            status = javaVM_->AttachCurrentThread(&env, nullptr);
            if (status != JNI_OK) {
                LOGE("Failed to attach thread to JVM");
                return nullptr;
            }
        }
        return env;
    }
    
private:
    JavaVM* javaVM_;  // JavaVM for getting JNIEnv in any thread
    jobject vpnBuilder_;  // Android VpnService.Builder instance (global ref)
    int tunFd_ = -1;  // TUN file descriptor from Android VpnService
    
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
    
    // Override TunBuilderBase methods to use Android VpnService.Builder
    // These methods are called by OpenVPN 3 during connection setup
    virtual bool tun_builder_add_address(const std::string &address,
                                         int prefix_length,
                                         const std::string &gateway,
                                         bool ipv6,
                                         bool net30) override {
        LOGI("tun_builder_add_address: %s/%d (ipv6=%s)", address.c_str(), prefix_length, ipv6 ? "true" : "false");
        
        // For Android, we use the already-established VpnService interface
        // OpenVPN 3 will configure routes, but we don't need to call Builder methods
        // since the interface is already established. We just return true to allow
        // OpenVPN 3 to continue with connection setup.
        return true;
    }
    
    virtual bool tun_builder_reroute_gw(bool ipv4, bool ipv6, unsigned int flags) override {
        LOGI("tun_builder_reroute_gw: ipv4=%s, ipv6=%s", ipv4 ? "true" : "false", ipv6 ? "true" : "false");
        // Gateway is already rerouted by VpnEngineService (routes to 0.0.0.0/0)
        return true;
    }
    
    virtual bool tun_builder_add_route(const std::string &address,
                                       int prefix_length,
                                       int metric,
                                       bool ipv6) override {
        LOGI("tun_builder_add_route: %s/%d (ipv6=%s)", address.c_str(), prefix_length, ipv6 ? "true" : "false");
        // Routes are already configured by VpnEngineService
        // Additional routes from OpenVPN config can be logged but don't need action
        return true;
    }
    
    virtual bool tun_builder_set_dns_options(const openvpn::DnsOptions &dns) override {
        LOGI("tun_builder_set_dns_options called");
        // DNS is already configured by VpnEngineService
        // OpenVPN 3 DNS options can be logged but don't need action
        // Note: dns.servers is a map, not a vector
        LOGI("DNS options received (servers configured by VpnEngineService)");
        return true;
    }
    
    virtual bool tun_builder_set_mtu(int mtu) override {
        LOGI("tun_builder_set_mtu: %d", mtu);
        // MTU is already set by VpnEngineService
        return true;
    }
    
    virtual bool tun_builder_set_session_name(const std::string &name) override {
        LOGI("tun_builder_set_session_name: %s", name.c_str());
        // Session name is already set by VpnEngineService
        return true;
    }
    
    virtual int tun_builder_establish() override {
        LOGI("═══════════════════════════════════════════════════════");
        LOGI("tun_builder_establish() called by OpenVPN 3");
        LOGI("Current TUN FD: %d", tunFd_);
        LOGI("═══════════════════════════════════════════════════════");
        
        // For Android, the TUN interface is already established by VpnEngineService
        // We return the file descriptor that was set via setTunFileDescriptor()
        if (tunFd_ < 0) {
            LOGE("❌ CRITICAL: TUN file descriptor not set!");
            LOGE("   OpenVPN 3 cannot establish connection without a valid TUN FD");
            LOGE("   This means setTunFileDescriptor() was not called before connect()");
            LOGE("   The FD should be passed via nativeConnect() and setTunFileDescriptor()");
            return -1;
        }
        
        LOGI("✅ Returning TUN file descriptor: %d", tunFd_);
        LOGI("   OpenVPN 3 will use this FD for packet I/O");
        return tunFd_;
    }
};
#endif  // OPENVPN3_AVAILABLE

// OpenVPN session structure
struct OpenVpnSession {
    bool connected;
    std::string last_error;
    
#ifdef OPENVPN3_AVAILABLE
    AndroidOpenVPNClient* androidClient;  // For accessing Android-specific methods
    OpenVPNClient* client;  // Alias for androidClient (polymorphic base pointer)
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
    
    OpenVpnSession() : connected(false), androidClient(nullptr), client(nullptr), should_stop(false) {
        // Initialize Android-specific OpenVPN 3 Client - This implements all required virtual methods
        androidClient = new AndroidOpenVPNClient();
        client = androidClient;  // Store both pointers for convenience
        if (!client || !androidClient) {
            throw std::runtime_error("Failed to create Android OpenVPN 3 client");
        }
    }
    
    ~OpenVpnSession() {
        should_stop = true;
        
        // Stop connection if running
        if (androidClient && connected) {
            try {
                androidClient->stop();
            } catch (...) {
                // Ignore errors during cleanup
            }
        }
        
        // Wait for connection thread to finish
        if (connection_thread.joinable()) {
            connection_thread.join();
        }
        
        // Delete client (androidClient is the actual instance)
        if (androidClient) {
            delete androidClient;
            androidClient = nullptr;
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

// Helper function to set Android-specific parameters
void openvpn_wrapper_set_android_params(OpenVpnSession* session,
                                        JNIEnv* env,
                                        jobject vpnBuilder,
                                        jint tunFd) {
#ifdef OPENVPN3_AVAILABLE
    if (!session || !session->androidClient) {
        LOGE("Invalid session or androidClient not available");
        return;
    }
    
    LOGI("Setting Android params: tunFd=%d", tunFd);
    
    // Store JavaVM globally for use in other threads
    JavaVM* javaVM = nullptr;
    env->GetJavaVM(&javaVM);
    
    // Set JavaVM for getting JNIEnv in any thread
    session->androidClient->setJavaVM(javaVM);
    
    if (vpnBuilder) {
        session->androidClient->setVpnServiceBuilder(env, vpnBuilder);
        LOGI("VpnService.Builder set in AndroidOpenVPNClient");
    }
    
    // CRITICAL: Set TUN file descriptor BEFORE calling connect()
    // OpenVPN 3 will call tun_builder_establish() during connect(), and it needs the FD
    if (tunFd >= 0) {
        session->androidClient->setTunFileDescriptor(tunFd);
        LOGI("✅ TUN file descriptor set: %d", tunFd);
    } else {
        LOGE("❌ WARNING: TUN file descriptor not provided (-1)");
        LOGE("   OpenVPN 3 connect() will call tun_builder_establish() which needs a valid FD");
        LOGE("   Connection will likely fail unless FD is set via reflection");
    }
#else
    LOGE("OpenVPN 3 not available - cannot set Android params");
#endif
}

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
        // NordVPN configs use username/password auth, not client certificates
        // Add client-cert-not-required if not present to prevent "Missing External PKI alias" error
        // Also remove OpenVPN 3 unsupported options
        std::string config_content = std::string(config_str);
        
        // Remove OpenVPN 3 unsupported options (these are OpenVPN 2.x only)
        std::vector<std::string> unsupported_options = {
            "ping-timer-rem",
            "remote-random",
            "fast-io",
            "comp-lzo"
        };
        
        for (const auto& option : unsupported_options) {
            // Remove lines containing the unsupported option
            std::string search_pattern = option;
            size_t pos = 0;
            while ((pos = config_content.find(search_pattern, pos)) != std::string::npos) {
                // Find the start of the line
                size_t line_start = config_content.rfind('\n', pos);
                if (line_start == std::string::npos) {
                    line_start = 0;
                } else {
                    line_start++; // Skip the newline
                }
                // Find the end of the line
                size_t line_end = config_content.find('\n', pos);
                if (line_end == std::string::npos) {
                    line_end = config_content.length();
                } else {
                    line_end++; // Include the newline
                }
                // Remove the entire line
                config_content.erase(line_start, line_end - line_start);
                pos = line_start; // Continue searching from where we removed
            }
        }
        
        // CRITICAL FIX: Remove auth-user-pass from config when using OpenVPN 3 ClientAPI
        // OpenVPN 3 ClientAPI expects credentials via provide_creds(), NOT via auth file
        // Having both causes OpenVPN 3 to try reading from a file path that may not be accessible
        // or conflict with the credentials we provide programmatically
        if (config_content.find("auth-user-pass") != std::string::npos) {
            // Find and remove the auth-user-pass line (with or without file path)
            size_t pos = 0;
            while ((pos = config_content.find("auth-user-pass", pos)) != std::string::npos) {
                // Find the start of the line
                size_t line_start = config_content.rfind('\n', pos);
                if (line_start == std::string::npos) {
                    line_start = 0;
                } else {
                    line_start++; // Skip the newline
                }
                // Find the end of the line (could be just "auth-user-pass" or "auth-user-pass /path")
                size_t line_end = config_content.find('\n', pos);
                if (line_end == std::string::npos) {
                    line_end = config_content.length();
                } else {
                    line_end++; // Include the newline
                }
                // Remove the entire line
                config_content.erase(line_start, line_end - line_start);
                LOGI("Removed 'auth-user-pass' line from config (using provide_creds() instead)");
                // Don't continue searching - we only expect one auth-user-pass line
                break;
            }
        }
        
        if (config_content.find("client-cert-not-required") == std::string::npos) {
            // Add it before other auth directives or at the end
            if (config_content.find("auth ") != std::string::npos) {
                // Insert before auth directive
                size_t pos = config_content.find("auth ");
                config_content.insert(pos, "client-cert-not-required\n");
            } else {
                // Append at the end
                config_content += "\nclient-cert-not-required\n";
            }
            LOGI("Added 'client-cert-not-required' directive to config");
        }
        
        LOGI("OpenVPN config processed (%zu bytes, removed unsupported options)", config_content.length());
        
        session->config.content = config_content;
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
        // username and password are already UTF-8 from JNI GetStringUTFChars()
        // OpenVPN 3 ClientAPI's ProvideCreds uses std::string which stores UTF-8 bytes
        session->creds.username = std::string(username);
        session->creds.password = std::string(password);
        
        // Log credential info (without exposing password)
        LOGI("Providing credentials...");
        LOGI("Username length: %zu bytes (UTF-8)", session->creds.username.length());
        LOGI("Password length: %zu bytes (UTF-8)", session->creds.password.length());
        
        // Verify UTF-8 encoding by checking first byte
        if (!session->creds.username.empty()) {
            LOGI("Username first byte: 0x%02x (UTF-8)", (unsigned char)session->creds.username[0]);
        }
        
        // Verify credentials are not empty
        if (session->creds.username.empty() || session->creds.password.empty()) {
            LOGE("Credentials are empty - username: %zu bytes, password: %zu bytes", 
                 session->creds.username.length(), session->creds.password.length());
            session->last_error = "Credentials are empty";
            return OPENVPN_ERROR_INVALID_PARAMS;
        }
        
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
        // OpenVPN 3 connect() will call tun_builder_establish() which needs the TUN FD
        LOGI("Waiting for OpenVPN 3 connection to start...");
        std::this_thread::sleep_for(std::chrono::milliseconds(1000));
        
        {
            std::lock_guard<std::mutex> lock(session->state_mutex);
            if (session->connected) {
                LOGI("✅ Connection established immediately!");
                return OPENVPN_ERROR_SUCCESS;
            }
            
            // Check if there's an error message
            if (!session->last_error.empty()) {
                LOGE("Connection error detected: %s", session->last_error.c_str());
                // Don't return error yet - connection might still be in progress
            }
        }
        
        // Connection is in progress, return success (connection will complete asynchronously)
        LOGI("Connection initiated, will complete asynchronously...");
        LOGI("Note: Connection status will be updated in background thread");
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
        
        if (!session->client) {
            return 0;
        }
        
        // Check the session->connected flag first (set by background thread when connection succeeds)
        // This is more reliable than ConnectionInfo which might not be immediately available
        if (session->connected) {
            // Connection is established according to background thread
            // Optionally verify with ConnectionInfo, but don't fail if it's not ready yet
            try {
                ConnectionInfo info = session->client->connection_info();
                // If ConnectionInfo is available, use it; otherwise trust session->connected
                if (info.defined) {
                    return 1;
                }
                // ConnectionInfo not ready yet, but session->connected is true
                // This is OK - connection might still be finalizing
                return 1;
            } catch (...) {
                // ConnectionInfo might throw if not ready - that's OK, use session->connected
                return session->connected ? 1 : 0;
            }
        }
        
        return 0;
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
