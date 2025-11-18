package com.multiregionvpn.ui.shared

/**
 * Represents a logical group of VPN servers
 * 
 * @param id Unique identifier for the group
 * @param name User-defined alias (e.g., "UK Streaming")
 * @param region Region code (e.g., "uk", "us", "fr")
 * @param serverCount Number of servers in this group
 * @param isActive Whether this group is currently in use by any app rule
 * @param isConnected Whether any tunnel in this group is currently connected
 * @param latencyMs Latency in milliseconds (null if not connected or not measured)
 */
data class ServerGroup(
    val id: String,
    val name: String,
    val region: String,
    val serverCount: Int,
    val isActive: Boolean = false,
    val isConnected: Boolean = false,
    val latencyMs: Int? = null
)

