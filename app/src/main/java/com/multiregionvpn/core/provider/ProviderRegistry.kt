package com.multiregionvpn.core.provider

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registry for all available VPN providers.
 * This is the single source of truth for provider discovery and access.
 */
@Singleton
class ProviderRegistry @Inject constructor(
    private val providers: Set<@JvmSuppressWildcards VpnProvider>
) {
    private val providersById = providers.associateBy { it.id }

    init {
        Log.d(TAG, "ProviderRegistry initialized with ${providers.size} provider(s): ${providers.map { it.id }}")
    }

    /**
     * Gets a provider by its ID.
     * 
     * @param id The provider ID (e.g., "nordvpn").
     * @return The provider, or null if not found.
     */
    fun getProvider(id: ProviderId): VpnProvider? {
        return providersById[id]
    }

    /**
     * Gets a provider by its ID, throwing if not found.
     * 
     * @param id The provider ID (e.g., "nordvpn").
     * @return The provider.
     * @throws IllegalArgumentException if the provider is not found.
     */
    fun requireProvider(id: ProviderId): VpnProvider {
        return providersById[id] ?: throw IllegalArgumentException("Provider not found: $id")
    }

    /**
     * Returns all registered providers.
     */
    fun getAllProviders(): List<VpnProvider> {
        return providers.toList()
    }

    /**
     * Checks if a provider is registered.
     */
    fun hasProvider(id: ProviderId): Boolean {
        return providersById.containsKey(id)
    }

    companion object {
        private const val TAG = "ProviderRegistry"
    }
}


