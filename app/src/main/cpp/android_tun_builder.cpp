#include "android_tun_builder.h"
#include <android/log.h>

#define TAG "AndroidTunBuilder"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

AndroidTunBuilder::AndroidTunBuilder(JNIEnv* env, jobject vpnServiceBuilder)
    : env_(env), vpnServiceBuilder_(vpnServiceBuilder), tun_fd_(-1) {
    // Keep a global reference to the builder
    if (env_ && vpnServiceBuilder_) {
        vpnServiceBuilder_ = env_->NewGlobalRef(vpnServiceBuilder_);
    }
    LOGI("AndroidTunBuilder created");
}

AndroidTunBuilder::~AndroidTunBuilder() {
    if (env_ && vpnServiceBuilder_) {
        env_->DeleteGlobalRef(vpnServiceBuilder_);
    }
    LOGI("AndroidTunBuilder destroyed");
}

bool AndroidTunBuilder::tun_builder_new() {
    LOGI("tun_builder_new() called");
    // Builder is already created in Java/Kotlin, so just return true
    return true;
}

bool AndroidTunBuilder::tun_builder_set_layer(int layer) {
    LOGI("tun_builder_set_layer() called with layer=%d", layer);
    // Layer 3 = TUN (which we want), Layer 2 = TAP (not supported)
    return layer == 3;
}

bool AndroidTunBuilder::tun_builder_set_remote_address(const std::string &address, bool ipv6) {
    LOGI("tun_builder_set_remote_address() called: %s (ipv6=%s)", address.c_str(), ipv6 ? "true" : "false");
    // Android VpnService.Builder doesn't need remote address - it's implicit
    return true;
}

bool AndroidTunBuilder::tun_builder_add_address(const std::string &address,
                                                 int prefix_length,
                                                 const std::string &gateway,
                                                 bool ipv6,
                                                 bool net30) {
    LOGI("tun_builder_add_address() called: %s/%d (ipv6=%s, gateway=%s)",
         address.c_str(), prefix_length, ipv6 ? "true" : "false", gateway.c_str());
    
    if (!env_ || !vpnServiceBuilder_) {
        LOGE("JNI environment or builder not available");
        return false;
    }
    
    return addAddressToBuilder(address, prefix_length);
}

bool AndroidTunBuilder::tun_builder_reroute_gw(bool ipv4, bool ipv6, unsigned int flags) {
    LOGI("tun_builder_reroute_gw() called: ipv4=%s, ipv6=%s, flags=0x%x",
         ipv4 ? "true" : "false", ipv6 ? "true" : "false", flags);
    
    if (!env_ || !vpnServiceBuilder_) {
        LOGE("JNI environment or builder not available");
        return false;
    }
    
    // Reroute default gateway - add route to 0.0.0.0/0
    bool success = true;
    if (ipv4) {
        success = addRouteToBuilder("0.0.0.0", 0) && success;
    }
    if (ipv6) {
        success = addRouteToBuilder("::", 0) && success;
    }
    
    return success;
}

bool AndroidTunBuilder::tun_builder_add_route(const std::string &address,
                                              int prefix_length,
                                              int metric,
                                              bool ipv6) {
    LOGI("tun_builder_add_route() called: %s/%d (ipv6=%s, metric=%d)",
         address.c_str(), prefix_length, ipv6 ? "true" : "false", metric);
    
    if (!env_ || !vpnServiceBuilder_) {
        LOGE("JNI environment or builder not available");
        return false;
    }
    
    return addRouteToBuilder(address, prefix_length);
}

bool AndroidTunBuilder::tun_builder_exclude_route(const std::string &address,
                                                   int prefix_length,
                                                   int metric,
                                                   bool ipv6) {
    LOGI("tun_builder_exclude_route() called: %s/%d (ipv6=%s, metric=%d)",
         address.c_str(), prefix_length, ipv6 ? "true" : "false", metric);
    // Android doesn't support exclude routes directly in VpnService.Builder
    // This would need to be handled differently or ignored
    return true;
}

bool AndroidTunBuilder::tun_builder_set_dns_options(const openvpn::DnsOptions &dns) {
    LOGI("tun_builder_set_dns_options() called");
    
    if (!env_ || !vpnServiceBuilder_) {
        LOGE("JNI environment or builder not available");
        return false;
    }
    
    // Add DNS servers
    for (const auto& dns_server : dns.servers) {
        if (!addDnsServerToBuilder(dns_server)) {
            return false;
        }
    }
    
    return true;
}

bool AndroidTunBuilder::tun_builder_set_mtu(int mtu) {
    LOGI("tun_builder_set_mtu() called: %d", mtu);
    
    if (!env_ || !vpnServiceBuilder_) {
        LOGE("JNI environment or builder not available");
        return false;
    }
    
    jclass builderClass = env_->GetObjectClass(vpnServiceBuilder_);
    if (!builderClass) {
        LOGE("Failed to get VpnService.Builder class");
        return false;
    }
    
    jmethodID setMtuMethod = env_->GetMethodID(builderClass, "setMtu", "(I)Landroid/net/VpnService$Builder;");
    if (!setMtuMethod) {
        LOGE("Failed to find setMtu method");
        env_->DeleteLocalRef(builderClass);
        return false;
    }
    
    env_->CallObjectMethod(vpnServiceBuilder_, setMtuMethod, mtu);
    env_->DeleteLocalRef(builderClass);
    
    bool success = !env_->ExceptionCheck();
    if (!success) {
        env_->ExceptionClear();
        LOGE("Exception calling setMtu");
    }
    
    return success;
}

bool AndroidTunBuilder::tun_builder_set_session_name(const std::string &name) {
    LOGI("tun_builder_set_session_name() called: %s", name.c_str());
    
    if (!env_ || !vpnServiceBuilder_) {
        LOGE("JNI environment or builder not available");
        return false;
    }
    
    jclass builderClass = env_->GetObjectClass(vpnServiceBuilder_);
    if (!builderClass) {
        LOGE("Failed to get VpnService.Builder class");
        return false;
    }
    
    jmethodID setSessionMethod = env_->GetMethodID(builderClass, "setSession", "(Ljava/lang/String;)Landroid/net/VpnService$Builder;");
    if (!setSessionMethod) {
        LOGE("Failed to find setSession method");
        env_->DeleteLocalRef(builderClass);
        return false;
    }
    
    jstring sessionName = env_->NewStringUTF(name.c_str());
    env_->CallObjectMethod(vpnServiceBuilder_, setSessionMethod, sessionName);
    env_->DeleteLocalRef(sessionName);
    env_->DeleteLocalRef(builderClass);
    
    bool success = !env_->ExceptionCheck();
    if (!success) {
        env_->ExceptionClear();
        LOGE("Exception calling setSession");
    }
    
    return success;
}

bool AndroidTunBuilder::tun_builder_add_proxy_bypass(const std::string &bypass_host) {
    LOGI("tun_builder_add_proxy_bypass() called: %s", bypass_host.c_str());
    // Android VpnService.Builder doesn't support proxy bypass directly
    return true;
}

bool AndroidTunBuilder::tun_builder_set_proxy_auto_config_url(const std::string &url) {
    LOGI("tun_builder_set_proxy_auto_config_url() called: %s", url.c_str());
    // Android VpnService.Builder doesn't support proxy auto-config
    return true;
}

bool AndroidTunBuilder::tun_builder_set_proxy_http(const std::string &host, int port) {
    LOGI("tun_builder_set_proxy_http() called: %s:%d", host.c_str(), port);
    // Android VpnService.Builder doesn't support HTTP proxy
    return true;
}

bool AndroidTunBuilder::tun_builder_set_proxy_https(const std::string &host, int port) {
    LOGI("tun_builder_set_proxy_https() called: %s:%d", host.c_str(), port);
    // Android VpnService.Builder doesn't support HTTPS proxy
    return true;
}

bool AndroidTunBuilder::tun_builder_add_wins_server(const std::string &address) {
    LOGI("tun_builder_add_wins_server() called: %s", address.c_str());
    // WINS is Windows-specific, not needed for Android
    return true;
}

bool AndroidTunBuilder::tun_builder_set_allow_family(int af, bool allow) {
    LOGI("tun_builder_set_allow_family() called: af=%d, allow=%s", af, allow ? "true" : "false");
    // AF_INET = IPv4, AF_INET6 = IPv6
    // Android VpnService.Builder handles this automatically based on addresses/routes
    return true;
}

bool AndroidTunBuilder::tun_builder_set_allow_local_dns(bool allow) {
    LOGI("tun_builder_set_allow_local_dns() called: %s", allow ? "true" : "false");
    // Android handles DNS blocking automatically
    return true;
}

bool AndroidTunBuilder::tun_builder_establish() {
    LOGI("tun_builder_establish() called");
    
    int fd = tun_builder_establish_lite();
    return fd >= 0;
}

int AndroidTunBuilder::tun_builder_establish_lite() {
    LOGI("tun_builder_establish_lite() called");
    
    if (!env_ || !vpnServiceBuilder_) {
        LOGE("JNI environment or builder not available");
        return -1;
    }
    
    jclass builderClass = env_->GetObjectClass(vpnServiceBuilder_);
    if (!builderClass) {
        LOGE("Failed to get VpnService.Builder class");
        return -1;
    }
    
    // Call establish() on VpnService.Builder
    jmethodID establishMethod = env_->GetMethodID(builderClass, "establish", "()Landroid/os/ParcelFileDescriptor;");
    if (!establishMethod) {
        LOGE("Failed to find establish method");
        env_->DeleteLocalRef(builderClass);
        return -1;
    }
    
    jobject pfd = env_->CallObjectMethod(vpnServiceBuilder_, establishMethod);
    if (env_->ExceptionCheck()) {
        env_->ExceptionDescribe();
        env_->ExceptionClear();
        env_->DeleteLocalRef(builderClass);
        LOGE("Exception calling establish");
        return -1;
    }
    
    if (!pfd) {
        LOGE("establish() returned null");
        env_->DeleteLocalRef(builderClass);
        return -1;
    }
    
    // Get file descriptor from ParcelFileDescriptor
    jclass pfdClass = env_->GetObjectClass(pfd);
    jmethodID getFdMethod = env_->GetMethodID(pfdClass, "detachFd", "()I");
    if (!getFdMethod) {
        // Try getFd() as fallback
        getFdMethod = env_->GetMethodID(pfdClass, "getFd", "()I");
    }
    
    if (!getFdMethod) {
        LOGE("Failed to find detachFd or getFd method");
        env_->DeleteLocalRef(pfd);
        env_->DeleteLocalRef(pfdClass);
        env_->DeleteLocalRef(builderClass);
        return -1;
    }
    
    jint fd = env_->CallIntMethod(pfd, getFdMethod);
    env_->DeleteLocalRef(pfd);
    env_->DeleteLocalRef(pfdClass);
    env_->DeleteLocalRef(builderClass);
    
    if (env_->ExceptionCheck()) {
        env_->ExceptionClear();
        LOGE("Exception getting file descriptor");
        return -1;
    }
    
    tun_fd_ = fd;
    LOGI("TUN interface established with file descriptor: %d", fd);
    return fd;
}

// Helper method implementations
bool AndroidTunBuilder::addAddressToBuilder(const std::string &address, int prefix_length) {
    jclass builderClass = env_->GetObjectClass(vpnServiceBuilder_);
    if (!builderClass) {
        LOGE("Failed to get VpnService.Builder class");
        return false;
    }
    
    jmethodID addAddressMethod = env_->GetMethodID(builderClass, "addAddress", 
                                                   "(Ljava/lang/String;I)Landroid/net/VpnService$Builder;");
    if (!addAddressMethod) {
        LOGE("Failed to find addAddress method");
        env_->DeleteLocalRef(builderClass);
        return false;
    }
    
    jstring addrStr = env_->NewStringUTF(address.c_str());
    env_->CallObjectMethod(vpnServiceBuilder_, addAddressMethod, addrStr, prefix_length);
    env_->DeleteLocalRef(addrStr);
    env_->DeleteLocalRef(builderClass);
    
    bool success = !env_->ExceptionCheck();
    if (!success) {
        env_->ExceptionClear();
        LOGE("Exception calling addAddress");
    }
    
    return success;
}

bool AndroidTunBuilder::addRouteToBuilder(const std::string &address, int prefix_length) {
    jclass builderClass = env_->GetObjectClass(vpnServiceBuilder_);
    if (!builderClass) {
        LOGE("Failed to get VpnService.Builder class");
        return false;
    }
    
    jmethodID addRouteMethod = env_->GetMethodID(builderClass, "addRoute", 
                                                 "(Ljava/lang/String;I)Landroid/net/VpnService$Builder;");
    if (!addRouteMethod) {
        LOGE("Failed to find addRoute method");
        env_->DeleteLocalRef(builderClass);
        return false;
    }
    
    jstring routeStr = env_->NewStringUTF(address.c_str());
    env_->CallObjectMethod(vpnServiceBuilder_, addRouteMethod, routeStr, prefix_length);
    env_->DeleteLocalRef(routeStr);
    env_->DeleteLocalRef(builderClass);
    
    bool success = !env_->ExceptionCheck();
    if (!success) {
        env_->ExceptionClear();
        LOGE("Exception calling addRoute");
    }
    
    return success;
}

bool AndroidTunBuilder::addDnsServerToBuilder(const std::string &dns) {
    jclass builderClass = env_->GetObjectClass(vpnServiceBuilder_);
    if (!builderClass) {
        LOGE("Failed to get VpnService.Builder class");
        return false;
    }
    
    jmethodID addDnsMethod = env_->GetMethodID(builderClass, "addDnsServer", 
                                               "(Ljava/lang/String;)Landroid/net/VpnService$Builder;");
    if (!addDnsMethod) {
        LOGE("Failed to find addDnsServer method");
        env_->DeleteLocalRef(builderClass);
        return false;
    }
    
    jstring dnsStr = env_->NewStringUTF(dns.c_str());
    env_->CallObjectMethod(vpnServiceBuilder_, addDnsMethod, dnsStr);
    env_->DeleteLocalRef(dnsStr);
    env_->DeleteLocalRef(builderClass);
    
    bool success = !env_->ExceptionCheck();
    if (!success) {
        env_->ExceptionClear();
        LOGE("Exception calling addDnsServer");
    }
    
    return success;
}


