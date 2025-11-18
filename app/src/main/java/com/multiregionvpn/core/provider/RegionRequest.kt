package com.multiregionvpn.core.provider

/**
 * Request for a connection to a specific region.
 */
data class RegionRequest(
    val regionCode: String, // e.g., "UK", "FR", "US"
    val preferredProtocol: SupportedProtocol? = null, // Preferred protocol, null = any
    val requireFeatures: List<String> = emptyList() // Required features, e.g., ["P2P"]
)


