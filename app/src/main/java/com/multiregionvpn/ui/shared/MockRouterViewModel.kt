package com.multiregionvpn.ui.shared

import android.graphics.drawable.ColorDrawable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Mock implementation of RouterViewModel for UI development and testing
 * 
 * Provides realistic fake data without requiring backend services.
 * Useful for:
 * - Compose UI previews
 * - UI testing
 * - Development without running VPN services
 */
class MockRouterViewModel : RouterViewModel() {
    
    // ═══════════════════════════════════════════════════════════════════════════
    // STATE (Observable by UI)
    // ═══════════════════════════════════════════════════════════════════════════
    
    private val _vpnStatus = MutableStateFlow(VpnStatus.DISCONNECTED)
    override val vpnStatus: StateFlow<VpnStatus> = _vpnStatus.asStateFlow()
    
    private val _allServerGroups = MutableStateFlow(createMockServerGroups())
    override val allServerGroups: StateFlow<List<ServerGroup>> = _allServerGroups.asStateFlow()
    
    private val _allAppRules = MutableStateFlow(createMockAppRules())
    override val allAppRules: StateFlow<List<AppRule>> = _allAppRules.asStateFlow()
    
    private val _allInstalledApps = MutableStateFlow(createMockInstalledApps())
    override val allInstalledApps: StateFlow<List<AppRule>> = _allInstalledApps.asStateFlow()
    
    private val _selectedServerGroup = MutableStateFlow<ServerGroup?>(null)
    override val selectedServerGroup: StateFlow<ServerGroup?> = _selectedServerGroup.asStateFlow()
    
    private val _liveStats = MutableStateFlow(VpnStats())
    override val liveStats: StateFlow<VpnStats> = _liveStats.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    override val error: StateFlow<String?> = _error.asStateFlow()
    
    // ═══════════════════════════════════════════════════════════════════════════
    // MOCK DATA CREATION
    // ═══════════════════════════════════════════════════════════════════════════
    
    companion object {
        fun createMockServerGroups(): List<ServerGroup> = listOf(
            ServerGroup(
                id = "group_uk",
                name = "UK Streaming",
                region = "uk",
                serverCount = 42,
                isActive = true
            ),
            ServerGroup(
                id = "group_us",
                name = "US General",
                region = "us",
                serverCount = 156,
                isActive = true
            ),
            ServerGroup(
                id = "group_fr",
                name = "France",
                region = "fr",
                serverCount = 28,
                isActive = false
            ),
            ServerGroup(
                id = "group_de",
                name = "Germany",
                region = "de",
                serverCount = 64,
                isActive = false
            ),
            ServerGroup(
                id = "group_jp",
                name = "Japan",
                region = "jp",
                serverCount = 32,
                isActive = false
            ),
            ServerGroup(
                id = "group_au",
                name = "Australia",
                region = "au",
                serverCount = 18,
                isActive = true
            )
        )
        
        fun createMockAppRules(): List<AppRule> = listOf(
            AppRule(
                packageName = "com.bbc.iplayer",
                appName = "BBC iPlayer",
                icon = ColorDrawable(0xFFE12C26.toInt()), // BBC red
                routedGroupId = "group_uk"
            ),
            AppRule(
                packageName = "com.channel4.android",
                appName = "All 4",
                icon = ColorDrawable(0xFF00B9F2.toInt()), // C4 blue
                routedGroupId = "group_uk"
            ),
            AppRule(
                packageName = "com.netflix.mediaclient",
                appName = "Netflix",
                icon = ColorDrawable(0xFFE50914.toInt()), // Netflix red
                routedGroupId = "group_us"
            ),
            AppRule(
                packageName = "com.disney.disneyplus",
                appName = "Disney+",
                icon = ColorDrawable(0xFF0072D2.toInt()), // Disney blue
                routedGroupId = "group_us"
            ),
            AppRule(
                packageName = "com.google.android.youtube",
                appName = "YouTube",
                icon = ColorDrawable(0xFFFF0000.toInt()), // YouTube red
                routedGroupId = null // Direct internet (bypass)
            ),
            AppRule(
                packageName = "com.spotify.music",
                appName = "Spotify",
                icon = ColorDrawable(0xFF1DB954.toInt()), // Spotify green
                routedGroupId = null // Direct internet
            ),
            AppRule(
                packageName = "au.com.abc.iview",
                appName = "ABC iview",
                icon = ColorDrawable(0xFF000000.toInt()), // ABC black
                routedGroupId = "group_au"
            ),
            AppRule(
                packageName = "com.facebook.katana",
                appName = "Facebook",
                icon = ColorDrawable(0xFF1877F2.toInt()), // Facebook blue
                routedGroupId = "block" // Blocked
            )
        )
        
        /**
         * Creates mock list of ALL installed apps (including those without routing rules)
         * This is what the UI should display to users - mobile and TV
         */
        fun createMockInstalledApps(): List<AppRule> {
            // Return the same as createMockAppRules for now
            // In production, this would query PackageManager for ALL apps
            return createMockAppRules()
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // EVENTS (Simulated behavior)
    // ═══════════════════════════════════════════════════════════════════════════
    
    override fun onToggleVpn(enable: Boolean) {
        if (enable) {
            // Simulate connection process
            _vpnStatus.value = VpnStatus.CONNECTING
            
            // Simulate async connection (would be real in production)
            CoroutineScope(Dispatchers.Default).launch {
                delay(2000) // 2 second connection time
                _vpnStatus.value = VpnStatus.CONNECTED
                
                // Start simulating stats
                simulateLiveStats()
            }
        } else {
            _vpnStatus.value = VpnStatus.DISCONNECTED
            _liveStats.value = VpnStats() // Reset stats
        }
    }
    
    override fun onAppRuleChange(app: AppRule, newGroupId: String?) {
        // Update the app rule in the list
        val currentRules = _allAppRules.value.toMutableList()
        val index = currentRules.indexOfFirst { it.packageName == app.packageName }
        
        if (index != -1) {
            currentRules[index] = app.copy(routedGroupId = newGroupId)
            _allAppRules.value = currentRules
        }
        
        // Update server group active status
        updateServerGroupActiveStatus()
    }
    
    override fun onServerGroupSelected(group: ServerGroup) {
        _selectedServerGroup.value = group
    }
    
    override fun onAddServerGroup() {
        // Add a new mock server group
        val newId = "group_new_${System.currentTimeMillis()}"
        val newGroup = ServerGroup(
            id = newId,
            name = "New Server Group",
            region = "xx",
            serverCount = 10,
            isActive = false
        )
        
        val currentGroups = _allServerGroups.value.toMutableList()
        currentGroups.add(newGroup)
        _allServerGroups.value = currentGroups
    }
    
    override fun onRemoveServerGroup(group: ServerGroup) {
        // Remove the server group
        val currentGroups = _allServerGroups.value.toMutableList()
        currentGroups.removeAll { it.id == group.id }
        _allServerGroups.value = currentGroups
        
        // Update app rules that were using this group (set to bypass)
        val currentRules = _allAppRules.value.map { rule ->
            if (rule.routedGroupId == group.id) {
                rule.copy(routedGroupId = null)
            } else {
                rule
            }
        }
        _allAppRules.value = currentRules
        
        // Clear selection if this was selected
        if (_selectedServerGroup.value?.id == group.id) {
            _selectedServerGroup.value = null
        }
    }

    override fun onClearError() {
        _error.value = null
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════════
    
    private fun updateServerGroupActiveStatus() {
        val currentGroups = _allServerGroups.value.map { group ->
            val isActive = _allAppRules.value.any { it.routedGroupId == group.id }
            group.copy(isActive = isActive)
        }
        _allServerGroups.value = currentGroups
    }
    
    private suspend fun simulateLiveStats() {
        var bytesSent = 0L
        var bytesReceived = 0L
        var connectionTime = 0L
        
        while (_vpnStatus.value == VpnStatus.CONNECTED) {
            // Simulate traffic (random increase)
            bytesSent += (100..1000).random().toLong()
            bytesReceived += (500..5000).random().toLong()
            connectionTime++
            
            val activeCount = _allServerGroups.value.count { it.isActive }
            
            _liveStats.value = VpnStats(
                bytesSent = bytesSent,
                bytesReceived = bytesReceived,
                connectionTimeSeconds = connectionTime,
                activeConnections = activeCount
            )
            
            delay(1000)
        }
    }
}

