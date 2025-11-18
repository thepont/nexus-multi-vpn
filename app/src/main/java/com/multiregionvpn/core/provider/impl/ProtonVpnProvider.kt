package com.multiregionvpn.core.provider.impl

import com.multiregionvpn.core.provider.*
import com.multiregionvpn.data.database.ProviderCredentials
import com.multiregionvpn.data.database.VpnConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ProtonVPN provider implementation.
 * TODO: Implement authentication, server fetching, and config generation.
 */
@Singleton
class ProtonVpnProvider @Inject constructor() : VpnProvider {
    override val id: ProviderId = "protonvpn"
    override val displayName: String = "ProtonVPN"
    override val logoRes: Int = android.R.drawable.ic_dialog_info // TODO: Replace with actual logo

    override fun requiredCredentials(): List<CredentialField> {
        return listOf(
            CredentialField(
                key = "username",
                label = "ProtonVPN Username",
                isPassword = false,
                hint = "Your ProtonVPN account username"
            ),
            CredentialField(
                key = "password",
                label = "ProtonVPN Password",
                isPassword = true,
                hint = "Your ProtonVPN account password"
            )
        )
    }

    override fun authenticate(creds: ProviderCredentials): Flow<AuthenticationState> {
        // TODO: Implement ProtonVPN authentication
        return flowOf(AuthenticationState.Authenticated())
    }

    override suspend fun fetchServers(forceRefresh: Boolean): List<ProviderServer> {
        // TODO: Implement server fetching from ProtonVPN API
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


