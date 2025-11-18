package com.multiregionvpn.core.provider

/**
 * Represents a VPN server from a provider's catalog.
 */
data class ProviderServer(
    val hostname: String, // e.g., "uk1860.nordvpn.com"
    val ipAddress: String?, // e.g., "185.230.63.107"
    val regionCode: String, // e.g., "UK", "FR", "US"
    val countryCode: String, // ISO 3166-1 alpha-2, e.g., "GB", "FR", "US"
    val supportedProtocols: List<SupportedProtocol>, // e.g., [OPENVPN_UDP, WIREGUARD]
    val load: Int? = null, // Server load percentage (0-100), null if unknown
    val features: List<String> = emptyList(), // e.g., ["P2P", "Obfuscated"]
    val latencyMs: Long? = null // Measured latency in milliseconds, null if not measured
)

enum class SupportedProtocol {
    OPENVPN_UDP,
    OPENVPN_TCP,
    WIREGUARD,
    IKEV2
}


