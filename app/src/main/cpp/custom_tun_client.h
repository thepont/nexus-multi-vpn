#ifndef CUSTOM_TUN_CLIENT_H
#define CUSTOM_TUN_CLIENT_H

#include <openvpn/tun/client/tunbase.hpp>
#include <openvpn/common/rc.hpp>
#include <openvpn/buffer/buffer.hpp>
#include <openvpn/io/io.hpp>
#include <asio/posix/stream_descriptor.hpp>
#include <android/log.h>
#include <string>
#include <sstream>
#include <array>
#include <memory>
#include <sys/socket.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <cstring>

// Note: OPENVPN_LOG is now defined globally via openvpn_log_override.h (force-included)

namespace openvpn {

/**
 * Callback interface for IP/DNS notifications from CustomTunClient
 */
class CustomTunCallback {
public:
    virtual ~CustomTunCallback() {}
    virtual void on_ip_assigned(const std::string& tunnel_id, const std::string& ip, int prefix_len) = 0;
    virtual void on_dns_configured(const std::string& tunnel_id, const std::vector<std::string>& dns_servers) = 0;
};

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
                    const std::string& tunnel_id,
                    CustomTunCallback* callback = nullptr)
        : io_context_(io_context),
          parent_(parent),
          tunnel_id_(tunnel_id),
          callback_(callback),
          app_fd_(-1),
          lib_fd_(-1),
          stream_(nullptr),
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
        OPENVPN_LOG("CustomTunClient::tun_start() for tunnel: " << tunnel_id_);
        
        // Create socketpair for bidirectional communication
        int sockets[2];
        if (socketpair(AF_UNIX, SOCK_SEQPACKET, 0, sockets) == -1) {
            OPENVPN_LOG("Failed to create socket pair: " << strerror(errno));
            parent_.tun_error(Error::TUN_SETUP_FAILED, 
                             std::string("Failed to create socket pair: ") + strerror(errno));
            return;
        }
        
        app_fd_ = sockets[0];   // Our application's end
        lib_fd_ = sockets[1];   // OpenVPN 3's end
        
        OPENVPN_LOG("Socket pair created: app_fd=" << app_fd_ << " lib_fd=" << lib_fd_);
        
        // Set non-blocking on both ends
        int flags = fcntl(lib_fd_, F_GETFL, 0);
        if (flags != -1) {
            fcntl(lib_fd_, F_SETFL, flags | O_NONBLOCK);
        }
        
        flags = fcntl(app_fd_, F_GETFL, 0);
        if (flags != -1) {
            fcntl(app_fd_, F_SETFL, flags | O_NONBLOCK);
        }
        
        // Extract TUN configuration from options
        extract_tun_config(opt);
        
        // Notify parent
        parent_.tun_pre_tun_config();
        parent_.tun_pre_route_config();
        start_async_read();  // CRITICAL: Start reading from lib_fd to feed packets to OpenVPN
        parent_.tun_connected();
        
        OPENVPN_LOG("CustomTunClient started for tunnel: " << tunnel_id_);
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
        // CRITICAL LOGGING: Log EVERY call to tun_send to verify OpenVPN is calling it
        __android_log_print(ANDROID_LOG_INFO, "OpenVPN-CustomTUN", 
            "🔔🔔🔔 tun_send() CALLED! tunnel=%s, packet_size=%zu bytes, halt=%d, lib_fd=%d", 
            tunnel_id_.c_str(), buf.size(), halt_, lib_fd_);
        
        if (halt_ || lib_fd_ < 0) {
            __android_log_print(ANDROID_LOG_WARN, "OpenVPN-CustomTUN",
                "❌ tun_send: Cannot send - halt=%d, lib_fd=%d", halt_, lib_fd_);
            return false;
        }
        
        // Write decrypted packet to lib_fd
        // Our app will read this from app_fd
        ssize_t n = write(lib_fd_, buf.c_data(), buf.size());
        
        if (n < 0) {
            if (errno == EAGAIN || errno == EWOULDBLOCK) {
                // Would block - queue for later (or drop)
                __android_log_print(ANDROID_LOG_WARN, "OpenVPN-CustomTUN",
                    "⚠️  tun_send: Would block, dropping packet");
                return false;
            }
            __android_log_print(ANDROID_LOG_ERROR, "OpenVPN-CustomTUN",
                "❌ tun_send: write error: %s (errno=%d)", strerror(errno), errno);
            return false;
        }
        
        if (static_cast<size_t>(n) != buf.size()) {
            __android_log_print(ANDROID_LOG_WARN, "OpenVPN-CustomTUN",
                "⚠️  tun_send: partial write (%zd/%zu bytes)", n, buf.size());
            return false;
        }
        
        // Successfully wrote packet
        __android_log_print(ANDROID_LOG_INFO, "OpenVPN-CustomTUN",
            "✅ tun_send: Successfully wrote %zu bytes to lib_fd=%d", buf.size(), lib_fd_);
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
     * CRITICAL: This implements the OUTBOUND path (app → OpenVPN → server)
     * We read packets from lib_fd and call parent_.tun_recv() to feed them into OpenVPN
     */
    void start_async_read() {
        if (halt_ || lib_fd_ < 0) {
            return;
        }
        
        try {
            // Create Asio stream descriptor to register lib_fd with OpenVPN's io_context
            // This makes OpenVPN's event loop poll lib_fd for readability
            stream_ = new openvpn_io::posix::stream_descriptor(io_context_, lib_fd_);
            __android_log_print(ANDROID_LOG_INFO, "OpenVPN-CustomTUN",
                "✅ Registered lib_fd=%d with OpenVPN io_context - OUTBOUND path ready", lib_fd_);
            
            // Start async read loop
            queue_read();
        } catch (const std::exception& e) {
            __android_log_print(ANDROID_LOG_ERROR, "OpenVPN-CustomTUN",
                "❌ Failed to register lib_fd with io_context: %s", e.what());
        }
    }
    
    /**
     * Queue async read from lib_fd
     * This is the OUTBOUND path: App writes to app_fd → we read from lib_fd → OpenVPN encrypts
     */
    void queue_read() {
        if (halt_ || !stream_) {
            __android_log_print(ANDROID_LOG_WARN, "OpenVPN-CustomTUN",
                "⚠️  queue_read() skipped: halt=%d, stream=%p", halt_, (void*)stream_);
            return;
        }
        
        __android_log_print(ANDROID_LOG_DEBUG, "OpenVPN-CustomTUN",
            "📖 Queuing next async read from lib_fd...");
        
        // Use a simple byte buffer for reading, then copy to BufferAllocated
        auto read_buf = std::make_shared<std::array<uint8_t, 2048>>();
        
        stream_->async_read_some(
            openvpn_io::buffer(read_buf->data(), read_buf->size()),
            [this, read_buf](const openvpn_io::error_code& error, std::size_t bytes_read) mutable {
                handle_read(error, bytes_read, read_buf);
            }
        );
    }
    
    /**
     * Handle packet read from lib_fd
     * Feed packet to OpenVPN for encryption and transmission
     */
    void handle_read(const openvpn_io::error_code& error, std::size_t bytes_read,
                    std::shared_ptr<std::array<uint8_t, 2048>> read_buf) {
        __android_log_print(ANDROID_LOG_DEBUG, "OpenVPN-CustomTUN",
            "📬 handle_read() called: error=%d, bytes_read=%zu, halt=%d",
            error.value(), bytes_read, halt_);
        
        if (halt_) {
            __android_log_print(ANDROID_LOG_WARN, "OpenVPN-CustomTUN",
                "   handle_read() exiting early: halt=true");
            return;
        }
        
        if (!error && bytes_read > 0) {
            __android_log_print(ANDROID_LOG_INFO, "OpenVPN-CustomTUN",
                "📤 OUTBOUND: Read %zu bytes from lib_fd (from app) - feeding to OpenVPN", bytes_read);
            
            try {
                // CRITICAL: Allocate buffer with headroom for encryption headers!
                // OpenVPN needs space at the front of the buffer to add encryption overhead
                // Typically needs 128-256 bytes of headroom for:
                // - Protocol headers (up to 100 bytes)
                // - Encryption/HMAC overhead (up to 100 bytes)
                // - Alignment padding
                constexpr size_t HEADROOM = 256;
                constexpr size_t TAILROOM = 128;
                
                // Allocate buffer with total capacity: headroom + packet + tailroom
                BufferAllocated buf(HEADROOM + bytes_read + TAILROOM, BufAllocFlags::CONSTRUCT_ZERO);
                
                // Initialize headroom (sets offset to start of headroom region)
                buf.init_headroom(HEADROOM);
                
                // Copy packet data to buffer (after headroom)
                std::memcpy(buf.write_alloc(bytes_read), read_buf->data(), bytes_read);
                
                __android_log_print(ANDROID_LOG_INFO, "OpenVPN-CustomTUN",
                    "   Calling parent_.tun_recv() with %zu byte buffer (capacity=%zu with headroom)...", 
                    bytes_read, buf.capacity());
                __android_log_print(ANDROID_LOG_INFO, "OpenVPN-CustomTUN",
                    "   Buffer: size=%zu, offset=%zu, capacity=%zu", 
                    buf.size(), buf.offset(), buf.capacity());
                
                // CRITICAL: Call parent_.tun_recv() to feed packet into OpenVPN's pipeline
                // This is documented in TunClientParent interface (tunbase.hpp line 85)
                // This should encrypt the packet and send it via UDP to the server
                parent_.tun_recv(buf);
                
                __android_log_print(ANDROID_LOG_INFO, "OpenVPN-CustomTUN",
                    "✅ OUTBOUND: Successfully fed %zu byte packet to OpenVPN!", bytes_read);
                __android_log_print(ANDROID_LOG_INFO, "OpenVPN-CustomTUN",
                    "   OpenVPN should now encrypt and send this packet to server");
                __android_log_print(ANDROID_LOG_INFO, "OpenVPN-CustomTUN",
                    "   If server responds, tun_send() will be called with decrypted response");
                
                // Queue next read
                queue_read();
            } catch (const std::exception& e) {
                __android_log_print(ANDROID_LOG_ERROR, "OpenVPN-CustomTUN",
                    "❌ OUTBOUND: Exception in handle_read: %s", e.what());
                // Still queue next read to avoid getting stuck
                queue_read();
            } catch (...) {
                __android_log_print(ANDROID_LOG_ERROR, "OpenVPN-CustomTUN",
                    "❌ OUTBOUND: Unknown exception in handle_read");
                // Still queue next read to avoid getting stuck
                queue_read();
            }
        } else {
            if (error && error != openvpn_io::error::operation_aborted) {
                __android_log_print(ANDROID_LOG_ERROR, "OpenVPN-CustomTUN",
                    "❌ OUTBOUND: Read error from lib_fd: %s", error.message().c_str());
            }
        }
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
            
            // Extract prefix length (netmask)
            int prefix_len = 24;  // Default /24
            if (ip_opt->size() >= 3) {
                // Second parameter is netmask (e.g., "255.255.0.0")
                // For point-to-point, OpenVPN uses "ifconfig local remote"
                // So we'll just use default /24 for now
            }
            
            // Notify callback about IP assignment
            if (callback_ && !vpn_ip4_.empty()) {
                OPENVPN_LOG("Notifying callback: IP=" << vpn_ip4_ << "/" << prefix_len);
                callback_->on_ip_assigned(tunnel_id_, vpn_ip4_, prefix_len);
            }
        }
        
        // Extract DNS servers from dhcp-option
        std::vector<std::string> dns_servers;
        for (const auto& option : opt) {
            if (option.size() >= 3 && option.ref(0) == "dhcp-option") {
                std::string opt_type = option.get(1, 32);
                if (opt_type == "DNS") {
                    std::string dns = option.get(2, 256);
                    dns_servers.push_back(dns);
                    OPENVPN_LOG("TUN DNS: " << dns);
                }
            }
        }
        
        // Notify callback about DNS configuration
        if (callback_ && !dns_servers.empty()) {
            OPENVPN_LOG("Notifying callback: DNS servers count=" << dns_servers.size());
            callback_->on_dns_configured(tunnel_id_, dns_servers);
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
        halt_ = true;
        
        // Cancel and delete stream
        if (stream_) {
            try {
                stream_->cancel();
                delete stream_;
            } catch (...) {}
            stream_ = nullptr;
        }
        
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
    CustomTunCallback* callback_;  // Callback for IP/DNS notifications
    int app_fd_;      // Our application's FD
    int lib_fd_;      // OpenVPN 3's FD
    openvpn_io::posix::stream_descriptor* stream_;  // Asio stream for async reading from lib_fd
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
    
    CustomTunClientFactory(const std::string& tunnel_id, CustomTunCallback* callback = nullptr)
        : tunnel_id_(tunnel_id), callback_(callback) {
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
        auto client = new CustomTunClient(io_context, parent, tunnel_id_, callback_);
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
        __android_log_print(ANDROID_LOG_INFO, "OpenVPN-CustomTUN",
            "CustomTunClientFactory::getAppFd() - tun_client_=%p", (void*)tun_client_);
        
        if (tun_client_) {
            int fd = tun_client_->getAppFd();
            __android_log_print(ANDROID_LOG_INFO, "OpenVPN-CustomTUN",
                "CustomTunClient::getAppFd() returned: %d", fd);
            return fd;
        }
        __android_log_print(ANDROID_LOG_WARN, "OpenVPN-CustomTUN",
            "CustomTunClientFactory::getAppFd() - tun_client_ is NULL!");
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
    CustomTunCallback* callback_;  // Callback for IP/DNS notifications
    CustomTunClient* tun_client_ = nullptr;  // Non-owning pointer to get FDs
};

} // namespace openvpn

#endif // CUSTOM_TUN_CLIENT_H

