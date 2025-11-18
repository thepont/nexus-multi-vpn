// OpenVPN 3 C++ wrapper implementation
// This file contains the integration with the OpenVPN 3 library

#ifndef OPENVPN_EXTERNAL_TUN_FACTORY
#define OPENVPN_EXTERNAL_TUN_FACTORY 1
#endif

#include "openvpn_wrapper.h"
#include <jni.h>  // For JNI types (JavaVM, JNIEnv, jobject)
#include <android/log.h>
#include <string>
#include <cstring>
#include <memory>
#include <vector>
#include <cstdlib>
#include <unistd.h>  // For write()
#include <errno.h>   // For errno

#define LOG_TAG "OpenVPN-Wrapper"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

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

// CRITICAL: Include External TUN Factory for proper custom TUN implementation
#ifdef OPENVPN_EXTERNAL_TUN_FACTORY
#include "external_tun_factory.h"
#include "custom_tun_client.h"
// External TUN mode enabled - OpenVPN 3 will actively poll our socketpair FD
#endif

// OpenVPN 3 ClientAPI is now included and ready to use

#include <thread>
#include <mutex>
#include <atomic>
#include <chrono>

// Custom OpenVPN client implementation for Android
// This class implements the abstract virtual methods required by OpenVPNClient
// AND overrides TunBuilderBase methods to use Android's VpnService.Builder
// Note: OpenVPNClient already inherits from ExternalTun::Factory, so we just override its methods
// ALSO implements CustomTunCallback to receive IP/DNS notifications from CustomTunClient
class AndroidOpenVPNClient : public OpenVPNClient, public openvpn::CustomTunCallback {
public:
    AndroidOpenVPNClient() : OpenVPNClient(), javaVM_(nullptr), vpnBuilder_(nullptr), vpnService_(nullptr), tunFd_(-1), session_(nullptr), ipAddressCallback_(nullptr), dnsCallback_(nullptr), routeCallback_(nullptr), sessionJavaVM_(nullptr), destroying_(false)
#ifdef OPENVPN_EXTERNAL_TUN_FACTORY
        , customTunClientFactory_(nullptr), factoryCreated_(false)
#endif
    {
        LOGI("AndroidOpenVPNClient created");
    }
    
    virtual ~AndroidOpenVPNClient() {
        // Mark as destroyed to prevent callback access during cleanup
        destroying_ = true;
        
        // Cleanup will be done by OpenVPNClient destructor
        // OpenVPN 3 owns and deletes the factory - we just null our pointer
#ifdef OPENVPN_EXTERNAL_TUN_FACTORY
        customTunClientFactory_ = nullptr;
        factoryCreated_ = false;
#endif
        
        // Clear callback pointers to prevent dangling references
        ipAddressCallback_ = nullptr;
        dnsCallback_ = nullptr;
        routeCallback_ = nullptr;
        sessionJavaVM_ = nullptr;
        
        LOGI("AndroidOpenVPNClient destroyed");
    }
    
    // Set JavaVM for getting JNIEnv in any thread
    void setJavaVM(JavaVM* vm) {
        javaVM_ = vm;
        LOGI("JavaVM set in AndroidOpenVPNClient");
    }
    
    // Set the session pointer so event() can update connection state
    // This is public so it can be called from openvpn_wrapper_connect()
    // Implementation moved after OpenVpnSession definition to avoid forward declaration issues
    void setSession(OpenVpnSession* session);
    
    // Get the session pointer (used by tun_builder_add_address)
    OpenVpnSession* getSession() {
        return session_;
    }
    
    // Update session callback info (called when callback is set on session)
    // Implementation after OpenVpnSession definition to avoid forward declaration issues
    void updateSessionCallbackInfo(OpenVpnSession* session);
    
    // Set stored credentials (called from openvpn_wrapper_connect)
    // This is public so it can be called from the wrapper
    void setStoredCredentials(const std::string& username, const std::string& password) {
        stored_username_ = username;
        stored_password_ = password;
        LOGI("Stored credentials for client_auth() callback: username=%zu bytes", username.length());
    }
    
    // Set the Android VpnService instance (called from JNI)
    // This is public so it can be called from openvpn_wrapper_set_android_params
    void setVpnService(JNIEnv* env, jobject vpnService) {
        if (!env) {
            LOGE("Cannot set VpnService: JNI env not available");
            return;
        }
        
        // Store JavaVM for later use (JNIEnv is thread-local)
        if (!javaVM_) {
            env->GetJavaVM(&javaVM_);
        }
        
        if (vpnService_) {
            JNIEnv* currentEnv = getJNIEnv();
            if (currentEnv) {
                currentEnv->DeleteGlobalRef(vpnService_);
            }
        }
        
        if (vpnService) {
            vpnService_ = env->NewGlobalRef(vpnService);
            LOGI("VpnService instance set in AndroidOpenVPNClient");
        }
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
    
    // Get the TUN file descriptor (used by send_packet)
    // This is public so it can be called from openvpn_wrapper_send_packet
    int getTunFd() const {
        return tunFd_;
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
    
#ifdef OPENVPN_EXTERNAL_TUN_FACTORY
    // Set the tunnel ID (must be called before connect)
    void setTunnelId(const std::string& tunnel_id) {
        tunnelId_ = tunnel_id;
        LOGI("Tunnel ID set to: %s", tunnel_id.c_str());
    }
    
    // Override ExternalTun::Factory::new_tun_factory()
    // OpenVPNClient already inherits from ExternalTun::Factory
    virtual openvpn::TunClientFactory* new_tun_factory(const openvpn::ExternalTun::Config& conf, 
                                                        const openvpn::OptionList& opt) override {
        LOGI("AndroidOpenVPNClient::new_tun_factory() for tunnel: %s", tunnelId_.c_str());
        
        // Create CustomTunClientFactory with callback (this)
        // OpenVPN 3 takes ownership and will delete it - we just keep a non-owning pointer
        customTunClientFactory_ = new openvpn::CustomTunClientFactory(tunnelId_, this);
        factoryCreated_ = true;
        
        LOGI("Created CustomTunClientFactory with callback for IP/DNS notifications");
        return customTunClientFactory_;
    }
    
    // Implement CustomTunCallback::on_ip_assigned
    virtual void on_ip_assigned(const std::string& tunnel_id, const std::string& ip, int prefix_len) override {
        LOGI("✅ on_ip_assigned callback: tunnel=%s, ip=%s/%d", tunnel_id.c_str(), ip.c_str(), prefix_len);
        
        // Forward to Android callbacks if available
        if (!destroying_ && ipAddressCallback_ && sessionJavaVM_ && !tunnel_id.empty()) {
            JNIEnv* env = nullptr;
            jint result = sessionJavaVM_->GetEnv((void**)&env, JNI_VERSION_1_6);
            if (result == JNI_EDETACHED) {
                result = sessionJavaVM_->AttachCurrentThread(&env, nullptr);
                if (result != JNI_OK || env == nullptr) {
                    LOGW("Cannot attach thread to JNI for IP callback");
                    return;
                }
            } else if (result != JNI_OK || env == nullptr) {
                LOGW("Cannot get JNIEnv for IP callback");
                return;
            }
            
            jclass callbackClass = env->GetObjectClass(ipAddressCallback_);
            if (callbackClass) {
                jmethodID methodId = env->GetMethodID(callbackClass, "onTunnelIpReceived", 
                    "(Ljava/lang/String;Ljava/lang/String;I)V");
                if (methodId) {
                    jstring tunnelIdStr = env->NewStringUTF(tunnel_id.c_str());
                    jstring ipStr = env->NewStringUTF(ip.c_str());
                    
                    env->CallVoidMethod(ipAddressCallback_, methodId, tunnelIdStr, ipStr, prefix_len);
                    
                    if (env->ExceptionCheck()) {
                        env->ExceptionDescribe();
                        env->ExceptionClear();
                    }
                    
                    env->DeleteLocalRef(tunnelIdStr);
                    env->DeleteLocalRef(ipStr);
                    env->DeleteLocalRef(callbackClass);
                    
                    LOGI("✅ Notified Kotlin about tunnel IP: tunnel=%s, ip=%s/%d", 
                         tunnel_id.c_str(), ip.c_str(), prefix_len);
                } else {
                    LOGW("Cannot find onTunnelIpReceived method in callback");
                    env->DeleteLocalRef(callbackClass);
                }
            }
        }
    }
    
    // Implement CustomTunCallback::on_dns_configured
    virtual void on_dns_configured(const std::string& tunnel_id, const std::vector<std::string>& dns_servers) override {
        LOGI("✅ on_dns_configured callback: tunnel=%s, dns_count=%zu", tunnel_id.c_str(), dns_servers.size());
        
        // Forward to Android callbacks if available
        if (!destroying_ && dnsCallback_ && sessionJavaVM_ && !tunnel_id.empty() && !dns_servers.empty()) {
            JNIEnv* env = nullptr;
            jint result = sessionJavaVM_->GetEnv((void**)&env, JNI_VERSION_1_6);
            if (result == JNI_EDETACHED) {
                result = sessionJavaVM_->AttachCurrentThread(&env, nullptr);
                if (result != JNI_OK || env == nullptr) {
                    LOGW("Cannot attach thread to JNI for DNS callback");
                    return;
                }
            } else if (result != JNI_OK || env == nullptr) {
                LOGW("Cannot get JNIEnv for DNS callback");
                return;
            }
            
            jclass callbackClass = env->GetObjectClass(dnsCallback_);
            if (callbackClass) {
                // Create ArrayList for DNS servers
                jclass arrayListClass = env->FindClass("java/util/ArrayList");
                jmethodID arrayListInit = env->GetMethodID(arrayListClass, "<init>", "(I)V");
                jmethodID arrayListAdd = env->GetMethodID(arrayListClass, "add", "(Ljava/lang/Object;)Z");
                
                jobject dnsList = env->NewObject(arrayListClass, arrayListInit, (jint)dns_servers.size());
                
                for (const auto& dns : dns_servers) {
                    jstring dnsStr = env->NewStringUTF(dns.c_str());
                    env->CallBooleanMethod(dnsList, arrayListAdd, dnsStr);
                    env->DeleteLocalRef(dnsStr);
                }
                
                jmethodID methodId = env->GetMethodID(callbackClass, "onTunnelDnsReceived", 
                    "(Ljava/lang/String;Ljava/util/List;)V");
                if (methodId) {
                    jstring tunnelIdStr = env->NewStringUTF(tunnel_id.c_str());
                    
                    env->CallVoidMethod(dnsCallback_, methodId, tunnelIdStr, dnsList);
                    
                    if (env->ExceptionCheck()) {
                        env->ExceptionDescribe();
                        env->ExceptionClear();
                    }
                    
                    env->DeleteLocalRef(tunnelIdStr);
                    env->DeleteLocalRef(dnsList);
                    env->DeleteLocalRef(arrayListClass);
                    env->DeleteLocalRef(callbackClass);
                    
                    LOGI("✅ Notified Kotlin about tunnel DNS: tunnel=%s", tunnel_id.c_str());
                } else {
                    LOGW("Cannot find onTunnelDnsReceived method in callback");
                    env->DeleteLocalRef(dnsList);
                    env->DeleteLocalRef(arrayListClass);
                    env->DeleteLocalRef(callbackClass);
                }
            }
        }
    }

    // Implement CustomTunCallback::on_route_pushed
    virtual void on_route_pushed(const std::string& tunnel_id,
                                 const std::string& address,
                                 int prefix_len,
                                 bool ipv6) override {
        LOGI("✅ on_route_pushed callback: tunnel=%s, route=%s/%d (ipv6=%s)",
             tunnel_id.c_str(), address.c_str(), prefix_len, ipv6 ? "true" : "false");
        notifyRouteCallback(tunnel_id, address, prefix_len, ipv6);
    }
    
    // Get the app FD for packet I/O (call after connection established)
    int getAppFd() const {
        __android_log_print(ANDROID_LOG_INFO, "OpenVPN-Wrapper",
            "AndroidOpenVPNClient::getAppFd() - factoryCreated_=%d, customTunClientFactory_=%p",
            factoryCreated_, (void*)customTunClientFactory_);
        
        if (factoryCreated_ && customTunClientFactory_) {
            int fd = customTunClientFactory_->getAppFd();
            __android_log_print(ANDROID_LOG_INFO, "OpenVPN-Wrapper",
                "CustomTunClientFactory::getAppFd() returned: %d", fd);
            return fd;
        }
        __android_log_print(ANDROID_LOG_WARN, "OpenVPN-Wrapper",
            "AndroidOpenVPNClient::getAppFd() - factory not ready!");
        return -1;
    }
    
    // Clear the factory pointer (called by OpenVPN when it deletes the factory)
    void clearFactory() {
        customTunClientFactory_ = nullptr;
        factoryCreated_ = false;
    }
#endif
    
private:
    JavaVM* javaVM_;  // JavaVM for getting JNIEnv in any thread
    jobject vpnBuilder_;  // Android VpnService.Builder instance (global ref)
    jobject vpnService_;  // Android VpnService instance (global ref) - for calling protect()
    int tunFd_ = -1;  // TUN file descriptor from Android VpnService
    OpenVpnSession* session_;  // Pointer to session to update connection state (forward declared)
    
    // Callbacks (stored from session for use in tun_builder_add_address and tun_builder_set_dns_options)
    jobject ipAddressCallback_;  // Global reference to Kotlin IP callback object
    jobject dnsCallback_;  // Global reference to Kotlin DNS callback object
    jobject routeCallback_;  // Global reference to Kotlin route callback object
    JavaVM* sessionJavaVM_;  // JavaVM for callback (per session)
    std::string tunnelId_;  // Tunnel ID from session
    std::atomic<bool> destroying_;  // Flag to prevent callback access during destruction
    
#ifdef OPENVPN_EXTERNAL_TUN_FACTORY
    // Store the custom TUN client factory for app FD retrieval
    // NOTE: This is a NON-OWNING pointer - OpenVPN 3 owns the factory and will delete it
    openvpn::CustomTunClientFactory* customTunClientFactory_ = nullptr;
    bool factoryCreated_ = false;  // Track if we created the factory
#endif
    
    // Helper to set connected flag - implemented after OpenVpnSession definition
    void setConnectedFromEvent();

    void notifyRouteCallback(const std::string& tunnel_id,
                             const std::string& address,
                             int prefix_length,
                             bool ipv6) {
        if (destroying_ || !routeCallback_ || !sessionJavaVM_ || tunnel_id.empty()) {
            LOGW("⚠️  Route callback skipped (destroying=%d, callback=%p, javaVM=%p, tunnelId=%s)",
                 destroying_.load(), routeCallback_, sessionJavaVM_, tunnel_id.c_str());
            return;
        }

        JNIEnv* env = nullptr;
        jint result = sessionJavaVM_->GetEnv((void**)&env, JNI_VERSION_1_6);
        if (result == JNI_EDETACHED) {
            result = sessionJavaVM_->AttachCurrentThread(&env, nullptr);
            if (result != JNI_OK || env == nullptr) {
                LOGW("Cannot attach thread to JNI for route callback");
                return;
            }
        } else if (result != JNI_OK || env == nullptr) {
            LOGW("Cannot get JNIEnv for route callback");
            return;
        }

        jclass callbackClass = env->GetObjectClass(routeCallback_);
        if (!callbackClass) {
            LOGW("Cannot get callback class for route callback");
            return;
        }

        jmethodID methodId = env->GetMethodID(
            callbackClass,
            "onTunnelRouteReceived",
            "(Ljava/lang/String;Ljava/lang/String;IZ)V"
        );

        if (!methodId) {
            LOGW("Cannot find onTunnelRouteReceived method in callback");
            env->DeleteLocalRef(callbackClass);
            return;
        }

        jstring tunnelIdStr = env->NewStringUTF(tunnel_id.c_str());
        jstring addressStr = env->NewStringUTF(address.c_str());

        env->CallVoidMethod(
            routeCallback_,
            methodId,
            tunnelIdStr,
            addressStr,
            static_cast<jint>(prefix_length),
            ipv6 ? JNI_TRUE : JNI_FALSE
        );

        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }

        env->DeleteLocalRef(addressStr);
        env->DeleteLocalRef(tunnelIdStr);
        env->DeleteLocalRef(callbackClass);
    }
    
    // Implement LogReceiver::log
    virtual void log(const LogInfo &log_info) override {
        // Log everything with appropriate level
        const char* text = log_info.text.c_str();
        
        // Check for transport/data channel related logs
        if (strstr(text, "TCP/UDP") || strstr(text, "Data Channel") || 
            strstr(text, "BYTES") || strstr(text, "packet") ||
            strstr(text, "send") || strstr(text, "recv")) {
            __android_log_print(ANDROID_LOG_INFO, "OpenVPN-Transport", "📡 %s", text);
        }
        
        // Also log everything to main OpenVPN log
        LOGI("OpenVPN: %s", text);
    }
    
    // Implement event callback
    virtual void event(const Event &evt) override {
        // Log ALL events with detailed information
        if (evt.error) {
            LOGE("🔴 OpenVPN Event [%s]: %s %s", evt.name.c_str(), 
                 evt.fatal ? "(FATAL)" : "(non-fatal)", evt.info.c_str());
        } else {
            LOGI("🔵 OpenVPN Event [%s]: %s", evt.name.c_str(), evt.info.c_str());
        }
        
        // Log additional event details that might help debug transport
        __android_log_print(ANDROID_LOG_DEBUG, "OpenVPN-Events",
            "Event details: name=%s, error=%d, fatal=%d, info=%s",
            evt.name.c_str(), evt.error, evt.fatal, evt.info.c_str());
        
        // Handle specific events to track PUSH_REPLY flow
        if (evt.name == "CONNECTED") {
            LOGI("✅ OpenVPN connection established");
            // CRITICAL: Set connected flag immediately when CONNECTED event fires
            // Using atomic<bool> so we can set it from event handler without mutex
            // This allows isConnected() to return true as soon as connection is established
            setConnectedFromEvent();
        } else if (evt.name == "DISCONNECTED") {
            LOGI("OpenVPN disconnected: %s", evt.info.c_str());
        } else if (evt.name == "PUSH_REQUEST") {
            LOGI("📤 Client sent PUSH_REQUEST to server (requesting configuration)");
        } else if (evt.name == "PUSH_REPLY") {
            LOGI("📥 Server sent PUSH_REPLY (configuration received)");
            LOGI("   PUSH_REPLY info: %s", evt.info.c_str());
        } else if (evt.name == "AUTH_FAILED") {
            LOGE("❌ Authentication failed: %s", evt.info.c_str());
        } else if (evt.name == "AUTH_PENDING") {
            LOGI("⏳ Authentication pending: %s", evt.info.c_str());
        } else if (evt.name == "AUTH_OK") {
            LOGI("✅ Authentication successful");
        } else if (evt.name == "COMPRESS_ERROR") {
            LOGE("❌ Compression error: %s", evt.info.c_str());
            LOGE("Server pushed compression settings that OpenVPN 3 rejects");
            LOGE("This is a fatal error - connection will disconnect");
        } else if (evt.name == "DATA_CHANNEL_STARTED") {
            __android_log_print(ANDROID_LOG_INFO, "OpenVPN-Transport",
                "🚀 DATA_CHANNEL_STARTED - can now send/receive encrypted packets");
        } else if (evt.name == "TRANSPORT_ERROR") {
            __android_log_print(ANDROID_LOG_ERROR, "OpenVPN-Transport",
                "❌ TRANSPORT_ERROR: %s", evt.info.c_str());
        } else if (evt.name.find("TRANSPORT") != std::string::npos || 
                   evt.name.find("TX") != std::string::npos || 
                   evt.name.find("RX") != std::string::npos) {
            // Log any transport, TX (transmit), or RX (receive) related events
            __android_log_print(ANDROID_LOG_INFO, "OpenVPN-Transport",
                "📡 %s: %s", evt.name.c_str(), evt.info.c_str());
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
    
    // NOTE: client_auth() is not part of OpenVPNClient from ClientAPI
    // ClientAPI uses provide_creds() for authentication instead
    // The client_auth() callback is part of the lower-level ProtoContext interface
    // which ClientAPI wraps internally. We use provide_creds() which is the
    // ClientAPI way to provide credentials.
    
    // Store credentials (though ClientAPI uses provide_creds(), not client_auth)
    // These are set via provide_creds() but stored here for potential future use
    std::string stored_username_;
    std::string stored_password_;
    
    // Override TunBuilderBase methods to use Android VpnService.Builder
    // These methods are called by OpenVPN 3 during connection setup
    
    // CRITICAL: tun_builder_new() must be implemented and return true
    // This is called FIRST before any other tun_builder_* methods
    // It should return true if the TUN builder is ready to configure the interface
    virtual bool tun_builder_new() override {
        LOGI("═══════════════════════════════════════════════════════");
        LOGI("🔧 tun_builder_new() called by OpenVPN 3");
        LOGI("   This is called FIRST before any other tun_builder_* methods");
        LOGI("   TUN interface is already established by VpnEngineService");
        LOGI("   Returning true to indicate TUN builder is ready");
        LOGI("═══════════════════════════════════════════════════════");
        return true;
    }
    
    virtual bool tun_builder_add_address(const std::string &address,
                                         int prefix_length,
                                         const std::string &gateway,
                                         bool ipv6,
                                         bool net30) override {
        LOGI("tun_builder_add_address: %s/%d (ipv6=%s, gateway=%s)", 
             address.c_str(), prefix_length, ipv6 ? "true" : "false", gateway.c_str());
        
        // Notify Kotlin about the IP address via callback
        // Use stored callback info from session (set via setSession/updateSessionCallbackInfo)
        // Check destroying_ flag to prevent accessing deleted callbacks during cleanup
        if (!destroying_ && ipAddressCallback_ && sessionJavaVM_ && !tunnelId_.empty()) {
            JNIEnv* env = nullptr;
            jint result = sessionJavaVM_->GetEnv((void**)&env, JNI_VERSION_1_6);
            if (result == JNI_EDETACHED) {
                // Thread not attached - attach it
                result = sessionJavaVM_->AttachCurrentThread(&env, nullptr);
                if (result != JNI_OK || env == nullptr) {
                    LOGW("Cannot attach thread to JNI for IP callback");
                    return true;
                }
            } else if (result != JNI_OK || env == nullptr) {
                LOGW("Cannot get JNIEnv for IP callback");
                return true;
            }
            
            // Call callback method: onTunnelIpReceived(tunnelId: String, ip: String, prefixLength: Int)
            // The callback object should implement an interface with this method
            jclass callbackClass = env->GetObjectClass(ipAddressCallback_);
            if (callbackClass) {
                jmethodID methodId = env->GetMethodID(callbackClass, "onTunnelIpReceived", 
                    "(Ljava/lang/String;Ljava/lang/String;I)V");
                if (methodId) {
                    jstring tunnelIdStr = env->NewStringUTF(tunnelId_.c_str());
                    jstring ipStr = env->NewStringUTF(address.c_str());
                    
                    env->CallVoidMethod(ipAddressCallback_, methodId, 
                        tunnelIdStr, ipStr, prefix_length);
                    
                    // Check for exceptions
                    if (env->ExceptionCheck()) {
                        env->ExceptionDescribe();
                        env->ExceptionClear();
                    }
                    
                    env->DeleteLocalRef(tunnelIdStr);
                    env->DeleteLocalRef(ipStr);
                    env->DeleteLocalRef(callbackClass);
                    
                    LOGI("✅ Notified Kotlin about tunnel IP: tunnelId=%s, ip=%s/%d", 
                         tunnelId_.c_str(), address.c_str(), prefix_length);
                } else {
                    LOGW("Cannot find onTunnelIpReceived method in callback");
                    env->DeleteLocalRef(callbackClass);
                }
            } else {
                LOGW("Cannot get callback class");
            }
        } else {
            LOGW("⚠️  IP address callback not set - cannot notify Kotlin (callback=%p, javaVM=%p, tunnelId=%s)", 
                 ipAddressCallback_, sessionJavaVM_, tunnelId_.c_str());
        }
        
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
        notifyRouteCallback(tunnelId_, address, prefix_length, ipv6);

        // Routes are already configured by VpnEngineService
        // Additional routes from OpenVPN config can be logged but don't need action
        return true;
    }
    
    virtual bool tun_builder_set_dns_options(const openvpn::DnsOptions &dns) override {
        LOGI("tun_builder_set_dns_options called");
        
        // Extract DNS servers from OpenVPN's DHCP options
        // DnsOptions.servers is a std::map<int, DnsServer> where key is priority
        // Each DnsServer has a std::vector<DnsAddress> addresses
        // Each DnsAddress has a std::string address field
        if (!dns.servers.empty()) {
            std::vector<std::string> dnsAddresses;
            for (const auto &[priority, server] : dns.servers) {
                for (const auto &dnsAddr : server.addresses) {
                    dnsAddresses.push_back(dnsAddr.address);
                    LOGI("DHCP DNS server (priority %d): %s", priority, dnsAddr.address.c_str());
                }
            }
            
            // Notify Kotlin about DNS servers via callback
            // Check destroying_ flag to prevent accessing deleted callbacks during cleanup
            if (!destroying_ && dnsCallback_ && sessionJavaVM_ && !tunnelId_.empty() && !dnsAddresses.empty()) {
                JNIEnv* env = nullptr;
                jint result = sessionJavaVM_->GetEnv((void**)&env, JNI_VERSION_1_6);
                if (result == JNI_EDETACHED) {
                    // Thread not attached - attach it
                    result = sessionJavaVM_->AttachCurrentThread(&env, nullptr);
                    if (result != JNI_OK || env == nullptr) {
                        LOGW("Cannot attach thread to JNI for DNS callback");
                        return true;
                    }
                } else if (result != JNI_OK || env == nullptr) {
                    LOGW("Cannot get JNIEnv for DNS callback");
                    return true;
                }
                
                // Call callback method: onTunnelDnsReceived(tunnelId: String, dnsServers: List<String>)
                jclass callbackClass = env->GetObjectClass(dnsCallback_);
                if (callbackClass) {
                    // Create ArrayList for DNS servers
                    jclass arrayListClass = env->FindClass("java/util/ArrayList");
                    jmethodID arrayListInit = env->GetMethodID(arrayListClass, "<init>", "(I)V");
                    jmethodID arrayListAdd = env->GetMethodID(arrayListClass, "add", "(Ljava/lang/Object;)Z");
                    
                    jobject dnsList = env->NewObject(arrayListClass, arrayListInit, (jint)dnsAddresses.size());
                    
                    // Add each DNS server to the list
                    for (const auto &dnsAddr : dnsAddresses) {
                        jstring dnsStr = env->NewStringUTF(dnsAddr.c_str());
                        env->CallBooleanMethod(dnsList, arrayListAdd, dnsStr);
                        env->DeleteLocalRef(dnsStr);
                    }
                    
                    // Call the callback method
                    jmethodID methodId = env->GetMethodID(callbackClass, "onTunnelDnsReceived", 
                        "(Ljava/lang/String;Ljava/util/List;)V");
                    if (methodId) {
                        jstring tunnelIdStr = env->NewStringUTF(tunnelId_.c_str());
                        
                        env->CallVoidMethod(dnsCallback_, methodId, tunnelIdStr, dnsList);
                        
                        // Check for exceptions
                        if (env->ExceptionCheck()) {
                            env->ExceptionDescribe();
                            env->ExceptionClear();
                        }
                        
                        env->DeleteLocalRef(tunnelIdStr);
                        env->DeleteLocalRef(dnsList);
                        env->DeleteLocalRef(callbackClass);
                        env->DeleteLocalRef(arrayListClass);
                        
                        LOGI("✅ Notified Kotlin about DNS servers: tunnelId=%s, count=%zu", 
                             tunnelId_.c_str(), dnsAddresses.size());
                    } else {
                        LOGW("Cannot find onTunnelDnsReceived method in callback");
                        env->DeleteLocalRef(dnsList);
                        env->DeleteLocalRef(callbackClass);
                    }
                } else {
                    LOGW("Cannot get callback class");
                }
            } else {
                LOGW("⚠️  DNS callback not set - cannot notify Kotlin (callback=%p, javaVM=%p, tunnelId=%s)", 
                     dnsCallback_, sessionJavaVM_, tunnelId_.c_str());
            }
            
            if (!dnsAddresses.empty()) {
                LOGI("✅ DHCP DNS servers received: %zu server(s)", dnsAddresses.size());
            }
        } else {
            LOGW("⚠️  No DNS servers in DHCP options from OpenVPN");
        }
        
        // Note: DNS search domains are also available in dns.search_domains
        if (!dns.search_domains.empty()) {
            LOGI("DNS search domains received: %zu domain(s)", dns.search_domains.size());
            for (const auto &domain : dns.search_domains) {
                LOGI("  Search domain: %s", domain.domain.c_str());
            }
        }
        
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
    
    virtual bool tun_builder_set_remote_address(const std::string &address, bool ipv6) override {
        LOGI("tun_builder_set_remote_address: %s (ipv6=%s)", address.c_str(), ipv6 ? "true" : "false");
        // Remote address is already configured by VpnEngineService via routes
        // This is typically the VPN server's IP address
        return true;
    }
    
    virtual bool tun_builder_persist() override {
        LOGI("tun_builder_persist() called - returning false (no TUN persistence)");
        // We set tunPersist = false in Config, so return false here
        // This prevents OpenVPN 3 from trying to reuse existing TUN interfaces
        return false;
    }
    
    // Helper function to protect a socket file descriptor
    bool protectSocket(int socket_fd) {
        // We need to call VpnService.protect(socket_fd) from Java
        // Since we have JavaVM, we can get JNIEnv and call the method
        if (javaVM_ == nullptr) {
            LOGW("JavaVM is null, cannot protect socket");
            return false;
        }
        
        JNIEnv* env = nullptr;
        jint result = javaVM_->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
        if (result == JNI_EDETACHED) {
            // Thread not attached - attach it
            result = javaVM_->AttachCurrentThread(&env, nullptr);
            if (result != JNI_OK || env == nullptr) {
                LOGW("Cannot attach thread to JNI to protect socket");
                return false;
            }
        } else if (result != JNI_OK || env == nullptr) {
            LOGW("Cannot get JNIEnv to protect socket");
            return false;
        }
        
        // Get VpnService class
        jclass vpnServiceClass = env->FindClass("android/net/VpnService");
        if (vpnServiceClass == nullptr) {
            LOGW("Cannot find VpnService class");
            return false;
        }
        
        // CRITICAL: VpnService.protect() is an INSTANCE method, not static!
        // We need a VpnService instance to call it on
        if (vpnService_ == nullptr) {
            LOGW("VpnService instance is null, cannot protect socket");
            env->DeleteLocalRef(vpnServiceClass);
            return false;
        }
        
        // Get protect() method: public boolean protect(int socket) - instance method
        jmethodID protectMethod = env->GetMethodID(
            vpnServiceClass,
            "protect",
            "(I)Z"
        );
        
        if (protectMethod == nullptr) {
            // Try to get error message
            jthrowable exc = env->ExceptionOccurred();
            if (exc) {
                env->ExceptionClear();
                LOGW("Cannot find VpnService.protect() instance method");
            } else {
                LOGW("Cannot find VpnService.protect() instance method - no exception");
            }
            if (exc) env->DeleteLocalRef(exc);
            env->DeleteLocalRef(vpnServiceClass);
            return false;
        }
        
        // Call vpnService_.protect(socket_fd) - instance method call
        jboolean protectResult = env->CallBooleanMethod(vpnService_, protectMethod, socket_fd);
        
        // Check for exceptions
        if (env->ExceptionCheck()) {
            jthrowable exc = env->ExceptionOccurred();
            env->ExceptionClear();
            LOGW("Exception calling VpnService.protect(): %p", exc);
            env->DeleteLocalRef(exc);
            env->DeleteLocalRef(vpnServiceClass);
            return false;
        }
        env->DeleteLocalRef(vpnServiceClass);
        
        if (protectResult) {
            LOGI("✅ Successfully protected socket FD %d from VPN interface", socket_fd);
            return true;
        } else {
            LOGW("Failed to protect socket FD %d", socket_fd);
            return false;
        }
    }
    
    // CRITICAL: Implement socket_protect() from OpenVPNClient interface
    // This is called by OpenVPN 3 for socket protection to prevent routing loops
    virtual bool socket_protect(openvpn_io::detail::socket_type socket, std::string remote, bool ipv6) override {
        LOGI("socket_protect() called for socket: remote=%s, ipv6=%s", remote.c_str(), ipv6 ? "true" : "false");
        
        // On Android/Linux, socket_type is typically an int (file descriptor)
        // Extract the FD from the socket
        int socket_fd = static_cast<int>(socket);
        LOGI("socket_protect() converting socket to FD: %d", socket_fd);
        
        // Protect the socket from being routed through VPN interface
        return protectSocket(socket_fd);
    }
    
    virtual int tun_builder_establish() override {
        LOGI("═══════════════════════════════════════════════════════");
        LOGI("🔧 tun_builder_establish() called by OpenVPN 3");
        LOGI("   This is called AFTER tun_builder_add_address()");
        LOGI("   OpenVPN 3 needs a valid TUN FD to start TLS handshake");
        LOGI("   Current TUN FD: %d", tunFd_);
        LOGI("═══════════════════════════════════════════════════════");
        
        // For Android, the TUN interface is already established by VpnEngineService
        // We return the file descriptor that was set via setTunFileDescriptor()
        if (tunFd_ < 0) {
            LOGE("❌ CRITICAL: TUN file descriptor not set!");
            LOGE("   OpenVPN 3 cannot establish connection without a valid TUN FD");
            LOGE("   This means setTunFileDescriptor() was not called before connect()");
            LOGE("   The FD should be passed via nativeConnect() and setTunFileDescriptor()");
            LOGE("   TLS handshake will NOT start without valid TUN FD");
            return -1;
        }
        
        LOGI("✅ Returning TUN file descriptor: %d", tunFd_);
        LOGI("   OpenVPN 3 will use this FD for packet I/O");
        LOGI("   TLS handshake should start after this returns");
        LOGI("   If no handshake packets are sent, check TLS initialization");
        return tunFd_;
    }
};
#endif  // OPENVPN3_AVAILABLE

// OpenVPN session structure
struct OpenVpnSession {
    std::atomic<bool> connected;  // Use atomic so event handler can set it without mutex
    bool connecting;  // True when connect() is running but not yet complete
    std::string last_error;
    std::string tunnelId;  // Tunnel ID for identifying which tunnel this session belongs to
    
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
    
    // JNI callbacks for tunnel IP address and DNS servers (called when tun_builder_add_address() and tun_builder_set_dns_options() are invoked)
    // These are global references to Kotlin objects that implement the callback interfaces
    jobject ipAddressCallback;  // Global reference to Kotlin IP callback object
    jobject dnsCallback;  // Global reference to Kotlin DNS callback object
    jobject routeCallback;  // Global reference to Kotlin route callback object
    JavaVM* javaVM;  // For calling callback from any thread
    
    // Note: No separate tunFactory needed - AndroidOpenVPNClient implements ExternalTun::Factory
    
    OpenVpnSession() : connected(false), connecting(false), androidClient(nullptr), client(nullptr), should_stop(false), ipAddressCallback(nullptr), dnsCallback(nullptr), routeCallback(nullptr), javaVM(nullptr) {
        // atomic<bool> is initialized with false above
        // Initialize Android-specific OpenVPN 3 Client - This implements all required virtual methods
        androidClient = new AndroidOpenVPNClient();
        client = androidClient;  // Store both pointers for convenience
        if (!client || !androidClient) {
            throw std::runtime_error("Failed to create Android OpenVPN 3 client");
        }
        
        #ifdef OPENVPN_EXTERNAL_TUN_FACTORY
        // AndroidOpenVPNClient implements ExternalTun::Factory
        // tunnelId will be set via openvpn_wrapper_set_tunnel_id_and_callback()
        LOGI("AndroidOpenVPNClient created (implements ExternalTun::Factory)");
        #endif
    }
    
    ~OpenVpnSession() {
        should_stop = true;
        
        // Stop connection if running
        if (androidClient && (connected || connecting)) {
            try {
                androidClient->stop();
            } catch (...) {
                // Ignore errors during cleanup
            }
        }
        
        // Wait for connection thread to finish - this ensures no more events will fire
        if (connection_thread.joinable()) {
            connection_thread.join();
        }
        
        // Delete client FIRST - this sets destroying_ flag preventing callback access
        // and ensures OpenVPN 3 stops processing events
        if (androidClient) {
            delete androidClient;
            androidClient = nullptr;
            client = nullptr;
        }
        
        // Brief delay to ensure any in-flight JNI calls complete
        std::this_thread::sleep_for(std::chrono::milliseconds(100));
        
        // Now safe to clean up JNI callback references
        if (ipAddressCallback && javaVM) {
            JNIEnv* env = nullptr;
            if (javaVM->GetEnv((void**)&env, JNI_VERSION_1_6) == JNI_OK) {
                env->DeleteGlobalRef(ipAddressCallback);
            } else if (javaVM->AttachCurrentThread(&env, nullptr) == JNI_OK) {
                env->DeleteGlobalRef(ipAddressCallback);
                javaVM->DetachCurrentThread();
            }
            ipAddressCallback = nullptr;
        }
        
        if (dnsCallback && javaVM) {
            JNIEnv* env = nullptr;
            if (javaVM->GetEnv((void**)&env, JNI_VERSION_1_6) == JNI_OK) {
                env->DeleteGlobalRef(dnsCallback);
            } else if (javaVM->AttachCurrentThread(&env, nullptr) == JNI_OK) {
                env->DeleteGlobalRef(dnsCallback);
                javaVM->DetachCurrentThread();
            }
            dnsCallback = nullptr;
        }
        
        if (routeCallback && javaVM) {
            JNIEnv* env = nullptr;
            if (javaVM->GetEnv((void**)&env, JNI_VERSION_1_6) == JNI_OK) {
                env->DeleteGlobalRef(routeCallback);
            } else if (javaVM->AttachCurrentThread(&env, nullptr) == JNI_OK) {
                env->DeleteGlobalRef(routeCallback);
                javaVM->DetachCurrentThread();
            }
            routeCallback = nullptr;
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

// Implement AndroidOpenVPNClient::setConnectedFromEvent() after OpenVpnSession definition
#ifdef OPENVPN3_AVAILABLE
void AndroidOpenVPNClient::setConnectedFromEvent() {
    // CRITICAL: Set connected flag immediately when CONNECTED event fires
    // Using atomic<bool> so we can set it from event handler without mutex
    // This allows isConnected() to return true as soon as connection is established
    if (session_) {
        session_->connected = true;
        // Need mutex for connecting since it's not atomic
        std::lock_guard<std::mutex> lock(session_->state_mutex);
        session_->connecting = false;
        LOGI("Updated session->connected = true (connection fully established via event)");
    }
}

// Implement AndroidOpenVPNClient::setSession() after OpenVpnSession definition
void AndroidOpenVPNClient::setSession(OpenVpnSession* session) {
    session_ = session;
    // Also store callbacks and JavaVM from session for use in tun_builder_add_address and tun_builder_set_dns_options
    if (session) {
        ipAddressCallback_ = session->ipAddressCallback;
        dnsCallback_ = session->dnsCallback;
        routeCallback_ = session->routeCallback;
        sessionJavaVM_ = session->javaVM;
        tunnelId_ = session->tunnelId;
        LOGI("Set session pointer and updated callback info: tunnelId=%s", tunnelId_.c_str());
    }
}

// Implement AndroidOpenVPNClient::updateSessionCallbackInfo() after OpenVpnSession definition
void AndroidOpenVPNClient::updateSessionCallbackInfo(OpenVpnSession* session) {
    if (session) {
        ipAddressCallback_ = session->ipAddressCallback;
        dnsCallback_ = session->dnsCallback;
        routeCallback_ = session->routeCallback;
        sessionJavaVM_ = session->javaVM;
        tunnelId_ = session->tunnelId;
        LOGI("Updated callback info: tunnelId=%s, ipCallback=%p, dnsCallback=%p, routeCallback=%p, javaVM=%p",
             tunnelId_.c_str(), ipAddressCallback_, dnsCallback_, routeCallback_, sessionJavaVM_);
    }
}
#endif

extern "C" {

// Helper function to set tunnel ID and callback
void openvpn_wrapper_set_tunnel_id_and_callback(OpenVpnSession* session,
                                                 JNIEnv* env,
                                                 const char* tunnelId,
                                                 jobject ipCallback,
                                                 jobject dnsCallback,
                                                 jobject routeCallback) {
#ifdef OPENVPN3_AVAILABLE
    if (!session) {
        LOGE("Invalid session for set_tunnel_id_and_callback");
        return;
    }
    
    // Set tunnel ID
    if (tunnelId) {
        session->tunnelId = std::string(tunnelId);
        LOGI("Tunnel ID set: %s", tunnelId);
        
        #ifdef OPENVPN_EXTERNAL_TUN_FACTORY
        // Set tunnel ID on AndroidOpenVPNClient (which implements ExternalTun::Factory)
        if (session->androidClient) {
            session->androidClient->setTunnelId(tunnelId);
            LOGI("✅ Set tunnel ID on AndroidOpenVPNClient: %s", tunnelId);
            LOGI("   AndroidOpenVPNClient implements ExternalTun::Factory");
            LOGI("   OpenVPN 3 will call client->new_tun_factory()");
        }
        #endif
    }
    
    // Store JavaVM for callback
    if (!session->javaVM) {
        env->GetJavaVM(&session->javaVM);
    }
    
    // Set IP callback (global reference so it persists)
    if (ipCallback) {
        if (session->ipAddressCallback) {
            // Delete old callback if exists
            JNIEnv* currentEnv = nullptr;
            if (session->javaVM->GetEnv((void**)&currentEnv, JNI_VERSION_1_6) == JNI_OK) {
                currentEnv->DeleteGlobalRef(session->ipAddressCallback);
            }
        }
        session->ipAddressCallback = env->NewGlobalRef(ipCallback);
        LOGI("IP address callback set for tunnel: %s", tunnelId ? tunnelId : "unknown");
    } else {
        LOGW("IP address callback is null");
    }
    
    // Set DNS callback (global reference so it persists)
    if (dnsCallback) {
        if (session->dnsCallback) {
            // Delete old callback if exists
            JNIEnv* currentEnv = nullptr;
            if (session->javaVM->GetEnv((void**)&currentEnv, JNI_VERSION_1_6) == JNI_OK) {
                currentEnv->DeleteGlobalRef(session->dnsCallback);
            }
        }
        session->dnsCallback = env->NewGlobalRef(dnsCallback);
        LOGI("DNS callback set for tunnel: %s", tunnelId ? tunnelId : "unknown");
    } else {
        LOGW("DNS callback is null");
    }
    
    // Set route callback (global reference so it persists)
    if (routeCallback) {
        if (session->routeCallback) {
            // Delete old callback if exists
            JNIEnv* currentEnv = nullptr;
            if (session->javaVM->GetEnv((void**)&currentEnv, JNI_VERSION_1_6) == JNI_OK) {
                currentEnv->DeleteGlobalRef(session->routeCallback);
            }
        }
        session->routeCallback = env->NewGlobalRef(routeCallback);
        LOGI("Route callback set for tunnel: %s", tunnelId ? tunnelId : "unknown");
    } else {
        LOGW("Route callback is null");
    }
    
    // Update AndroidOpenVPNClient with callback info so tun_builder_add_address and tun_builder_set_dns_options can use it
    if (session->androidClient) {
        session->androidClient->updateSessionCallbackInfo(session);
        LOGI("Updated AndroidOpenVPNClient with callback info");
    }
#else
    LOGE("OpenVPN 3 not available");
#endif
}

// Helper function to set Android-specific parameters
void openvpn_wrapper_set_android_params(OpenVpnSession* session,
                                        JNIEnv* env,
                                        jobject vpnBuilder,
                                        jint tunFd,
                                        jobject vpnService) {
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
    
    // Also store in session for callback
    session->javaVM = javaVM;
    
    if (vpnBuilder) {
        session->androidClient->setVpnServiceBuilder(env, vpnBuilder);
        LOGI("VpnService.Builder set in AndroidOpenVPNClient");
    }
    
    // CRITICAL: Set VpnService instance for socket protection
    if (vpnService) {
        session->androidClient->setVpnService(env, vpnService);
        LOGI("VpnService instance set in AndroidOpenVPNClient (for protect())");
    } else {
        LOGW("VpnService instance is null - socket protection will fail!");
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
        // NOTE: comp-lzo is supported by OpenVPN 3, but we need to handle it properly
        std::vector<std::string> unsupported_options = {
            "ping-timer-rem",
            "remote-random",
            "fast-io"
            // "comp-lzo" - REMOVED: OpenVPN 3 supports LZO compression
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
        
        // CRITICAL: Keep 'auth-user-pass' in config (without file path) to prevent autologin detection
        // OpenVPN 3 detects autologin when 'auth-user-pass' is missing, which causes:
        //   - eval.autologin = true
        //   - xmit_creds = false
        //   - client_auth() never called
        //   - Credentials never sent
        //   - Connection times out
        // 
        // By keeping 'auth-user-pass' (without a file path), OpenVPN 3 knows credentials are needed,
        // but will use provide_creds() for the actual credentials instead of reading from a file.
        if (config_content.find("auth-user-pass") == std::string::npos) {
            // Add auth-user-pass without file path - this tells OpenVPN 3 credentials are needed
            // but we'll provide them via provide_creds() instead of a file
            config_content += "\nauth-user-pass\n";
            LOGI("Added 'auth-user-pass' directive (without file path) to prevent autologin detection");
        } else {
            // Replace any auth-user-pass with file path to just 'auth-user-pass' (no file)
            size_t pos = 0;
            while ((pos = config_content.find("auth-user-pass", pos)) != std::string::npos) {
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
                // Replace with just 'auth-user-pass' (no file path)
                config_content.replace(line_start, line_end - line_start, "auth-user-pass\n");
                LOGI("Replaced 'auth-user-pass' with file path to 'auth-user-pass' (using provide_creds() instead)");
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
        
        // Increase verbosity for debugging (verb 5 = very verbose)
        // Check if verb is already set, and if so, replace it with verb 5
        // Otherwise, add verb 5
        std::string verb_pattern = "verb ";
        size_t verb_pos = config_content.find(verb_pattern);
        if (verb_pos != std::string::npos) {
            // Find the line with verb and replace it
            size_t line_start = config_content.rfind('\n', verb_pos);
            if (line_start == std::string::npos) {
                line_start = 0;
            } else {
                line_start++; // Skip the newline
            }
            size_t line_end = config_content.find('\n', verb_pos);
            if (line_end == std::string::npos) {
                line_end = config_content.length();
            } else {
                line_end++; // Include the newline
            }
            // Replace the verb line with verb 5
            config_content.replace(line_start, line_end - line_start, "verb 5\n");
            LOGI("Updated verbosity to verb 5 (very verbose)");
        } else {
            // Add verb 5 if not present
            config_content += "\nverb 5\n";
            LOGI("Added 'verb 5' directive to config (very verbose logging)");
        }
        
        LOGI("OpenVPN config processed (%zu bytes, removed unsupported options)", config_content.length());
        
        session->config.content = config_content;
        session->config.connTimeout = 30;  // Connection timeout in seconds
        session->config.tunPersist = false; // Don't persist TUN interface
        
        // CRITICAL: Set compression mode to handle server-pushed compression
        // When compiled without LZO, OpenVPN 3 rejects LZO_STUB when compression_mode='no'
        // Setting compressionMode to "asym" allows asymmetric compression (downlink only)
        // which accepts LZO_STUB from server without actually compressing our uplink
        // This prevents COMPRESS_ERROR while maintaining security (no compression on our side)
        session->config.compressionMode = "asym";
        LOGI("Set compressionMode = 'asym' to accept server-pushed LZO_STUB without compressing uplink");
        
        #ifdef OPENVPN_EXTERNAL_TUN_FACTORY
        // External TUN Factory setup
        if (!session->androidClient) {
            LOGE("AndroidOpenVPNClient not initialized!");
            session->last_error = "AndroidOpenVPNClient not initialized";
            return OPENVPN_ERROR_INTERNAL;
        }
        LOGI("External TUN Factory ready for tunnel: %s", session->tunnelId.c_str());
        #endif
        
        // CRITICAL: Set autologinSessions = false to prevent creds_locked from being set
        // When autologin_sessions is true, ClientOptions constructor sets creds_locked = true,
        // which prevents credentials from being updated via submit_creds() later.
        // Since we're using provide_creds() to provide credentials, we need autologinSessions = false
        // so that credentials can be updated via submit_creds() in connect_setup().
        // autologinSessions is in ConfigCommon (which Config inherits from)
        session->config.autologinSessions = false;
        LOGI("Set autologinSessions = false to allow credential updates via provide_creds()");
        LOGI("Final processed OpenVPN config:\n%s", config_content.c_str());
        
        LOGI("Evaluating OpenVPN 3 config using ClientAPI...");
        LOGI("Config content length: %zu bytes", session->config.content.length());
        
        // Log first 500 chars of config to verify CA cert is present
        if (session->config.content.length() > 0) {
            std::string preview = session->config.content.substr(0, 500);
            LOGI("Config preview (first 500 chars): %s", preview.c_str());
        }
        
        // 2. Evaluate the config using OpenVPN 3 service
        EvalConfig eval = session->client->eval_config(session->config);
        if (eval.error) {
            session->last_error = eval.message;
            LOGE("OpenVPN config evaluation failed: %s", eval.message.c_str());
            return OPENVPN_ERROR_CONFIG_FAILED;
        }
        
        LOGI("Config evaluated successfully. Profile: %s", eval.profileName.c_str());
        LOGI("EvalConfig: autologin=%s, externalPki=%s, userlockedUsername=%s", 
             eval.autologin ? "true" : "false",
             eval.externalPki ? "true" : "false",
             eval.userlockedUsername.c_str());
        
        // CRITICAL: If eval.autologin is true, xmit_creds will be false
        // even if we set autologinSessions = false. This would prevent
        // client_auth() from being called.
        if (eval.autologin) {
            LOGE("WARNING: eval_config() returned autologin=true!");
            LOGE("This means xmit_creds will be false, preventing client_auth() from being called");
            LOGE("NordVPN configs should not be autologin - they require username/password");
        }
        
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
        // CRITICAL: Store credentials in session->creds AND call provide_creds()
        // OpenVPN 3 should use these credentials during connect()
        // 
        // IMPORTANT: provide_creds() must be called AFTER eval_config() but BEFORE connect()
        // - eval_config() doesn't create ClientOptions (that happens in connect_setup())
        // - connect_setup() creates ClientOptions, which calls submit_creds(empty_creds) in constructor
        // - Then connect_setup() calls submit_creds(state->creds) to update with our credentials
        // - If creds_locked is true, submit_creds() won't update credentials (but this only happens for autologin/embedded)
        LOGI("═══════════════════════════════════════════════════════");
        LOGI("Calling provide_creds() on client instance:");
        LOGI("Client pointer: %p", (void*)session->client);
        LOGI("Username: %zu bytes", session->creds.username.length());
        LOGI("Password: %zu bytes", session->creds.password.length());
        LOGI("═══════════════════════════════════════════════════════");
        
        Status credsStatus = session->client->provide_creds(session->creds);
        if (credsStatus.error) {
            session->last_error = credsStatus.message;
            LOGE("OpenVPN 3 provide_creds() failed: %s", credsStatus.message.c_str());
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
        
        LOGI("✅ provide_creds() succeeded - credentials should be stored in client");
        LOGI("   Client instance: %p", (void*)session->client);
        LOGI("   Same client will be used for connect()");
        
        // CRITICAL: Verify that state->creds was set correctly
        // provide_creds() creates a new ClientCreds and sets state->creds = cc
        // If provide_creds() returned success, state->creds should be set.
        // When connect() is called, connect_setup() will:
        //   1. Create ClientOptions (which creates empty creds in constructor)
        //   2. Call submit_creds(state->creds) to update with our credentials
        //   3. If state->creds is NULL, submit_creds() returns early (line 1185-1186)
        //   4. This would cause 'Creds: UsernameEmpty' in client_auth()
        LOGI("   Credentials stored in OpenVPNClient's internal state->creds");
        LOGI("   When connect() is called, connect_setup() will call submit_creds(state->creds)");
        LOGI("   If state->creds is NULL here, submit_creds() will return early!");
        LOGI("   This would cause Session to have empty creds -> 'Creds: UsernameEmpty'");
        
        // Try to verify credentials using session_token() if available
        // This can help us confirm that state->creds is actually set
        try {
            SessionToken token;
            // session_token() only returns true if session_id is defined, but we can try
            // This is just a verification attempt - it may not work for username/password auth
            LOGI("Attempting to verify credentials via session_token()...");
            // Note: session_token() only works if session_id is set, which won't be the case
            // for initial username/password auth. This is just for debugging.
        } catch (...) {
            // Ignore - session_token may not be available or may throw
        }
        
        // CRITICAL: Set session pointer in AndroidOpenVPNClient so event() can update connection state
        // This must be done BEFORE connect() so CONNECTED events can set connected=true
        if (session->androidClient) {
            session->androidClient->setSession(session);
            LOGI("Set session pointer in AndroidOpenVPNClient for event callbacks");
        }
        
        // CRITICAL: Also store credentials in AndroidOpenVPNClient for client_auth() callback
        // OpenVPN 3 may call client_auth() during connect() instead of using provide_creds()
        if (session->androidClient) {
            session->androidClient->setStoredCredentials(session->creds.username, session->creds.password);
            LOGI("Stored credentials in AndroidOpenVPNClient for client_auth() callback");
        }
        
        // CRITICAL: Add explicit verification that credentials are still in our session struct
        // This helps verify they weren't corrupted before being passed to provide_creds()
        LOGI("═══════════════════════════════════════════════════════");
        LOGI("Post-provide_creds() verification:");
        LOGI("  session->creds.username length: %zu bytes", session->creds.username.length());
        LOGI("  session->creds.password length: %zu bytes", session->creds.password.length());
        LOGI("  Client pointer still valid: %p", (void*)session->client);
        LOGI("═══════════════════════════════════════════════════════");
        
        // 5. Start connection using OpenVPN 3 service (connect() is blocking)
        // The connect() method blocks and manages the connection loop via OpenVPN 3
        session->should_stop = false;
        
        LOGI("Starting OpenVPN 3 service connection in background thread...");
        
        // Mark as connecting BEFORE starting the thread
        {
            std::lock_guard<std::mutex> lock(session->state_mutex);
            session->connecting = true;
            session->connected = false;
        }
        
        session->connection_thread = std::thread([session]() {
            try {
                // CRITICAL: Verify credentials are still valid before connect()
                // Log credential status to verify they weren't cleared
                LOGI("═══════════════════════════════════════════════════════");
                LOGI("About to call connect() - verifying credentials:");
                LOGI("Username length: %zu bytes", session->creds.username.length());
                LOGI("Password length: %zu bytes", session->creds.password.length());
                if (!session->creds.username.empty()) {
                    LOGI("Username first byte: 0x%02x", (unsigned char)session->creds.username[0]);
                }
                LOGI("Client instance: %p", (void*)session->client);
                LOGI("═══════════════════════════════════════════════════════");
                
                // CRITICAL: The connect() call will internally:
                // 1. Call connect_setup() which creates ClientOptions
                // 2. ClientOptions constructor creates empty creds
                // 3. connect_setup() calls submit_creds(state->creds)
                // 4. If state->creds is NULL, submit_creds() returns early
                // 5. Session ends up with empty creds -> "Creds: UsernameEmpty"
                //
                // We can't verify state->creds directly, but we can log that
                // provide_creds() succeeded, which means state->creds should be set.
                LOGI("Calling connect() - this will call connect_setup() which");
                LOGI("should call submit_creds(state->creds) with credentials from provide_creds()");
                
                // Log immediately before connect() to see if we reach this point
                LOGI("═══════════════════════════════════════════════════════");
                LOGI("About to call session->client->connect()");
                LOGI("Client pointer: %p", (void*)session->client);
                LOGI("Config content length: %zu bytes", session->config.content.length());
                LOGI("Config preview (first 200 chars): %s", session->config.content.substr(0, 200).c_str());
                LOGI("═══════════════════════════════════════════════════════");
                
                // This calls OpenVPN 3 service connect() - blocks until disconnect
                // This can take a long time (60+ seconds), so we mark as connecting first
                // The CONNECTED event will fire during connect(), and we'll set connected=true then
                LOGI("Calling connect() NOW - this will block until connection is established or fails");
                LOGI("   This method runs OpenVPN's event loop for processing IO");
                LOGI("   It will return when: connection fails, stop() is called, or reconnect triggered");
                Status connectStatus = session->client->connect();
                LOGI("═══════════════════════════════════════════════════════");
                LOGI("🔴 connect() RETURNED!");
                LOGI("   Error: %s", connectStatus.error ? "true" : "false");
                LOGI("   Message: %s", connectStatus.message.empty() ? "(empty)" : connectStatus.message.c_str());
                LOGI("   This means OpenVPN's event loop exited");
                LOGI("   Possible reasons: timeout, no server response, reconnect triggered");
                LOGI("═══════════════════════════════════════════════════════");
                
                std::lock_guard<std::mutex> lock(session->state_mutex);
                session->connecting = false;  // No longer connecting
                
                // If connect() returned successfully, connection is established
                // (CONNECTED event should have already fired and set connected=true via event callback)
                // But if it didn't, set it here as fallback
                if (!connectStatus.error && !session->connected) {
                    LOGI("connect() returned success but connected flag not set - setting now");
                    session->connected = true;
                }
                
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
                session->connecting = false;
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
        
        // CRITICAL: OpenVPN 3 ClientAPI reads packets from TUN FD directly
        // This means we CANNOT write packets to TUN - it will conflict with OpenVPN 3's reads.
        // 
        // For multi-tunnel routing, we need to:
        // 1. Read packets from TUN ourselves (VpnEngineService.readPacketsFromTun)
        // 2. Route them based on app rules (PacketRouter)
        // 3. Inject packets directly into OpenVPN 3's protocol/transport layer
        //
        // However, OpenVPN 3 ClientAPI doesn't expose a packet injection API.
        // The ClientAPI is designed for single-tunnel VPNs where OpenVPN 3 manages TUN I/O.
        //
        // SOLUTION: We need to prevent OpenVPN 3 from reading from TUN and handle I/O ourselves.
        // But OpenVPN 3 ClientAPI requires a valid TUN FD for tun_builder_establish().
        //
        // ALTERNATIVE: Don't write to TUN at all. Instead, queue packets and let
        // VpnEngineService read from TUN, then route them appropriately.
        // But then how do we get packets INTO OpenVPN 3?
        //
        // CRITICAL: With FIFO-based routing, we don't need to queue packets here.
        // Packets are written to FIFO from Kotlin code (VpnConnectionManager.sendPacketToTunnel).
        // This function is called from the old sendPacket() path, which we're replacing.
        // 
        // For FIFO-based routing, we should never reach here because sendPacketToTunnel()
        // writes directly to FIFO. But if we do, just return success (packet will be
        // written to FIFO from Kotlin side).
        
        LOGI("send_packet() called for tunnel - packet should be written to FIFO from Kotlin");
        return 0; // Success (packet will be written to FIFO)
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
        
        // If connection is established, return true
        // Use load() for atomic<bool> to get thread-safe read
        if (session->connected.load()) {
            return 1;
        }
        
        // CRITICAL: For connection state checking in VpnConnectionManager,
        // we should NOT return true for connecting=true. We only want to return
        // true when fully connected. This allows VpnConnectionManager to correctly
        // detect connecting state and pause TUN reading.
        //
        // The packet reception loop in NativeOpenVpnClient.kt handles waiting
        // differently - it checks nativeIsConnected() less frequently and waits
        // for connection to be ready before starting packet reception.
        //
        // Return false for connecting=true to fix VpnConnectionManager state detection
        if (session->connecting && !session->connected) {
            // Connection is in progress but not complete - return false
            // This allows VpnConnectionManager to detect connecting state correctly
            return 0;
        }
        
        return 0;
    } catch (const std::exception& e) {
        LOGE("Exception checking connection: %s", e.what());
        // If we can't check, trust session->connected or session->connecting
        if (session) {
            std::lock_guard<std::mutex> lock(session->state_mutex);
            return (session->connected || session->connecting) ? 1 : 0;
        }
        return 0;
    }
#else
    return session->connected ? 1 : 0;
#endif
}

// Get app FD from External TUN Factory (for OPENVPN_EXTERNAL_TUN_FACTORY mode)
int openvpn_wrapper_get_app_fd(OpenVpnSession* session) {
    if (!session) {
        LOGE("openvpn_wrapper_get_app_fd: null session");
        return -1;
    }
    
#if defined(OPENVPN_EXTERNAL_TUN_FACTORY) && defined(OPENVPN3_AVAILABLE)
    if (!session->androidClient) {
        LOGE("openvpn_wrapper_get_app_fd: androidClient not initialized");
        return -1;
    }
    
    int appFd = session->androidClient->getAppFd();
    if (appFd < 0) {
        LOGE("openvpn_wrapper_get_app_fd: Invalid app FD (tunnel not started yet?)");
        return -1;
    }
    
    LOGI("openvpn_wrapper_get_app_fd: Retrieved app FD: %d", appFd);
    return appFd;
#else
    LOGW("openvpn_wrapper_get_app_fd: OPENVPN_EXTERNAL_TUN_FACTORY or OPENVPN3_AVAILABLE not enabled");
    return -1;
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

/**
 * Reconnect an OpenVPN session after a network change.
 * 
 * THE ZOMBIE TUNNEL BUG FIX (Part 3):
 * This function is called from the JNI layer when the device's network changes.
 * It forces the OpenVPN 3 client to drop its dead socket and establish a new one
 * on the new underlying network.
 * 
 * OpenVPN 3 provides a `reconnect()` method specifically for this purpose.
 * It performs a "soft restart" - maintains the session state but establishes
 * a new TCP/UDP connection.
 */
void reconnectSession(OpenVpnSession* session) {
    if (!session) {
        LOGE("reconnectSession: NULL session");
        return;
    }
    
    if (!session->connected && !session->connecting) {
        LOGI("reconnectSession: Session %s not connected, skipping", session->tunnelId.c_str());
        return;
    }
    
#ifdef OPENVPN3_AVAILABLE
    if (session->androidClient) {
        try {
            LOGI("reconnectSession: Calling androidClient->reconnect() for tunnel %s", 
                 session->tunnelId.c_str());
            
            // OpenVPN 3's reconnect() performs a "soft restart":
            // - Maintains session state (keys, compression, etc.)
            // - Closes old socket
            // - Establishes new connection on the new underlying network
            session->androidClient->reconnect(0);  // 0 = reconnect immediately
            
            LOGI("reconnectSession: Reconnect successful for tunnel %s", session->tunnelId.c_str());
        } catch (const std::exception& e) {
            LOGE("reconnectSession: Exception for tunnel %s: %s", 
                 session->tunnelId.c_str(), e.what());
            session->last_error = std::string("Reconnect failed: ") + e.what();
        }
    } else {
        LOGE("reconnectSession: NULL androidClient for tunnel %s", session->tunnelId.c_str());
    }
#else
    LOGI("reconnectSession: OpenVPN 3 not available, skipping reconnect for tunnel %s", 
         session->tunnelId.c_str());
#endif
}

} // extern "C"