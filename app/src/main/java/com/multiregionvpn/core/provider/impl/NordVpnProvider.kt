package com.multiregionvpn.core.provider.impl

import com.multiregionvpn.core.provider.*
import com.multiregionvpn.data.database.ProviderCredentials
import com.multiregionvpn.data.database.VpnConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NordVPN provider implementation.
 * TODO: Implement authentication, server fetching, and config generation.
 */
@Singleton
class NordVpnProvider @Inject constructor() : VpnProvider {
    override val id: ProviderId = "nordvpn"
    override val displayName: String = "NordVPN"
    override val logoRes: Int = android.R.drawable.ic_dialog_info // TODO: Replace with actual logo

    override fun requiredCredentials(): List<CredentialField> {
        return listOf(
            CredentialField(
                key = "username",
                label = "Service Username",
                isPassword = false,
                hint = "Your NordVPN Service Credentials username"
            ),
            CredentialField(
                key = "password",
                label = "Service Password",
                isPassword = true,
                hint = "Your NordVPN Service Credentials password"
            )
        )
    }

    override fun authenticate(creds: ProviderCredentials): Flow<AuthenticationState> {
        // TODO: Implement NordVPN authentication
        return flowOf(AuthenticationState.Authenticated())
    }

    override suspend fun fetchServers(forceRefresh: Boolean): List<ProviderServer> {
        // TODO: Implement server fetching from NordVPN API
        return emptyList()
    }

    override suspend fun bestServer(target: RegionRequest): ProviderServer {
        // TODO: Implement best server selection with latency testing
        throw NotImplementedError("bestServer() not yet implemented")
    }

    override suspend fun generateConfig(target: ProviderTarget, creds: ProviderCredentials): VpnConfig {
        // TODO: Implement config generation
        throw NotImplementedError("generateConfig() not yet implemented")
    }
}


