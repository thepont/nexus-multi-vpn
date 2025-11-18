package com.multiregionvpn.core.provider

import com.multiregionvpn.data.database.ProviderCredentials

/**
 * Target specification for generating a VPN config.
 */
data class ProviderTarget(
    val server: ProviderServer,
    val protocol: SupportedProtocol,
    val credentials: ProviderCredentials
)


