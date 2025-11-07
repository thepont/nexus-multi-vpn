package com.multiregionvpn.ui.shared

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.viewModelScope
import com.multiregionvpn.core.VpnConnectionManager
import com.multiregionvpn.core.VpnEngineService
import com.multiregionvpn.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Production implementation of RouterViewModel
 * 
 * Bridges the shared UI models to the existing VPN backend:
 * - VpnEngineService (VPN control)
 * - VpnConnectionManager (tunnel management)
 * - SettingsRepository (persistence)
 */
@HiltViewModel
class RouterViewModelImpl @Inject constructor(
    private val application: Application,
    private val settingsRepository: SettingsRepository
) : RouterViewModel() {
    
    // ═══════════════════════════════════════════════════════════════════════════
    // STATE (Observable by UI)
    // ═══════════════════════════════════════════════════════════════════════════
    
    private val _vpnStatus = MutableStateFlow(VpnStatus.DISCONNECTED)
    override val vpnStatus: StateFlow<VpnStatus> = _vpnStatus.asStateFlow()
    
    private val _allServerGroups = MutableStateFlow<List<ServerGroup>>(emptyList())
    override val allServerGroups: StateFlow<List<ServerGroup>> = _allServerGroups.asStateFlow()
    
    private val _allAppRules = MutableStateFlow<List<AppRule>>(emptyList())
    override val allAppRules: StateFlow<List<AppRule>> = _allAppRules.asStateFlow()
    
    private val _selectedServerGroup = MutableStateFlow<ServerGroup?>(null)
    override val selectedServerGroup: StateFlow<ServerGroup?> = _selectedServerGroup.asStateFlow()
    
    private val _liveStats = MutableStateFlow(VpnStats())
    override val liveStats: StateFlow<VpnStats> = _liveStats.asStateFlow()
    
    init {
        // Initialize state from backend
        viewModelScope.launch {
            loadServerGroups()
            loadAppRules()
            observeVpnStatus()
            observeLiveStats()
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // BACKEND INTEGRATION (Private)
    // ═══════════════════════════════════════════════════════════════════════════
    
    private suspend fun loadServerGroups() {
        // TODO: Load VPN configs from repository and convert to ServerGroups
        // For now, use mock data
        _allServerGroups.value = MockRouterViewModel.createMockServerGroups()
    }
    
    private suspend fun loadAppRules() {
        // TODO: Load app rules from repository and convert to UI model
        // For now, use mock data
        _allAppRules.value = MockRouterViewModel.createMockAppRules()
    }
    
    private suspend fun observeVpnStatus() {
        // Monitor VPN engine service status
        // Note: In production, this would observe VpnEngineService.isRunning()
        // For now, we'll use a simplified approach
        viewModelScope.launch {
            // Poll service status (in production, use StateFlow from service)
            while (true) {
                _vpnStatus.value = when {
                    VpnEngineService.isRunning() -> VpnStatus.CONNECTED
                    else -> VpnStatus.DISCONNECTED
                }
                kotlinx.coroutines.delay(1000)
            }
        }
    }
    
    private suspend fun observeLiveStats() {
        // TODO: Monitor connection statistics from ConnectionTracker
        // For now, stats remain at zero
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // EVENTS (Triggered by UI)
    // ═══════════════════════════════════════════════════════════════════════════
    
    override fun onToggleVpn(enable: Boolean) {
        viewModelScope.launch {
            if (enable) {
                // Start VPN service
                _vpnStatus.value = VpnStatus.CONNECTING
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
                    
                    _vpnStatus.value = VpnStatus.CONNECTED
                } catch (e: Exception) {
                    _vpnStatus.value = VpnStatus.ERROR
                }
            } else {
                // Stop VPN service
                val intent = android.content.Intent(
                    application,
                    VpnEngineService::class.java
                ).apply {
                    action = VpnEngineService.ACTION_STOP
                }
                application.startService(intent)
                _vpnStatus.value = VpnStatus.DISCONNECTED
            }
        }
    }
    
    override fun onAppRuleChange(app: AppRule, newGroupId: String?) {
        viewModelScope.launch {
            // TODO: Update repository
            // For now, just update local state
            val currentRules = _allAppRules.value.toMutableList()
            val index = currentRules.indexOfFirst { it.packageName == app.packageName }
            
            if (index != -1) {
                currentRules[index] = app.copy(routedGroupId = newGroupId)
                _allAppRules.value = currentRules
            }
        }
    }
    
    override fun onServerGroupSelected(group: ServerGroup) {
        _selectedServerGroup.value = group
    }
    
    override fun onAddServerGroup() {
        // In production, this would open a dialog to configure new VPN servers
        // For now, this is a no-op (handled by existing UI)
    }
    
    override fun onRemoveServerGroup(group: ServerGroup) {
        viewModelScope.launch {
            // TODO: Remove all VPN configs in this group from repository
            // For now, just update local state
            val currentGroups = _allServerGroups.value.toMutableList()
            currentGroups.removeAll { it.id == group.id }
            _allServerGroups.value = currentGroups
        }
    }
}

