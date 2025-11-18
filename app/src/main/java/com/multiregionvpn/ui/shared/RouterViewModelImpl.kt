package com.multiregionvpn.ui.shared

import android.app.Application
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.multiregionvpn.core.ConnectionTracker
import com.multiregionvpn.core.VpnEngineService
import com.multiregionvpn.core.VpnServiceStateTracker
import com.multiregionvpn.core.VpnConnectionManager
import com.multiregionvpn.data.GeoBlockedApps
import com.multiregionvpn.data.database.AppRule as DbAppRule
import com.multiregionvpn.data.database.VpnConfig
import com.multiregionvpn.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Socket
import java.net.SocketTimeoutException
import javax.inject.Inject

/**
 * Production implementation of RouterViewModel
 * 
 * Bridges the shared UI models to the existing VPN backend:
 * - VpnEngineService (VPN control)
 * - ConnectionTracker (live stats)
 * - SettingsRepository (persistence)
 * 
 * This replaces all mock data with real database/backend integration.
 */
@HiltViewModel
class RouterViewModelImpl @Inject constructor(
    private val application: Application,
    private val settingsRepository: SettingsRepository
) : RouterViewModel() {
    
    companion object {
        private const val TAG = "RouterViewModelImpl"
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // STATE (Observable by UI)
    // ═══════════════════════════════════════════════════════════════════════════
    
    private val _vpnStatus = MutableStateFlow(VpnStatus.DISCONNECTED)
    override val vpnStatus: StateFlow<VpnStatus> = _vpnStatus.asStateFlow()
    
    private val _allServerGroups = MutableStateFlow<List<ServerGroup>>(emptyList())
    override val allServerGroups: StateFlow<List<ServerGroup>> = _allServerGroups.asStateFlow()
    
    private val _allAppRules = MutableStateFlow<List<AppRule>>(emptyList())
    override val allAppRules: StateFlow<List<AppRule>> = _allAppRules.asStateFlow()
    
    private val _allInstalledApps = MutableStateFlow<List<AppRule>>(emptyList())
    override val allInstalledApps: StateFlow<List<AppRule>> = _allInstalledApps.asStateFlow()
    
    private val _selectedServerGroup = MutableStateFlow<ServerGroup?>(null)
    override val selectedServerGroup: StateFlow<ServerGroup?> = _selectedServerGroup.asStateFlow()
    
    private val _liveStats = MutableStateFlow(VpnStats())
    override val liveStats: StateFlow<VpnStats> = _liveStats.asStateFlow()
    
    // Error handler for all coroutines
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Error in RouterViewModel", throwable)
        _vpnStatus.value = VpnStatus.ERROR
    }
    
    init {
        Log.i(TAG, "═══════════════════════════════════════════════════════")
        Log.i(TAG, "📱 RouterViewModelImpl initializing...")
        Log.i(TAG, "═══════════════════════════════════════════════════════")
        
        // Initialize state from backend
        viewModelScope.launch(exceptionHandler) { loadServerGroups() }
        viewModelScope.launch(exceptionHandler) { loadAppRules() }
        viewModelScope.launch(exceptionHandler) { loadInstalledApps() }
        observeVpnStatus()
        observeLiveStats()
        
        // Start monitoring tunnel connection status and latency
        viewModelScope.launch(exceptionHandler) { monitorTunnelStatus() }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // BACKEND INTEGRATION (Private)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Load VPN configs from repository and convert to ServerGroups.
     * 
     * Groups VPN configs by region and creates a ServerGroup for each region.
     * For example, if user has 3 UK servers and 2 US servers, this creates:
     * - ServerGroup("uk", "United Kingdom", 3 servers, isActive=true if any UK apps route through it)
     * - ServerGroup("us", "United States", 2 servers, isActive=true if any US apps route through it)
     */
    private suspend fun loadServerGroups() {
        try {
            Log.d(TAG, "📂 Loading server groups from repository...")
            
            // Collect VPN configs from database
            settingsRepository.getAllVpnConfigs().collect { vpnConfigs ->
                Log.d(TAG, "   Found ${vpnConfigs.size} VPN configs in database")
                
                // Group by regionId
                val groupedByRegion = vpnConfigs.groupBy { it.regionId }
                
                // Get all app rules to determine which groups are active
                val appRules = settingsRepository.appRuleDao.getAllRulesList()
                val activeVpnConfigIds = appRules.mapNotNull { it.vpnConfigId }.toSet()
                
                // Convert to ServerGroups
                val serverGroups = groupedByRegion.map { (regionId, configs) ->
                    // Check if any config in this region is actively used
                    val isActive = configs.any { config -> activeVpnConfigIds.contains(config.id) }
                    
                    // Get human-readable region name
                    val regionName = getRegionDisplayName(regionId)
                    
                    // Check connection status and latency (will be updated by monitorTunnelStatus)
                    val connectionManager = try {
                        VpnConnectionManager.getInstance()
                    } catch (e: IllegalStateException) {
                        null
                    }
                    
                    // Find first connected tunnel in this region
                    var isConnected = false
                    var latencyMs: Int? = null
                    
                    if (connectionManager != null) {
                        for (config in configs) {
                            val tunnelId = "${config.templateId}_${config.regionId}"
                            if (connectionManager.isTunnelConnected(tunnelId)) {
                                isConnected = true
                                // Measure latency for the first connected tunnel
                                if (latencyMs == null && config.serverHostname.isNotEmpty()) {
                                    latencyMs = measureLatency(config.serverHostname)
                                }
                                break
                            }
                        }
                    }
                    
                    ServerGroup(
                        id = regionId,
                        name = regionName,
                        region = regionId,
                        serverCount = configs.size,
                        isActive = isActive,
                        isConnected = isConnected,
                        latencyMs = latencyMs
                    )
                }
                
                _allServerGroups.value = serverGroups
                Log.i(TAG, "✅ Loaded ${serverGroups.size} server groups (${serverGroups.count { it.isActive }} active)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error loading server groups", e)
            _allServerGroups.value = emptyList()
        }
    }
    
    /**
     * Load app rules from repository and convert to UI model.
     * 
     * Fetches installed apps and their routing rules from the database.
     * For each installed app:
     * - Loads its icon from PackageManager
     * - Looks up its routing rule (if any)
     * - Creates AppRule(packageName, appName, icon, routedGroupId)
     */
    private suspend fun loadAppRules() {
        try {
            Log.d(TAG, "📱 Loading app rules from repository...")
            
            val packageManager = application.packageManager
            
            // Collect app rules from database
            settingsRepository.getAllAppRules().collect { dbAppRules ->
                Log.d(TAG, "   Found ${dbAppRules.size} app rules in database")
                
                // Get list of all installed apps (excluding system apps)
                val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                    .filter { (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 }
                
                Log.d(TAG, "   Found ${installedApps.size} installed user apps")
                
                // Create AppRule for each app that has a rule OR is a known geo-blocked app
                val appRules = mutableListOf<AppRule>()
                
                // First, add all apps that have explicit rules
                for (dbRule in dbAppRules) {
                    try {
                        val appInfo = packageManager.getApplicationInfo(dbRule.packageName, 0)
                        val appName = packageManager.getApplicationLabel(appInfo).toString()
                        val icon: Drawable? = try {
                            packageManager.getApplicationIcon(dbRule.packageName)
                        } catch (e: Exception) {
                            null
                        }
                        
                        // Map vpnConfigId to regionId (groupId)
                        val groupId = if (dbRule.vpnConfigId != null) {
                            val vpnConfig = settingsRepository.getVpnConfigById(dbRule.vpnConfigId!!)
                            vpnConfig?.regionId  // Use regionId as groupId
                        } else {
                            null  // Bypass VPN
                        }
                        
                        appRules.add(
                            AppRule(
                                packageName = dbRule.packageName,
                                appName = appName,
                                icon = icon,
                                routedGroupId = groupId
                            )
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "   ⚠️  App ${dbRule.packageName} not installed, skipping")
                    }
                }
                
                _allAppRules.value = appRules
                Log.i(TAG, "✅ Loaded ${appRules.size} app rules")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error loading app rules", e)
            _allAppRules.value = emptyList()
        }
    }
    
    /**
     * Load ALL installed apps (including those without routing rules).
     * 
     * This is what the UI should display to users - both mobile and TV.
     * For each installed app:
     * - Loads its icon from PackageManager
     * - Looks up its routing rule (if any) from the database
     * - Creates AppRule with current rule or null (bypass)
     */
    private suspend fun loadInstalledApps() {
        try {
            Log.d(TAG, "📱 Loading ALL installed apps...")
            
            val pm = application.packageManager
            val allApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            Log.d(TAG, "   PackageManager returned ${allApps.size} apps (system + user)")
            
            val filteredApps = allApps.filter { appInfo ->
                val pkg = appInfo.packageName
                
                if (pkg == application.packageName) return@filter false
                
                // Allow all user-installed apps
                if ((appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0) return@filter true
                
                // Allow updated system apps
                if ((appInfo.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) return@filter true
                
                // Allow core Google / store apps so routing works for them
                pkg.startsWith("com.android.vending") || // Play Store
                pkg.startsWith("com.google.android.apps.") ||
                pkg.startsWith("com.google.android.youtube") ||
                pkg.startsWith("com.google.android.tv") ||
                pkg.startsWith("com.android.chrome") ||
                pkg.startsWith("com.amazon") // Fire TV sideloaded stores, etc.
            }
            
            Log.d(TAG, "   Filtered to ${filteredApps.size} apps after removing pure system packages")
            
            val dbAppRules = settingsRepository.getAllAppRules().first()
            val rulesMap = dbAppRules.associateBy { it.packageName }
            
            val appRules = filteredApps.map { appInfo ->
                val packageName = appInfo.packageName
                val appName = pm.getApplicationLabel(appInfo).toString()
                val icon: Drawable? = try {
                    pm.getApplicationIcon(packageName)
                } catch (e: Exception) {
                    null
                }
                
                val dbRule = rulesMap[packageName]
                val groupId = if (dbRule?.vpnConfigId != null) {
                    val vpnConfig = settingsRepository.getVpnConfigById(dbRule.vpnConfigId!!)
                    vpnConfig?.regionId
                } else {
                    null
                }
                
                AppRule(
                    packageName = packageName,
                    appName = appName,
                    icon = icon,
                    routedGroupId = groupId
                )
            }.sortedBy { it.appName.lowercase() }
            
            _allInstalledApps.value = appRules
            Log.i(TAG, "✅ Loaded ${appRules.size} installed apps (rules exist for ${appRules.count { it.routedGroupId != null }})")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error loading installed apps", e)
            _allInstalledApps.value = emptyList()
        }
    }
    
    /**
     * Get human-readable region name from region code.
     * 
     * Examples:
     * - "uk" → "United Kingdom"
     * - "us" → "United States"
     * - "fr" → "France"
     */
    private fun getRegionDisplayName(regionId: String): String {
        return when (regionId.lowercase()) {
            "uk" -> "United Kingdom"
            "us" -> "United States"
            "fr" -> "France"
            "de" -> "Germany"
            "jp" -> "Japan"
            "au" -> "Australia"
            "ca" -> "Canada"
            "nl" -> "Netherlands"
            "se" -> "Sweden"
            "es" -> "Spain"
            "it" -> "Italy"
            "br" -> "Brazil"
            "in" -> "India"
            "sg" -> "Singapore"
            "hk" -> "Hong Kong"
            else -> regionId.uppercase()  // Fallback to uppercase code
        }
    }
    
    /**
     * Monitor VPN engine service status.
     * 
     * Polls VpnEngineService.isRunning() every second to update UI status.
     * In a future refactor, this could be replaced with a StateFlow from the service.
     */
    private fun observeVpnStatus() {
        viewModelScope.launch(exceptionHandler) {
            VpnServiceStateTracker.status.collect { status ->
                if (_vpnStatus.value != status) {
                    Log.d(TAG, "   VPN status update: ${_vpnStatus.value} → $status")
                    _vpnStatus.value = status
                }
            }
        }
    }
    
    /**
     * Monitor live VPN statistics from ConnectionTracker.
     * 
     * Polls connection stats every second and updates the UI.
     * Stats include: bytes sent/received, connection time, active connections.
     * 
     * NOTE: ConnectionTracker is instantiated by VpnEngineService, so it's only
     * available when the service is running. We access it via a static reference.
     */
    private fun observeLiveStats() {
        viewModelScope.launch(exceptionHandler) {
            VpnServiceStateTracker.stats.collect { stats ->
                _liveStats.value = stats
            }
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // EVENTS (Triggered by UI)
    // ═══════════════════════════════════════════════════════════════════════════
    
    override fun onToggleVpn(enable: Boolean) {
        viewModelScope.launch(exceptionHandler) {
            Log.i(TAG, "═══════════════════════════════════════════════════════")
            Log.i(TAG, "🎛️  User toggled VPN: enable=$enable")
            Log.i(TAG, "═══════════════════════════════════════════════════════")
            
            if (enable) {
                // Start VPN service
                _vpnStatus.value = VpnStatus.CONNECTING
                Log.i(TAG, "   Starting VPN service...")
                
                try {
                    val intent = android.content.Intent(
                        application,
                        VpnEngineService::class.java
                    ).apply {
                        action = VpnEngineService.ACTION_START
                    }
                    
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        application.startForegroundService(intent)
                    } else {
                        application.startService(intent)
                    }
                    
                    Log.i(TAG, "✅ VPN service start intent sent")
                    // Status updates flow via VpnServiceStateTracker
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to start VPN service", e)
                    _vpnStatus.value = VpnStatus.ERROR
                }
            } else {
                // Stop VPN service
                Log.i(TAG, "   Stopping VPN service...")
                
                val intent = android.content.Intent(
                    application,
                    VpnEngineService::class.java
                ).apply {
                    action = VpnEngineService.ACTION_STOP
                }
                application.startService(intent)
                
                _vpnStatus.value = VpnStatus.DISCONNECTED
                Log.i(TAG, "✅ VPN service stop intent sent")
            }
        }
    }
    
    override fun onAppRuleChange(app: AppRule, newGroupId: String?) {
        viewModelScope.launch(exceptionHandler) {
            Log.i(TAG, "═══════════════════════════════════════════════════════")
            Log.i(TAG, "🔀 App rule change: ${app.packageName}")
            Log.i(TAG, "   Old group: ${app.routedGroupId ?: "Bypass"}")
            Log.i(TAG, "   New group: ${newGroupId ?: "Bypass"}")
            Log.i(TAG, "═══════════════════════════════════════════════════════")
            
            try {
                // Find a VPN config in the target group (regionId)
                val vpnConfigId = if (newGroupId != null) {
                    // Find first VPN config matching the target region
                    val vpnConfigs = settingsRepository.getAllVpnConfigs().first()
                    val targetConfig = vpnConfigs.firstOrNull { it.regionId == newGroupId }
                    
                    if (targetConfig == null) {
                        Log.e(TAG, "❌ No VPN config found for group: $newGroupId")
                        return@launch
                    }
                    
                    Log.d(TAG, "   Selected config: ${targetConfig.name} (${targetConfig.id})")
                    targetConfig.id
                } else {
                    // Bypass VPN (direct internet)
                    Log.d(TAG, "   Setting to Bypass VPN (direct internet)")
                    null
                }
                
                // Save to database
                settingsRepository.saveAppRule(
                    DbAppRule(
                        packageName = app.packageName,
                        vpnConfigId = vpnConfigId
                    )
                )
                
                Log.i(TAG, "✅ App rule saved to database")
                
                // Update local state immediately (Flow will emit update, but this gives instant feedback)
                val currentRules = _allAppRules.value.toMutableList()
                val index = currentRules.indexOfFirst { it.packageName == app.packageName }
                
                if (index != -1) {
                    currentRules[index] = app.copy(routedGroupId = newGroupId)
                    _allAppRules.value = currentRules
                    Log.d(TAG, "   Local state updated")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to save app rule", e)
            }
        }
    }
    
    override fun onServerGroupSelected(group: ServerGroup) {
        Log.d(TAG, "📂 Server group selected: ${group.name} (${group.id})")
        _selectedServerGroup.value = group
    }
    
    override fun onAddServerGroup() {
        Log.d(TAG, "➕ Add server group (delegated to existing UI)")
        // In production, this would open a dialog to configure new VPN servers
        // For now, this is handled by existing UI (not part of RouterViewModel)
    }
    
    /**
     * Measure latency to a server by attempting a TCP connection
     */
    private suspend fun measureLatency(hostname: String, timeoutMs: Int = 2000): Int? {
        return withContext(Dispatchers.IO) {
            try {
                val host = hostname.split(":").first()
                val port = if (hostname.contains(":")) {
                    hostname.split(":").getOrNull(1)?.toIntOrNull() ?: 443
                } else {
                    443 // Default to HTTPS port
                }
                
                val startTime = System.currentTimeMillis()
                Socket().use { socket ->
                    socket.connect(java.net.InetSocketAddress(host, port), timeoutMs)
                }
                val latency = (System.currentTimeMillis() - startTime).toInt()
                if (latency >= 0 && latency < timeoutMs) {
                    Log.d(TAG, "Latency measurement: $host:$port = ${latency}ms")
                    latency
                } else {
                    Log.w(TAG, "Latency measurement invalid: $host:$port = ${latency}ms")
                    null
                }
            } catch (e: SocketTimeoutException) {
                Log.d(TAG, "Latency measurement timeout: $hostname")
                null
            } catch (e: Exception) {
                Log.w(TAG, "Latency measurement error: $hostname - ${e.message}")
                null
            }
        }
    }
    
    /**
     * Monitor tunnel connection status and latency, updating ServerGroups periodically
     */
    private suspend fun monitorTunnelStatus() {
        while (true) {
            try {
                delay(5000) // Update every 5 seconds
                
                val connectionManager = try {
                    VpnConnectionManager.getInstance()
                } catch (e: IllegalStateException) {
                    // VpnConnectionManager not initialized yet
                    continue
                }
                
                val vpnConfigs = settingsRepository.getAllVpnConfigs().first()
                val appRules = settingsRepository.appRuleDao.getAllRulesList()
                val activeVpnConfigIds = appRules.mapNotNull { it.vpnConfigId }.toSet()
                
                // Group by region
                val groupedByRegion = vpnConfigs.groupBy { it.regionId }
                
                // Update ServerGroups with connection status and latency
                val updatedGroups = groupedByRegion.map { (regionId, configs) ->
                    val isActive = configs.any { config -> activeVpnConfigIds.contains(config.id) }
                    val regionName = getRegionDisplayName(regionId)
                    
                    // Find first connected tunnel in this region
                    var isConnected = false
                    var latencyMs: Int? = null
                    
                    for (config in configs) {
                        val tunnelId = "${config.templateId}_${config.regionId}"
                        if (connectionManager.isTunnelConnected(tunnelId)) {
                            isConnected = true
                            // Measure latency for the first connected tunnel
                            if (latencyMs == null && config.serverHostname.isNotEmpty()) {
                                latencyMs = measureLatency(config.serverHostname)
                            }
                            break
                        }
                    }
                    
                    ServerGroup(
                        id = regionId,
                        name = regionName,
                        region = regionId,
                        serverCount = configs.size,
                        isActive = isActive,
                        isConnected = isConnected,
                        latencyMs = latencyMs
                    )
                }
                
                _allServerGroups.value = updatedGroups
            } catch (e: Exception) {
                Log.w(TAG, "Error monitoring tunnel status: ${e.message}")
            }
        }
    }
    
    override fun onRemoveServerGroup(group: ServerGroup) {
        viewModelScope.launch(exceptionHandler) {
            Log.i(TAG, "═══════════════════════════════════════════════════════")
            Log.i(TAG, "🗑️  Remove server group: ${group.name} (${group.id})")
            Log.i(TAG, "═══════════════════════════════════════════════════════")
            
            try {
                // Find all VPN configs in this group (same regionId)
                val vpnConfigs = settingsRepository.getAllVpnConfigs().first()
                val configsToDelete = vpnConfigs.filter { it.regionId == group.id }
                
                Log.d(TAG, "   Found ${configsToDelete.size} configs to delete")
                
                // Delete each config (this will cascade to app rules via foreign key)
                for (config in configsToDelete) {
                    Log.d(TAG, "   Deleting config: ${config.name} (${config.id})")
                    settingsRepository.deleteVpnConfig(config.id)
                }
                
                Log.i(TAG, "✅ Server group removed from database")
                
                // Update local state immediately
                val currentGroups = _allServerGroups.value.toMutableList()
                currentGroups.removeAll { it.id == group.id }
                _allServerGroups.value = currentGroups
                Log.d(TAG, "   Local state updated")
                
                // If this was the selected group, clear selection
                if (_selectedServerGroup.value?.id == group.id) {
                    _selectedServerGroup.value = null
                    Log.d(TAG, "   Cleared selected group")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to remove server group", e)
            }
        }
    }
}

