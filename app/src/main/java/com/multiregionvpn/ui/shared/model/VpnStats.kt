package com.multiregionvpn.ui.shared.model

/**
 * Shared VPN statistics model
 */
data class VpnStats(
    val bytesSent: Long = 0L,
    val bytesReceived: Long = 0L,
    val connectionTimeSeconds: Long = 0L,
    val activeConnections: Int = 0
)
