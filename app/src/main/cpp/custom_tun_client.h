#ifndef CUSTOM_TUN_CLIENT_H
#define CUSTOM_TUN_CLIENT_H

#include <openvpn/tun/client/tunbase.hpp>
#include <openvpn/common/rc.hpp>
#include <openvpn/buffer/buffer.hpp>
#include <openvpn/io/io.hpp>
#include <android/log.h>
#include <string>
#include <sstream>
#include <sys/socket.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <cstring>

// Note: OPENVPN_LOG is now defined globally via openvpn_log_override.h (force-included)

namespace openvpn {

/**
 * Custom TUN Client Implementation using ExternalTun::Factory
 * 
 * This is the CORRECT way to implement custom TUN for OpenVPN 3.
 * OpenVPN 3's event loop will actively poll the FD we provide.
 * 
 * Architecture:
 * 1. Factory creates socketpair (app_fd, lib_fd)
 * 2. TunClient provides lib_fd to OpenVPN 3's event loop
 * 3. OpenVPN 3 polls lib_fd for readability/writability
 * 4. Our app uses app_fd for packet I/O
 * 
 * Packet Flow:
 * - Outbound: App writes plaintext to app_fd → OpenVPN reads from lib_fd → Encrypts → Sends to server
 * - Inbound: Server sends encrypted → OpenVPN decrypts → Writes to lib_fd → App reads from app_fd
 */
class CustomTunClient : public TunClient {
public:
    typedef RCPtr<CustomTunClient> Ptr;
    
    CustomTunClient(openvpn_io::io_context& io_context,
                    TunClientParent& parent,
                    const std::string& tunnel_id)
        : io_context_(io_context),
          parent_(parent),
          tunnel_id_(tunnel_id),
          app_fd_(-1),
          lib_fd_(-1),
          halt_(false),
          mtu_(1500) {
        
        OPENVPN_LOG("CustomTunClient created for tunnel: " << tunnel_id_);
    }
    
    virtual ~CustomTunClient() {
        OPENVPN_LOG("CustomTunClient destroyed for tunnel: " << tunnel_id_);
        cleanup();
    }
    
    /**
     * Called by OpenVPN 3 to start the TUN interface
     */
    virtual void tun_start(const OptionList& opt,
                          TransportClient& transcli,
                          CryptoDCSettings& dc_settings) override {
        OPENVPN_LOG("═══════════════════════════════════════════════════════");
        OPENVPN_LOG("CustomTunClient::tun_start() for tunnel: " << tunnel_id_);
        OPENVPN_LOG("═══════════════════════════════════════════════════════");
        
        // Create socketpair for bidirectional communication
        int sockets[2];
        if (socketpair(AF_UNIX, SOCK_SEQPACKET, 0, sockets) == -1) {
            OPENVPN_LOG("❌ Failed to create socket pair: " << strerror(errno));
            parent_.tun_error(Error::TUN_SETUP_FAILED, 
                             std::string("Failed to create socket pair: ") + strerror(errno));
            return;
        }
        
        app_fd_ = sockets[0];   // Our application's end
        lib_fd_ = sockets[1];   // OpenVPN 3's end
        
        OPENVPN_LOG("✅ Socket pair created successfully:");
        OPENVPN_LOG("   App FD:  " << app_fd_ << " (for packet routing)");
        OPENVPN_LOG("   Lib FD:  " << lib_fd_ << " (for OpenVPN 3 Core)");
        
        // Set non-blocking on library end (OpenVPN 3 expects non-blocking)
        int flags = fcntl(lib_fd_, F_GETFL, 0);
        if (flags != -1) {
            fcntl(lib_fd_, F_SETFL, flags | O_NONBLOCK);
            OPENVPN_LOG("✅ Set O_NONBLOCK on library FD");
        }
        
        // Set non-blocking on app end too (for our async reading)
        flags = fcntl(app_fd_, F_GETFL, 0);
        if (flags != -1) {
            fcntl(app_fd_, F_SETFL, flags | O_NONBLOCK);
            OPENVPN_LOG("✅ Set O_NONBLOCK on app FD");
        }
        
        // Extract TUN configuration from options
        // OpenVPN 3 will have pushed IP address, routes, DNS, etc. in opt
        extract_tun_config(opt);
        
        // Notify parent that TUN is pre-configured
        parent_.tun_pre_tun_config();
        
        // Notify parent that routes are pre-configured
        parent_.tun_pre_route_config();
        
        // Start async reading from lib_fd (OpenVPN → App)
        start_async_read();
        
        // Notify parent that TUN is connected
        parent_.tun_connected();
        
        OPENVPN_LOG("═══════════════════════════════════════════════════════");
        OPENVPN_LOG("✅ CustomTunClient started successfully for tunnel: " << tunnel_id_);
        OPENVPN_LOG("   lib_fd: " << lib_fd_ << " (OpenVPN 3 will poll this)");
        OPENVPN_LOG("   app_fd: " << app_fd_ << " (Our app will use this)");
        OPENVPN_LOG("═══════════════════════════════════════════════════════");
    }
    
    /**
     * Called by OpenVPN 3 to stop the TUN interface
     */
    virtual void stop() override {
        OPENVPN_LOG("CustomTunClient::stop() for tunnel: " << tunnel_id_);
        halt_ = true;
        cleanup();
    }
    
    /**
     * Called by OpenVPN 3 on disconnect
     */
    virtual void set_disconnect() override {
        OPENVPN_LOG("CustomTunClient::set_disconnect() for tunnel: " << tunnel_id_);
        halt_ = true;
    }
    
    /**
     * Called by OpenVPN 3 to send a packet to TUN
     * (This is where OpenVPN writes decrypted packets to us)
     * 
     * @param buf Buffer containing decrypted IP packet
     * @return true if send succeeded
     */
    virtual bool tun_send(BufferAllocated& buf) override {
        if (halt_ || lib_fd_ < 0) {
            return false;
        }
        
        // Write decrypted packet to lib_fd
        // Our app will read this from app_fd
        ssize_t n = write(lib_fd_, buf.c_data(), buf.size());
        
        if (n < 0) {
            if (errno == EAGAIN || errno == EWOULDBLOCK) {
                // Would block - queue for later (or drop)
                OPENVPN_LOG("tun_send: Would block, dropping packet");
                return false;
            }
            OPENVPN_LOG("❌ tun_send: write error: " << strerror(errno));
            return false;
        }
        
        if (static_cast<size_t>(n) != buf.size()) {
            OPENVPN_LOG("⚠️  tun_send: partial write (" << n << "/" << buf.size() << " bytes)");
            return false;
        }
        
        // Successfully wrote packet
        return true;
    }
    
    /**
     * Get TUN interface name
     */
    virtual std::string tun_name() const override {
        return "custom_tun_" + tunnel_id_;
    }
    
    /**
     * Get VPN IPv4 address
     */
    virtual std::string vpn_ip4() const override {
        return vpn_ip4_;
    }
    
    /**
     * Get VPN IPv6 address
     */
    virtual std::string vpn_ip6() const override {
        return vpn_ip6_;
    }
    
    /**
     * Get VPN MTU
     */
    virtual int vpn_mtu() const override {
        return mtu_;
    }
    
    /**
     * Get the app FD (for our application to use)
     */
    int getAppFd() const {
        return app_fd_;
    }
    
    /**
     * Get the lib FD (for OpenVPN 3 to use)
     */
    int getLibFd() const {
        return lib_fd_;
    }
    
private:
    /**
     * Start async reading from lib_fd
     * This reads packets that OpenVPN has decrypted and sends them to parent
     */
    void start_async_read() {
        if (halt_ || lib_fd_ < 0) {
            return;
        }
        
        // For now, we'll let VpnConnectionManager handle the reading
        // OpenVPN 3 will poll lib_fd and call tun_send() when data arrives
        OPENVPN_LOG("Async read setup complete (VpnConnectionManager will read from app_fd)");
    }
    
    /**
     * Extract TUN configuration from OpenVPN options
     */
    void extract_tun_config(const OptionList& opt) {
        // Extract IP address
        const Option* ip_opt = opt.get_ptr("ifconfig");
        if (ip_opt && ip_opt->size() >= 2) {
            vpn_ip4_ = ip_opt->get(1, 256);  // max_len = 256 for IP address
            OPENVPN_LOG("TUN IP: " << vpn_ip4_);
        }
        
        // Extract MTU
        const Option* mtu_opt = opt.get_ptr("tun-mtu");
        if (mtu_opt && mtu_opt->size() >= 2) {
            try {
                mtu_ = std::stoi(mtu_opt->get(1, 16));  // max_len = 16 for MTU number
                OPENVPN_LOG("TUN MTU: " << mtu_);
            } catch (...) {
                OPENVPN_LOG("⚠️  Failed to parse MTU, using default: " << mtu_);
            }
        }
    }
    
    /**
     * Clean up resources
     */
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
    
    openvpn_io::io_context& io_context_;
    TunClientParent& parent_;
    std::string tunnel_id_;
    int app_fd_;      // Our application's FD
    int lib_fd_;      // OpenVPN 3's FD
    bool halt_;
    int mtu_;
    std::string vpn_ip4_;
    std::string vpn_ip6_;
};

/**
 * Factory for CustomTunClient
 */
class CustomTunClientFactory : public TunClientFactory {
public:
    typedef RCPtr<CustomTunClientFactory> Ptr;
    
    CustomTunClientFactory(const std::string& tunnel_id)
        : tunnel_id_(tunnel_id) {
        OPENVPN_LOG("CustomTunClientFactory created for tunnel: " << tunnel_id_);
    }
    
    virtual ~CustomTunClientFactory() {
        OPENVPN_LOG("CustomTunClientFactory destroyed for tunnel: " << tunnel_id_);
    }
    
    /**
     * Called by OpenVPN 3 to create a new TunClient instance
     */
    virtual TunClient::Ptr new_tun_client_obj(openvpn_io::io_context& io_context,
                                              TunClientParent& parent,
                                              TransportClient* transcli) override {
        OPENVPN_LOG("Creating new CustomTunClient for tunnel: " << tunnel_id_);
        auto client = new CustomTunClient(io_context, parent, tunnel_id_);
        tun_client_ = client;  // Keep reference to get FDs later
        return TunClient::Ptr(client);
    }
    
    /**
     * Layer 2 tunnels not supported
     */
    virtual bool layer_2_supported() const override {
        return false;
    }
    
    /**
     * Data v3 features not supported yet
     */
    virtual bool supports_epoch_data() override {
        return false;
    }
    
    /**
     * Get the app FD from the created TunClient
     */
    int getAppFd() const {
        if (tun_client_) {
            return tun_client_->getAppFd();
        }
        return -1;
    }
    
    /**
     * Get the lib FD from the created TunClient
     */
    int getLibFd() const {
        if (tun_client_) {
            return tun_client_->getLibFd();
        }
        return -1;
    }
    
private:
    std::string tunnel_id_;
    CustomTunClient* tun_client_ = nullptr;  // Non-owning pointer to get FDs
};

} // namespace openvpn

#endif // CUSTOM_TUN_CLIENT_H

