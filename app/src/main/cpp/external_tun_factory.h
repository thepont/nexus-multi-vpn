#ifndef EXTERNAL_TUN_FACTORY_H
#define EXTERNAL_TUN_FACTORY_H

#include <openvpn/tun/extern/fw.hpp>
#include <openvpn/common/rc.hpp>
#include <memory>
#include <string>

namespace openvpn {

/**
 * Custom External TUN Factory Implementation
 * 
 * This is the CORRECT way to provide custom TUN implementations to OpenVPN 3 Core.
 * Instead of hacking TunBuilderBase, we implement ExternalTun::Factory which is
 * explicitly designed for this purpose.
 * 
 * Architecture:
 * 1. Create a socketpair (bidirectional FD pair)
 * 2. Give "library end" FD to OpenVPN Core
 * 3. Keep "app end" FD for our application
 * 4. OpenVPN reads/writes encrypted packets from/to library end
 * 5. Our app reads/writes decrypted packets from/to app end
 * 
 * This gives us full control over packet routing without using VpnService!
 */
class CustomExternalTunFactory : public ExternalTun::Factory {
public:
    typedef RCPtr<CustomExternalTunFactory> Ptr;
    
    CustomExternalTunFactory(const std::string& tunnel_id) 
        : tunnel_id_(tunnel_id),
          app_fd_(-1),
          lib_fd_(-1) {
    }
    
    virtual ~CustomExternalTunFactory() {
        cleanup();
    }
    
    /**
     * Called by OpenVPN 3 Core to create the TUN device.
     * 
     * This is where we create our socketpair and return the library end FD.
     * 
     * @return File descriptor for OpenVPN 3 to use (library end of socketpair)
     */
    virtual int tun_builder_establish() override {
        LOGI("═══════════════════════════════════════════════════════");
        LOGI("🔧 CustomExternalTunFactory::tun_builder_establish()");
        LOGI("   Creating socketpair for tunnel: %s", tunnel_id_.c_str());
        LOGI("═══════════════════════════════════════════════════════");
        
        // Create socketpair for bidirectional communication
        int sockets[2];
        if (socketpair(AF_UNIX, SOCK_SEQPACKET, 0, sockets) == -1) {
            LOGE("❌ Failed to create socket pair: %s", strerror(errno));
            return -1;
        }
        
        app_fd_ = sockets[0];   // Our application's end
        lib_fd_ = sockets[1];   // OpenVPN 3's end
        
        LOGI("✅ Socket pair created successfully:");
        LOGI("   App FD:  %d (for our packet routing)", app_fd_);
        LOGI("   Lib FD:  %d (for OpenVPN 3 Core)", lib_fd_);
        
        // Set non-blocking on library end
        int flags = fcntl(lib_fd_, F_GETFL, 0);
        if (flags != -1) {
            fcntl(lib_fd_, F_SETFL, flags | O_NONBLOCK);
            LOGI("✅ Set O_NONBLOCK on library FD");
        }
        
        LOGI("═══════════════════════════════════════════════════════");
        LOGI("✅ Returning library FD %d to OpenVPN 3 Core", lib_fd_);
        LOGI("   OpenVPN will read/write encrypted packets here");
        LOGI("   Our app will read/write decrypted packets to FD %d", app_fd_);
        LOGI("═══════════════════════════════════════════════════════");
        
        return lib_fd_;
    }
    
    /**
     * Get the application end of the socketpair.
     * Our app will use this FD to read decrypted packets from OpenVPN
     * and write plaintext packets to OpenVPN for encryption.
     */
    int getAppFd() const {
        return app_fd_;
    }
    
    /**
     * Called by OpenVPN 3 when TUN is being torn down
     */
    virtual void tun_builder_teardown(bool disconnect) override {
        LOGI("🔧 CustomExternalTunFactory::tun_builder_teardown(disconnect=%s)", 
             disconnect ? "true" : "false");
        cleanup();
    }
    
    /**
     * Set MTU (optional, can be no-op)
     */
    virtual bool tun_builder_set_mtu(int mtu) override {
        LOGI("tun_builder_set_mtu: %d", mtu);
        return true;
    }
    
    /**
     * Add IP address to TUN (optional, can be no-op for external TUN)
     */
    virtual bool tun_builder_add_address(const std::string& address,
                                         int prefix_length,
                                         const std::string& gateway,
                                         bool ipv6,
                                         bool net30) override {
        LOGI("tun_builder_add_address: %s/%d (ipv6=%s)", 
             address.c_str(), prefix_length, ipv6 ? "true" : "false");
        
        // For external TUN, we just note the IP but don't configure anything
        // The VpnEngineService will handle the actual VPN interface
        if (!ipv6) {
            tun_ip_ = address;
            tun_prefix_ = prefix_length;
        }
        
        return true;
    }
    
    /**
     * Add route (optional, can be no-op for external TUN)
     */
    virtual bool tun_builder_add_route(const std::string& address,
                                       int prefix_length,
                                       int metric,
                                       bool ipv6) override {
        LOGI("tun_builder_add_route: %s/%d", address.c_str(), prefix_length);
        return true;
    }
    
    /**
     * Set DNS servers (optional, can be no-op for external TUN)
     */
    virtual bool tun_builder_add_dns_server(const std::string& address, bool ipv6) override {
        LOGI("tun_builder_add_dns_server: %s (ipv6=%s)", 
             address.c_str(), ipv6 ? "true" : "false");
        return true;
    }
    
    /**
     * Get the TUN IP address that OpenVPN assigned
     */
    std::string getTunIp() const {
        return tun_ip_;
    }
    
    /**
     * Get the TUN prefix length
     */
    int getTunPrefix() const {
        return tun_prefix_;
    }
    
private:
    void cleanup() {
        if (app_fd_ >= 0) {
            close(app_fd_);
            app_fd_ = -1;
        }
        if (lib_fd_ >= 0) {
            close(lib_fd_);
            lib_fd_ = -1;
        }
    }
    
    std::string tunnel_id_;
    int app_fd_;      // Our application's FD
    int lib_fd_;      // OpenVPN 3's FD
    std::string tun_ip_;
    int tun_prefix_;
};

} // namespace openvpn

#endif // EXTERNAL_TUN_FACTORY_H

