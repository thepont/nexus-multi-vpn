package com.multiregionvpn.ui.shared

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.multiregionvpn.core.VpnEngineService
import com.multiregionvpn.data.database.AppRule as DbAppRule
import com.multiregionvpn.data.database.VpnConfig
import com.multiregionvpn.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RouterViewModelImpl @Inject constructor(
    private val application: Application,
    private val settingsRepository: SettingsRepository
) : RouterViewModel() {
    
    companion object {
        private const val TAG = "RouterViewModelImpl"
    }
    
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

    private val _error = MutableStateFlow<String?>(null)
    override val error: StateFlow<String?> = _error.asStateFlow()
    
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Error in RouterViewModel", throwable)
        _error.value = throwable.message
    }
    
    init {
        Log.i(TAG, "Initializing...")
        
        viewModelScope.launch(exceptionHandler) {
            launch { loadServerGroups() }
            launch { loadAppRules() }
            launch { loadInstalledApps() }
            
            VpnEngineService.vpnStatus.collect { status ->
                _vpnStatus.value = status
            }
        }
    }
    
    private suspend fun loadServerGroups() {
        settingsRepository.getAllVpnConfigs().collect { vpnConfigs ->
            val appRules = settingsRepository.appRuleDao.getAllRulesList()
            val activeVpnConfigIds = appRules.mapNotNull { it.vpnConfigId }.toSet()
            
            val serverGroups = vpnConfigs.groupBy { it.regionId }.map { (regionId, configs) ->
                val isActive = configs.any { config -> activeVpnConfigIds.contains(config.id) }
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
        }
    }
    
    private suspend fun loadAppRules() {
        val packageManager = application.packageManager
        settingsRepository.getAllAppRules().collect { dbAppRules ->
            val appRules = dbAppRules.mapNotNull { dbRule ->
                try {
                    val appInfo = packageManager.getApplicationInfo(dbRule.packageName, 0)
                    val appName = packageManager.getApplicationLabel(appInfo).toString()
                    val icon: Drawable? = try {
                        packageManager.getApplicationIcon(dbRule.packageName)
                    } catch (e: Exception) { null }
                    
                    val groupId = if (dbRule.vpnConfigId != null) {
                        val vpnConfig = settingsRepository.getVpnConfigById(dbRule.vpnConfigId!!)
                        vpnConfig?.regionId
                    } else { null }
                    
                    AppRule(
                        packageName = dbRule.packageName,
                        appName = appName,
                        icon = icon,
                        routedGroupId = groupId
                    )
                } catch (e: Exception) {
                    null
                }
            }
            _allAppRules.value = appRules
        }
    }
    
    private suspend fun loadInstalledApps() {
        val packageManager = application.packageManager
        val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 }
            .sortedBy { packageManager.getApplicationLabel(it).toString().lowercase() }
        
        val dbAppRules = settingsRepository.getAllAppRules().first()
        val rulesMap = dbAppRules.associateBy { it.packageName }
        
        val appRules = installedApps.mapNotNull { appInfo ->
            try {
                val packageName = appInfo.packageName
                val appName = packageManager.getApplicationLabel(appInfo).toString()
                val icon: Drawable? = try { packageManager.getApplicationIcon(packageName) } catch (e: Exception) { null }
                
                val dbRule = rulesMap[packageName]
                val groupId = if (dbRule?.vpnConfigId != null) {
                    val vpnConfig = settingsRepository.getVpnConfigById(dbRule.vpnConfigId!!)
                    vpnConfig?.regionId
                } else { null }
                
                AppRule(
                    packageName = packageName,
                    appName = appName,
                    icon = icon,
                    routedGroupId = groupId
                )
            } catch (e: Exception) {
                null
            }
        }
        
        _allInstalledApps.value = appRules
    }
    
    private fun getRegionDisplayName(regionId: String): String {
        return when (regionId.lowercase()) {
            "uk" -> "United Kingdom"
            "us" -> "United States"
            "fr" -> "France"
            else -> regionId.uppercase()
        }
    }
    
    override fun onToggleVpn(enable: Boolean) {
        val intent = Intent(application, VpnEngineService::class.java).apply {
            action = if (enable) VpnEngineService.ACTION_START else VpnEngineService.ACTION_STOP
        }
        application.startService(intent)
    }
    
    override fun onAppRuleChange(app: AppRule, newGroupId: String?) {
        viewModelScope.launch(exceptionHandler) {
            val vpnConfigId = if (newGroupId != null) {
                val vpnConfigs = settingsRepository.getAllVpnConfigs().first()
                val targetConfig = vpnConfigs.firstOrNull { it.regionId == newGroupId }
                targetConfig?.id
            } else {
                null
            }
            
            settingsRepository.saveAppRule(
                DbAppRule(
                    packageName = app.packageName,
                    vpnConfigId = vpnConfigId
                )
            )
        }
    }
    
    override fun onServerGroupSelected(group: ServerGroup) {
        _selectedServerGroup.value = group
    }
    
    override fun onAddServerGroup() {
        // Not implemented
    }
    
    override fun onRemoveServerGroup(group: ServerGroup) {
        viewModelScope.launch(exceptionHandler) {
            val vpnConfigs = settingsRepository.getAllVpnConfigs().first()
            val configsToDelete = vpnConfigs.filter { it.regionId == group.id }
            for (config in configsToDelete) {
                settingsRepository.deleteVpnConfig(config.id)
            }
        }
    }

    override fun onClearError() {
        _error.value = null
    }
}
