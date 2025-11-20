package com.multiregionvpn.ui.settings

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.multiregionvpn.data.database.AppRule
import com.multiregionvpn.data.database.ProviderCredentials
import com.multiregionvpn.data.database.VpnConfig
import com.multiregionvpn.data.repository.SettingsRepository
import com.multiregionvpn.network.NordVpnApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.multiregionvpn.core.VpnError
import com.multiregionvpn.core.VpnEngineService
import com.multiregionvpn.ui.components.VpnStatus

/**
 * Events emitted by ViewModel for VPN service operations.
 * The UI layer observes these events and performs Context-dependent actions.
 */
sealed class VpnServiceEvent {
    object Start : VpnServiceEvent()
    object Stop : VpnServiceEvent()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
    private val nordVpnApiService: NordVpnApiService,
    private val app: Application // AndroidViewModel provides Application context
) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    private val _vpnServiceEvents = MutableSharedFlow<VpnServiceEvent>()
    val vpnServiceEvents: SharedFlow<VpnServiceEvent> = _vpnServiceEvents.asSharedFlow()
    
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
                
                android.util.Log.e("SettingsViewModel", "Received VPN error: ${error.type} - ${error.message}")
                _uiState.update { it.copy(
                    currentError = error,
                    vpnStatus = VpnStatus.ERROR
                ) }
            }
        }
    }

    init {
        loadAllData()
        // Register error receiver
        LocalBroadcastManager.getInstance(app).registerReceiver(
            errorReceiver,
            IntentFilter(VpnEngineService.ACTION_VPN_ERROR)
        )
    }
    
    override fun onCleared() {
        super.onCleared()
        LocalBroadcastManager.getInstance(app).unregisterReceiver(errorReceiver)
    }
    
    fun clearError() {
        _uiState.update { it.copy(currentError = null) }
    }

    private fun loadAllData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // 1. Get Nord Credentials (UPDATED)
            val nordCreds = settingsRepo.getProviderCredentials("nordvpn")
            
            // 2. Load all installed apps
            val installedApps = loadInstalledApps()
            
            // 3. Combine flows for configs and rules
            combine(
                settingsRepo.getAllVpnConfigs(),
                settingsRepo.getAllAppRules()
            ) { configs, rules ->
                SettingsUiState(
                    vpnConfigs = configs,
                    appRules = rules.associate { it.packageName to it.vpnConfigId },
                    nordCredentials = nordCreds, // UPDATED
                    installedApps = installedApps,
                    isLoading = false
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }
    
    private fun loadInstalledApps(): List<InstalledApp> {
        val pm = app.packageManager
        
        // Get all installed apps (user and system)
        val allApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        
        android.util.Log.d("SettingsViewModel", "Total installed: ${allApps.size}")
        
        // Debug: Print all user-installed packages
        val userPackages = allApps
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .map { it.packageName }
        android.util.Log.d("SettingsViewModel", "User packages (${userPackages.size}): ${userPackages.joinToString(", ")}")
        
        // Filter to apps that are visible and meaningful
        val apps = allApps
            .filter { appInfo ->
                // Exclude our own VPN app
                if (appInfo.packageName == app.packageName) return@filter false
                
                // Include user-installed apps
                if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0) return@filter true
                
                // Include updated system apps (like Chrome when updated)
                if ((appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) return@filter true
                
                // Include common system apps by package name patterns
                val pkg = appInfo.packageName
                if (pkg.startsWith("com.google.android.apps.") ||  // Google apps
                    pkg.startsWith("com.android.chrome") ||
                    pkg.startsWith("com.google.android.gm") ||      // Gmail
                    pkg.startsWith("com.google.android.youtube") ||
                    pkg.startsWith("com.google.android.googlequicksearchbox") // Google app
                ) return@filter true
                
                false
            }
            .map { appInfo ->
                InstalledApp(
                    name = appInfo.loadLabel(pm).toString(),
                    packageName = appInfo.packageName,
                    icon = appInfo.loadIcon(pm)
                )
            }
            .sortedBy { it.name.lowercase() }
        
        android.util.Log.d("SettingsViewModel", "Loaded ${apps.size} apps")
        android.util.Log.d("SettingsViewModel", "First 15: ${apps.take(15).map { it.name }}")
        
        // Debug: Check if BBC iPlayer is in the list
        val bbcApp = apps.find { it.packageName.contains("bbc", ignoreCase = true) }
        if (bbcApp != null) {
            android.util.Log.d("SettingsViewModel", "✅ BBC iPlayer found: ${bbcApp.name}")
        } else {
            android.util.Log.w("SettingsViewModel", "❌ BBC iPlayer NOT in list!")
            // Check if it was filtered out
            val bbcInfo = allApps.find { it.packageName.contains("bbc", ignoreCase = true) }
            if (bbcInfo != null) {
                android.util.Log.w("SettingsViewModel", "BBC found in allApps: ${bbcInfo.packageName}, flags=${bbcInfo.flags}")
            }
        }
        
        return apps
    }
    
    // --- UI Actions ---
    
    // RENAMED and UPDATED Function
    fun saveNordCredentials(username: String, password: String) = viewModelScope.launch {
        val creds = ProviderCredentials(
            templateId = "nordvpn", 
            username = username, 
            password = password
        )
        settingsRepo.saveProviderCredentials(creds)
        _uiState.update { it.copy(nordCredentials = creds) }
    }
    
    fun saveVpnConfig(config: VpnConfig) = viewModelScope.launch {
        settingsRepo.saveVpnConfig(config)
        // The flow will automatically update the UI
    }
    
    fun deleteVpnConfig(id: String) = viewModelScope.launch {
        settingsRepo.deleteVpnConfig(id)
        // The flow will automatically update the UI
    }
    
    fun saveAppRule(packageName: String, vpnConfigId: String?) = viewModelScope.launch {
        if (vpnConfigId == null) {
            settingsRepo.deleteAppRule(packageName)
        } else {
            settingsRepo.createAppRule(packageName, vpnConfigId)
        }
        // The flow will automatically update the UI
    }
    
    fun startVpn() {
        android.util.Log.d("SettingsViewModel", "startVpn() called - emitting Start event")
        
        // Set status to CONNECTING immediately
        _uiState.update { it.copy(
            isVpnRunning = true,
            vpnStatus = VpnStatus.CONNECTING
        ) }
        
        // Emit event for UI to handle service start
        viewModelScope.launch {
            _vpnServiceEvents.emit(VpnServiceEvent.Start)
        }
        
        // After a short delay, assume connected (will be updated by service callbacks)
        viewModelScope.launch {
            kotlinx.coroutines.delay(3000)
            if (_uiState.value.isVpnRunning) {
                _uiState.update { it.copy(vpnStatus = VpnStatus.PROTECTED) }
            }
        }
        
        android.util.Log.d("SettingsViewModel", "startVpn() completed - status set to CONNECTING")
    }
    
    fun stopVpn() {
        android.util.Log.d("SettingsViewModel", "stopVpn() called - emitting Stop event")
        
        // Emit event for UI to handle service stop
        viewModelScope.launch {
            _vpnServiceEvents.emit(VpnServiceEvent.Stop)
        }
        
        _uiState.update { it.copy(
            isVpnRunning = false,
            vpnStatus = VpnStatus.DISCONNECTED,
            dataRateMbps = 0.0
        ) }
        android.util.Log.d("SettingsViewModel", "stopVpn() completed - status set to DISCONNECTED")
    }
    
    fun fetchNordVpnServer(regionId: String, callback: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                // Get credentials from repository
                val creds = settingsRepo.getProviderCredentials("nordvpn")
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
                
                // Call NordVPN API (Note: The API may require authentication)
                // For now, we'll try without auth, but this may need adjustment
                val servers = try {
                    nordVpnApiService.getServers("Bearer ${creds.username}") // Using username as token placeholder
                } catch (e: Exception) {
                    // If auth fails, try public endpoint or handle differently
                    emptyList()
                }
                
                // Find best server for the region (lowest load)
                val bestServer = servers
                    .filter { it.country == countryCode }
                    .minByOrNull { it.load ?: Int.MAX_VALUE }
                
                callback(bestServer?.hostname)
            } catch (e: Exception) {
                callback(null)
            }
        }
    }

}

data class SettingsUiState(
    val vpnConfigs: List<VpnConfig> = emptyList(),
    val appRules: Map<String, String?> = emptyMap(), // <packageName, vpnConfigId>
    val nordCredentials: ProviderCredentials? = null,
    val installedApps: List<InstalledApp> = emptyList(),
    val isLoading: Boolean = true,
    val isVpnRunning: Boolean = false,
    val vpnStatus: VpnStatus = VpnStatus.DISCONNECTED,
    val dataRateMbps: Double = 0.0,
    val currentError: VpnError? = null
)
