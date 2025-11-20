package com.multiregionvpn.core

import android.util.Log
import com.multiregionvpn.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe in-memory cache for packet routing rules.
 * Eliminates blocking database queries from the packet processing hot path.
 * 
 * The cache maps package names to tunnel IDs, allowing O(1) lookups during
 * packet routing without blocking on database I/O.
 */
class RuleCache(
    private val settingsRepository: SettingsRepository
) {
    private val packageToTunnelId = ConcurrentHashMap<String, String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    init {
        // Initialize cache from database and subscribe to updates
        scope.launch {
            // Initial load
            refreshCache()
            
            // Subscribe to rule changes
            settingsRepository.getAllAppRules()
                .onEach { rules ->
                    Log.d(TAG, "Rule cache update: ${rules.size} rules")
                    updateCache(rules)
                }
                .launchIn(this)
        }
    }
    
    /**
     * Get tunnel ID for a package name. Returns null if no rule exists.
     * This is a non-blocking, in-memory lookup - safe to call from packet processing thread.
     */
    fun getTunnelIdForPackage(packageName: String): String? {
        return packageToTunnelId[packageName]
    }
    
    /**
     * Refresh the entire cache from database.
     * Called on initialization and can be called manually if needed.
     */
    private suspend fun refreshCache() {
        try {
            val rules = settingsRepository.appRuleDao.getAllRulesList()
            Log.i(TAG, "Initializing rule cache with ${rules.size} rules")
            
            packageToTunnelId.clear()
            
            for (rule in rules) {
                if (rule.vpnConfigId != null) {
                    val vpnConfig = settingsRepository.getVpnConfigById(rule.vpnConfigId!!)
                    if (vpnConfig != null) {
                        val tunnelId = "${vpnConfig.templateId}_${vpnConfig.regionId}"
                        packageToTunnelId[rule.packageName] = tunnelId
                        Log.d(TAG, "Cached: ${rule.packageName} → $tunnelId")
                    }
                }
            }
            
            Log.i(TAG, "Rule cache initialized: ${packageToTunnelId.size} mappings")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh rule cache", e)
        }
    }
    
    /**
     * Update cache with new rules from Flow emission.
     */
    private suspend fun updateCache(rules: List<com.multiregionvpn.data.database.AppRule>) {
        try {
            val newMappings = ConcurrentHashMap<String, String>()
            
            for (rule in rules) {
                if (rule.vpnConfigId != null) {
                    val vpnConfig = settingsRepository.getVpnConfigById(rule.vpnConfigId!!)
                    if (vpnConfig != null) {
                        val tunnelId = "${vpnConfig.templateId}_${vpnConfig.regionId}"
                        newMappings[rule.packageName] = tunnelId
                    }
                }
            }
            
            // Replace entire cache atomically
            packageToTunnelId.clear()
            packageToTunnelId.putAll(newMappings)
            
            Log.i(TAG, "Rule cache updated: ${packageToTunnelId.size} mappings")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update rule cache", e)
        }
    }
    
    /**
     * Clear the cache. Useful for testing and cleanup.
     */
    fun clear() {
        packageToTunnelId.clear()
        Log.i(TAG, "Rule cache cleared")
    }
    
    companion object {
        private const val TAG = "RuleCache"
    }
}
