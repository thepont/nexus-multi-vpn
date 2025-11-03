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
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
    private val nordVpnApiService: NordVpnApiService,
    private val app: Application // AndroidViewModel provides Application context
) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadAllData()
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
        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 } // Filter out system apps
            .map {
                InstalledApp(
                    name = it.loadLabel(pm).toString(),
                    packageName = it.packageName,
                    icon = it.loadIcon(pm)
                )
            }
            .sortedBy { it.name.lowercase() }
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
    
    fun startVpn(context: android.content.Context) {
        val intent = android.content.Intent(context, com.multiregionvpn.core.VpnEngineService::class.java).apply {
            action = com.multiregionvpn.core.VpnEngineService.ACTION_START
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        _uiState.update { it.copy(isVpnRunning = true) }
    }
    
    fun stopVpn(context: android.content.Context) {
        val intent = android.content.Intent(context, com.multiregionvpn.core.VpnEngineService::class.java).apply {
            action = com.multiregionvpn.core.VpnEngineService.ACTION_STOP
        }
        context.startService(intent)
        _uiState.update { it.copy(isVpnRunning = false) }
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
    val nordCredentials: ProviderCredentials? = null, // UPDATED
    val installedApps: List<InstalledApp> = emptyList(),
    val isLoading: Boolean = true,
    val isVpnRunning: Boolean = false
)
