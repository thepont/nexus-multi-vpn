package com.multiregionvpn.ui.shared

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow

/**
 * Shared ViewModel interface for both Mobile and TV UIs
 * 
 * This is the "single source of truth" for the VPN router state.
 * Both UIs are "dumb" - they only observe state from this ViewModel
 * and send user events to it.
 * 
 * Implementation will bridge to the existing C++ backend via JNI/Kotlin.
 */
abstract class RouterViewModel : ViewModel() {
    
    // ═══════════════════════════════════════════════════════════════════════════
    // STATE (Observed by UI)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Current VPN connection status
     */
    abstract val vpnStatus: StateFlow<VpnStatus>
    
    /**
     * All server groups (logical routing destinations)
     */
    abstract val allServerGroups: StateFlow<List<ServerGroup>>
    
    /**
     * All app routing rules
     */
    abstract val allAppRules: StateFlow<List<AppRule>>
    
    /**
     * All installed apps (including those without routing rules)
     * This is what should be displayed in the app list UI
     */
    abstract val allInstalledApps: StateFlow<List<AppRule>>
    
    /**
     * Currently selected server group (for detail view)
     */
    abstract val selectedServerGroup: StateFlow<ServerGroup?>
    
    /**
     * Live statistics (bytes sent, bytes received, connection time)
     */
    abstract val liveStats: StateFlow<VpnStats>
    
    // ═══════════════════════════════════════════════════════════════════════════
    // EVENTS (Triggered by UI)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * User toggled the master VPN switch
     * 
     * @param enable true to start VPN, false to stop
     */
    abstract fun onToggleVpn(enable: Boolean)
    
    /**
     * User changed an app's routing rule
     * 
     * @param app The app rule to modify
     * @param newGroupId null = Bypass, "block" = Block, otherwise = ServerGroup ID
     */
    abstract fun onAppRuleChange(app: AppRule, newGroupId: String?)
    
    /**
     * User selected a server group (for detail view)
     * 
     * @param group The selected group
     */
    abstract fun onServerGroupSelected(group: ServerGroup)
    
    /**
     * User wants to add a new server group
     */
    abstract fun onAddServerGroup()
    
    /**
     * User wants to remove a server group
     * 
     * @param group The group to remove
     */
    abstract fun onRemoveServerGroup(group: ServerGroup)
}

/**
 * Live VPN statistics
 */
data class VpnStats(
    val bytesSent: Long = 0L,
    val bytesReceived: Long = 0L,
    val connectionTimeSeconds: Long = 0L,
    val activeConnections: Int = 0
)

