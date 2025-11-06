#ifndef EXTERNAL_TUN_FACTORY_H
#define EXTERNAL_TUN_FACTORY_H

#include <openvpn/tun/extern/fw.hpp>
#include <openvpn/tun/extern/config.hpp>
#include <openvpn/common/rc.hpp>
#include <openvpn/common/options.hpp>
#include <openvpn/log/log.hpp>
#include <memory>
#include <string>
#include "custom_tun_client.h"

namespace openvpn {

/**
 * Custom External TUN Factory Implementation
 * 
 * This is the CORRECT way to provide custom TUN implementations to OpenVPN 3 Core.
 * Instead of hacking TunBuilderBase, we implement ExternalTun::Factory which is
 * explicitly designed for this purpose.
 * 
 * Architecture Flow:
 * 1. OpenVPN 3 calls new_tun_factory()
 * 2. We return CustomTunClientFactory
 * 3. OpenVPN 3 calls factory->new_tun_client_obj()
 * 4. CustomTunClientFactory returns CustomTunClient
 * 5. OpenVPN 3 calls client->tun_start()
 * 6. CustomTunClient creates socketpair
 * 7. OpenVPN 3 polls lib_fd in its event loop ✅
 * 8. Our app uses app_fd for packet I/O ✅
 * 
 * This gives us full control over packet routing!
 */
class CustomExternalTunFactory : public ExternalTun::Factory {
public:
    typedef RCPtr<CustomExternalTunFactory> Ptr;
    
    CustomExternalTunFactory(const std::string& tunnel_id) 
        : tunnel_id_(tunnel_id) {
        OPENVPN_LOG("CustomExternalTunFactory created for tunnel: " << tunnel_id_);
    }
    
    virtual ~CustomExternalTunFactory() {
        OPENVPN_LOG("CustomExternalTunFactory destroyed for tunnel: " << tunnel_id_);
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
        OPENVPN_LOG("═══════════════════════════════════════════════════════");
        OPENVPN_LOG("🔧 CustomExternalTunFactory::new_tun_factory()");
        OPENVPN_LOG("   Creating TunClientFactory for tunnel: " << tunnel_id_);
        OPENVPN_LOG("═══════════════════════════════════════════════════════");
        
        // Create and return CustomTunClientFactory
        // This factory will create CustomTunClient instances
        tun_client_factory_ = new CustomTunClientFactory(tunnel_id_);
        
        OPENVPN_LOG("✅ CustomTunClientFactory created");
        OPENVPN_LOG("   OpenVPN 3 will call factory->new_tun_client_obj() next");
        OPENVPN_LOG("═══════════════════════════════════════════════════════");
        
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
