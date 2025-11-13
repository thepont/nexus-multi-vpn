#ifndef ANDROID_TUN_BUILDER_H
#define ANDROID_TUN_BUILDER_H

#include <string>
#include <jni.h>
#include <openvpn/tun/builder/base.hpp>

/**
 * Android TunBuilder implementation that interfaces with Android's VpnService.Builder
 * via JNI. This bridges OpenVPN 3's TunBuilderBase interface to Android's VPN API.
 */
class AndroidTunBuilder : public openvpn::TunBuilderBase {
public:
    AndroidTunBuilder(JNIEnv* env, jobject vpnServiceBuilder);
    virtual ~AndroidTunBuilder();
    
    // TunBuilderBase interface implementation
    virtual bool tun_builder_new() override;
    virtual bool tun_builder_set_layer(int layer) override;
    virtual bool tun_builder_set_remote_address(const std::string &address, bool ipv6) override;
    virtual bool tun_builder_add_address(const std::string &address,
                                         int prefix_length,
                                         const std::string &gateway,
                                         bool ipv6,
                                         bool net30) override;
    virtual bool tun_builder_reroute_gw(bool ipv4, bool ipv6, unsigned int flags) override;
    virtual bool tun_builder_add_route(const std::string &address,
                                       int prefix_length,
                                       int metric,
                                       bool ipv6) override;
    virtual bool tun_builder_exclude_route(const std::string &address,
                                           int prefix_length,
                                           int metric,
                                           bool ipv6) override;
    virtual bool tun_builder_set_dns_options(const openvpn::DnsOptions &dns) override;
    virtual bool tun_builder_set_mtu(int mtu) override;
    virtual bool tun_builder_set_session_name(const std::string &name) override;
    virtual bool tun_builder_add_proxy_bypass(const std::string &bypass_host) override;
    virtual bool tun_builder_set_proxy_auto_config_url(const std::string &url) override;
    virtual bool tun_builder_set_proxy_http(const std::string &host, int port) override;
    virtual bool tun_builder_set_proxy_https(const std::string &host, int port) override;
    virtual bool tun_builder_add_wins_server(const std::string &address) override;
    virtual bool tun_builder_set_allow_family(int af, bool allow) override;
    virtual bool tun_builder_set_allow_local_dns(bool allow) override;
    virtual bool tun_builder_establish() override;
    virtual int tun_builder_establish_lite() override;
    
    // Get the established file descriptor (for packet I/O)
    int getTunFileDescriptor() const { return tun_fd_; }
    
private:
    JNIEnv* env_;
    jobject vpnServiceBuilder_;  // Android VpnService.Builder instance
    int tun_fd_;  // File descriptor of established TUN interface
    
    // Helper methods to call Java methods via JNI
    bool callVpnBuilderMethod(const char* methodName, const char* signature, ...);
    bool addAddressToBuilder(const std::string &address, int prefix_length);
    bool addRouteToBuilder(const std::string &address, int prefix_length);
    bool addDnsServerToBuilder(const std::string &dns);
};

#endif // ANDROID_TUN_BUILDER_H


