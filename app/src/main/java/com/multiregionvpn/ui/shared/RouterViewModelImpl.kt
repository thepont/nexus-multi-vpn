package com.multiregionvpn.ui.shared

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.lifecycle.viewModelScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.multiregionvpn.core.ConnectionTracker
import com.multiregionvpn.core.VpnEngineService
import com.multiregionvpn.core.VpnError
import com.multiregionvpn.data.GeoBlockedApps
import com.multiregionvpn.data.database.AppRule as DbAppRule
import com.multiregionvpn.data.database.ProviderCredentials
import com.multiregionvpn.data.database.VpnConfig
import com.multiregionvpn.data.repository.SettingsRepository
import com.multiregionvpn.network.NordVpnApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    private val settingsRepository: SettingsRepository,
    private val nordVpnApiService: NordVpnApiService
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
    
    private val _allVpnConfigs = MutableStateFlow<List<VpnConfig>>(emptyList())
    override val allVpnConfigs: StateFlow<List<VpnConfig>> = _allVpnConfigs.asStateFlow()
    
    private val _providerCredentials = MutableStateFlow<ProviderCredentials?>(null)
    override val providerCredentials: StateFlow<ProviderCredentials?> = _providerCredentials.asStateFlow()
    
    private val _currentError = MutableStateFlow<VpnError?>(null)
    override val currentError: StateFlow<VpnError?> = _currentError.asStateFlow()
    
    private val _isLoading = MutableStateFlow(true)
    override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _isVpnRunning = MutableStateFlow(false)
    override val isVpnRunning: StateFlow<Boolean> = _isVpnRunning.asStateFlow()
    
    private val _dataRateMbps = MutableStateFlow(0.0)
    override val dataRateMbps: StateFlow<Double> = _dataRateMbps.asStateFlow()
    
    // Error receiver for VPN errors
    private val errorReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == VpnEngineService.ACTION_VPN_ERROR) {
                val errorTypeStr = intent.getStringExtra(VpnEngineService.EXTRA_ERROR_TYPE) ?: return
                val errorMessage = intent.getStringExtra(VpnEngineService.EXTRA_ERROR_MESSAGE) ?: "Unknown error"
                val errorDetails = intent.getStringExtra(VpnEngineService.EXTRA_ERROR_DETAILS)
                val tunnelId = intent.getStringExtra(VpnEngineService.EXTRA_ERROR_TUNNEL_ID)
                val timestamp = intent.getLongExtra(VpnEngineService.EXTRA_ERROR_TIMESTAMP, System.currentTimeMillis())
                
                val errorType = try {
                    VpnError.ErrorType.valueOf(errorTypeStr)
                } catch (e: Exception) {
                    VpnError.ErrorType.UNKNOWN
                }
                
                val error = VpnError(
                    type = errorType,
                    message = errorMessage,
                    details = errorDetails,
                    tunnelId = tunnelId,
                    timestamp = timestamp
                )
                
                Log.e(TAG, "Received VPN error: ${error.type} - ${error.message}")
                _currentError.value = error
                _vpnStatus.value = VpnStatus.ERROR
            }
        }
    }
    
    // Error handler for all coroutines
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Error in RouterViewModel", throwable)
        _vpnStatus.value = VpnStatus.ERROR
    }
    
    init {
        Log.i(TAG, "═══════════════════════════════════════════════════════")
        Log.i(TAG, "📱 RouterViewModelImpl initializing...")
        Log.i(TAG, "═══════════════════════════════════════════════════════")
        
        // Register error receiver
        LocalBroadcastManager.getInstance(application).registerReceiver(
            errorReceiver,
            IntentFilter(VpnEngineService.ACTION_VPN_ERROR)
        )
        
        // Initialize state from backend
        viewModelScope.launch(exceptionHandler) {
            loadProviderCredentials()
            loadVpnConfigs()
            loadServerGroups()
            loadAppRules()
            loadInstalledApps()
            observeVpnStatus()
            observeLiveStats()
            _isLoading.value = false
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        LocalBroadcastManager.getInstance(application).unregisterReceiver(errorReceiver)
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // BACKEND INTEGRATION (Private)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Load provider credentials from repository
     */
    private suspend fun loadProviderCredentials() {
        try {
            Log.d(TAG, "🔑 Loading provider credentials...")
            val creds = settingsRepository.getProviderCredentials("nordvpn")
            _providerCredentials.value = creds
            Log.d(TAG, "   Credentials loaded: ${if (creds != null) "✅" else "❌"}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error loading provider credentials", e)
            _providerCredentials.value = null
        }
    }
    
    /**
     * Load all VPN configurations from repository
     */
    private suspend fun loadVpnConfigs() {
        try {
            Log.d(TAG, "🔧 Loading VPN configurations...")
            settingsRepository.getAllVpnConfigs().collect { configs ->
                _allVpnConfigs.value = configs
                Log.d(TAG, "   Loaded ${configs.size} VPN configs")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error loading VPN configs", e)
            _allVpnConfigs.value = emptyList()
        }
    }
    
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
                    
                    ServerGroup(
                        id = regionId,
                        name = regionName,
                        region = regionId,
                        serverCount = configs.size,
                        isActive = isActive
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
            
            val packageManager = application.packageManager
            
            // Get ALL installed user apps (excluding system apps)
            val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 }
                .sortedBy { packageManager.getApplicationLabel(it).toString().lowercase() }
            
            Log.d(TAG, "   Found ${installedApps.size} installed user apps")
            
            // Get current app rules from database
            val dbAppRules = settingsRepository.getAllAppRules().first()
            val rulesMap = dbAppRules.associateBy { it.packageName }
            
            // Create AppRule for EVERY installed app
            val appRules = installedApps.mapNotNull { appInfo ->
                try {
                    val packageName = appInfo.packageName
                    val appName = packageManager.getApplicationLabel(appInfo).toString()
                    val icon: Drawable? = try {
                        packageManager.getApplicationIcon(packageName)
                    } catch (e: Exception) {
                        null
                    }
                    
                    // Look up rule for this app (if it exists)
                    val dbRule = rulesMap[packageName]
                    val groupId = if (dbRule?.vpnConfigId != null) {
                        val vpnConfig = settingsRepository.getVpnConfigById(dbRule.vpnConfigId!!)
                        vpnConfig?.regionId  // Use regionId as groupId
                    } else {
                        null  // No rule = bypass VPN
                    }
                    
                    AppRule(
                        packageName = packageName,
                        appName = appName,
                        icon = icon,
                        routedGroupId = groupId
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "   ⚠️  Error loading app ${appInfo.packageName}", e)
                    null
                }
            }
            
            _allInstalledApps.value = appRules
            Log.i(TAG, "✅ Loaded ${appRules.size} installed apps (including ${appRules.count { it.routedGroupId != null }} with routing rules)")
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
    private suspend fun observeVpnStatus() {
        viewModelScope.launch {
            Log.d(TAG, "🔍 Starting VPN status observation...")
            while (true) {
                val isRunning = VpnEngineService.isRunning()
                val newStatus = when {
                    isRunning -> VpnStatus.CONNECTED
                    else -> VpnStatus.DISCONNECTED
                }
                
                // Update isVpnRunning state
                if (_isVpnRunning.value != isRunning) {
                    _isVpnRunning.value = isRunning
                }
                
                // Only update if status changed (reduces UI churn)
                if (_vpnStatus.value != newStatus) {
                    Log.d(TAG, "   Status changed: ${_vpnStatus.value} → $newStatus")
                    _vpnStatus.value = newStatus
                }
                
                kotlinx.coroutines.delay(1000)
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
    private suspend fun observeLiveStats() {
        viewModelScope.launch {
            Log.d(TAG, "📊 Starting live stats observation...")
            var connectionStartTime: Long? = null
            
            while (true) {
                try {
                    if (VpnEngineService.isRunning()) {
                        // Service is running - update connection time
                        if (connectionStartTime == null) {
                            connectionStartTime = System.currentTimeMillis()
                            Log.d(TAG, "   Connection started - tracking time")
                        }
                        
                        val connectionTimeSeconds = (System.currentTimeMillis() - connectionStartTime) / 1000
                        
                        // Get stats from ConnectionTracker (if available)
                        // NOTE: In a production app, we'd expose these via VpnConnectionManager
                        // For now, we show connection time and estimate active connections from server groups
                        val activeConnections = _allServerGroups.value.count { it.isActive }
                        
                        _liveStats.value = VpnStats(
                            bytesSent = 0L,  // TODO: Expose from ConnectionTracker
                            bytesReceived = 0L,  // TODO: Expose from ConnectionTracker
                            connectionTimeSeconds = connectionTimeSeconds,
                            activeConnections = activeConnections
                        )
                    } else {
                        // Service not running - reset stats
                        if (connectionStartTime != null) {
                            connectionStartTime = null
                            _liveStats.value = VpnStats()
                            Log.d(TAG, "   Connection stopped - reset stats")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "   Error updating stats: ${e.message}")
                }
                
                kotlinx.coroutines.delay(1000)
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
                    // Note: Status will be updated by observeVpnStatus() polling
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
    
    override fun saveVpnConfig(config: VpnConfig) {
        viewModelScope.launch(exceptionHandler) {
            Log.i(TAG, "💾 Saving VPN config: ${config.name}")
            try {
                settingsRepository.saveVpnConfig(config)
                Log.i(TAG, "✅ VPN config saved")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to save VPN config", e)
            }
        }
    }
    
    override fun deleteVpnConfig(configId: String) {
        viewModelScope.launch(exceptionHandler) {
            Log.i(TAG, "🗑️  Deleting VPN config: $configId")
            try {
                settingsRepository.deleteVpnConfig(configId)
                Log.i(TAG, "✅ VPN config deleted")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to delete VPN config", e)
            }
        }
    }
    
    override fun saveProviderCredentials(username: String, password: String) {
        viewModelScope.launch(exceptionHandler) {
            Log.i(TAG, "🔑 Saving provider credentials")
            try {
                val creds = ProviderCredentials(
                    templateId = "nordvpn",
                    username = username,
                    password = password
                )
                settingsRepository.saveProviderCredentials(creds)
                _providerCredentials.value = creds
                Log.i(TAG, "✅ Provider credentials saved")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to save provider credentials", e)
            }
        }
    }
    
    override fun fetchNordVpnServer(regionId: String, callback: (String?) -> Unit) {
        viewModelScope.launch(exceptionHandler) {
            try {
                val creds = _providerCredentials.value
                if (creds == null) {
                    callback(null)
                    return@launch
                }
                
                // Map region IDs to country codes
                val countryCodeMap = mapOf(
                    "UK" to "GB",
                    "FR" to "FR",
                    "AU" to "AU",
                    "US" to "US",
                    "DE" to "DE",
                    "CA" to "CA",
                    "JP" to "JP",
                    "IT" to "IT",
                    "ES" to "ES",
                    "NL" to "NL"
                )
                
                val countryCode = countryCodeMap[regionId] ?: regionId
                
                val servers = try {
                    nordVpnApiService.getServers("Bearer ${creds.username}")
                } catch (e: Exception) {
                    emptyList()
                }
                
                val bestServer = servers
                    .filter { it.country == countryCode }
                    .minByOrNull { it.load ?: Int.MAX_VALUE }
                
                callback(bestServer?.hostname)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to fetch NordVPN server", e)
                callback(null)
            }
        }
    }
    
    override fun clearError() {
        _currentError.value = null
    }
}

