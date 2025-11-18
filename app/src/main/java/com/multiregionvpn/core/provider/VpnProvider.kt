package com.multiregionvpn.core.provider

import com.multiregionvpn.data.database.ProviderCredentials
import com.multiregionvpn.data.database.VpnConfig
import kotlinx.coroutines.flow.Flow

/**
 * Core interface for VPN provider implementations.
 * Each provider (NordVPN, ProtonVPN, etc.) implements this interface
 * to provide provider-specific authentication, server discovery, and config generation.
 */
interface VpnProvider {
    /**
     * Unique identifier for this provider (e.g., "nordvpn", "protonvpn").
     */
    val id: ProviderId

    /**
     * User-friendly display name (e.g., "NordVPN", "ProtonVPN").
     */
    val displayName: String

    /**
     * Resource ID for the provider's logo drawable.
     */
    val logoRes: Int

    /**
     * Returns the list of credential fields required by this provider.
     * Used by the UI to generate credential entry forms.
     */
    fun requiredCredentials(): List<CredentialField>

    /**
     * Authenticates with the provider using the given credentials.
     * Returns a Flow that emits authentication state updates.
     */
    fun authenticate(creds: ProviderCredentials): Flow<AuthenticationState>

    /**
     * Fetches the list of available servers from the provider's API.
     * 
     * @param forceRefresh If true, bypasses cache and fetches fresh data.
     * @return List of available servers, sorted by region.
     */
    suspend fun fetchServers(forceRefresh: Boolean = false): List<ProviderServer>

    /**
     * Selects the best server for the given region request.
     * This should use latency testing and load information to select the optimal server.
     * 
     * @param target The region request specifying desired region and preferences.
     * @return The best available server for the request.
     * @throws NoSuchElementException if no server matches the request.
     */
    suspend fun bestServer(target: RegionRequest): ProviderServer

    /**
     * Generates a VpnConfig object ready to be used by VpnConnectionManager.
     * This method handles provider-specific logic (e.g., NordVPN's custom auth headers).
     * 
     * @param target The target server and protocol specification.
     * @param creds The user's credentials for this provider.
     * @return A VpnConfig object that can be used to establish a connection.
     */
    suspend fun generateConfig(target: ProviderTarget, creds: ProviderCredentials): VpnConfig
}


