package com.multiregionvpn.core

import com.multiregionvpn.ui.shared.VpnStats
import com.multiregionvpn.ui.shared.VpnStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Thread-safe tracker that exposes VPN service status and stats as StateFlows.
 * The foreground service should call updateStatus/updateStats whenever the
 * connection changes. UI/ViewModels collect these flows instead of polling.
 */
object VpnServiceStateTracker {
    private val _status = MutableStateFlow(VpnStatus.DISCONNECTED)
    val status: StateFlow<VpnStatus> = _status.asStateFlow()

    private val _stats = MutableStateFlow(VpnStats())
    val stats: StateFlow<VpnStats> = _stats.asStateFlow()

    fun updateStatus(status: VpnStatus) {
        _status.value = status
    }

    fun updateStats(stats: VpnStats) {
        _stats.value = stats
    }

    fun reset() {
        _status.value = VpnStatus.DISCONNECTED
        _stats.value = VpnStats()
    }
}

