#ifndef EXTERNAL_TUN_FACTORY_H
#define EXTERNAL_TUN_FACTORY_H

#include <openvpn/tun/extern/fw.hpp>
#include <openvpn/tun/extern/config.hpp>
#include <openvpn/common/rc.hpp>
#include <openvpn/common/options.hpp>
#include <android/log.h>
#include <memory>
#include <string>
#include "custom_tun_client.h"

// Use Android logging instead of OPENVPN_LOG
#define EXTERNAL_TUN_LOG(...) __android_log_print(ANDROID_LOG_INFO, "ExternalTUN", __VA_ARGS__)

namespace openvpn {

/**
 * Custom External TUN Factory Implementation
 * 
 * Provides custom TUN implementation to OpenVPN 3 via the ExternalTun::Factory interface.
 * This allows the application to control packet I/O for multi-tunnel routing.
 * 
 * Architecture Flow:
 * 1. OpenVPN 3 calls new_tun_factory()
 * 2. Returns CustomTunClientFactory
 * 3. OpenVPN 3 calls factory->new_tun_client_obj()
 * 4. Returns CustomTunClient
 * 5. OpenVPN 3 calls client->tun_start()
 * 6. CustomTunClient creates socketpair for bidirectional communication
 * 7. OpenVPN 3 polls lib_fd in its event loop
 * 8. Application uses app_fd for packet I/O
 */
class CustomExternalTunFactory : public ExternalTun::Factory, public RC<thread_unsafe_refcount> {
public:
    typedef RCPtr<CustomExternalTunFactory> Ptr;
    
    CustomExternalTunFactory(const std::string& tunnel_id) 
        : tunnel_id_(tunnel_id) {
        EXTERNAL_TUN_LOG("CustomExternalTunFactory created for tunnel: %s", tunnel_id_.c_str());
    }
    
    virtual ~CustomExternalTunFactory() {
        EXTERNAL_TUN_LOG("CustomExternalTunFactory destroyed for tunnel: %s", tunnel_id_.c_str());
    }
    
    /**
     * Called by OpenVPN 3 Core to create a TunClientFactory.
     * 
     * This is the main entry point for external TUN factory.
     * We return a CustomTunClientFactory which will create CustomTunClient.
     * 
     * @param conf External TUN configuration
     * @param opt Option list from OpenVPN config
     * @return TunClientFactory that creates our custom TUN clients
     */
    virtual TunClientFactory* new_tun_factory(const ExternalTun::Config& conf, 
                                               const OptionList& opt) override {
        OPENVPN_LOG("CustomExternalTunFactory::new_tun_factory() for tunnel: " << tunnel_id_);
        
        // Create and return CustomTunClientFactory
        tun_client_factory_ = new CustomTunClientFactory(tunnel_id_);
        
        return tun_client_factory_.get();
    }
    
    /**
     * Get the app FD for packet I/O
     * Call this after connection is established
     */
    int getAppFd() const {
        if (tun_client_factory_) {
            return tun_client_factory_->getAppFd();
        }
        return -1;
    }
    
    /**
     * Get the lib FD (used by OpenVPN 3)
     */
    int getLibFd() const {
        if (tun_client_factory_) {
            return tun_client_factory_->getLibFd();
        }
        return -1;
    }
    
private:
    std::string tunnel_id_;
    CustomTunClientFactory::Ptr tun_client_factory_;
};

} // namespace openvpn

#endif // EXTERNAL_TUN_FACTORY_H
