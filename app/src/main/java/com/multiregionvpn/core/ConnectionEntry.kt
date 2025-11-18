package com.multiregionvpn.core

import com.multiregionvpn.core.provider.ProviderId
import com.multiregionvpn.core.provider.RegionRequest
import kotlinx.coroutines.Job

/**
 * Represents an active or pending VPN connection.
 */
data class ConnectionEntry(
    val routeKey: PacketBufferManager.RouteKey,
    val providerId: ProviderId,
    val regionRequest: RegionRequest,
    var state: ConnectionState = ConnectionState.DISCONNECTED,
    var tunnelId: String? = null, // Set once connection is established
    var lastActivityTime: Long = System.currentTimeMillis(), // Last time traffic was seen
    var connectionJob: Job? = null // Coroutine job managing this connection
) {
    fun updateActivity() {
        lastActivityTime = System.currentTimeMillis()
    }
    
    fun isIdle(idleTimeoutMs: Long): Boolean {
        return state == ConnectionState.CONNECTED && 
               (System.currentTimeMillis() - lastActivityTime) > idleTimeoutMs
    }
}


