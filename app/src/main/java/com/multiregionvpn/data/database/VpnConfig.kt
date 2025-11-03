package com.multiregionvpn.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a user-configured VPN server.
 */
@Entity(tableName = "vpn_configs")
data class VpnConfig(
    @PrimaryKey
    val id: String, // e.g., "uuid-12345"
    val name: String, // e.g., "My Nord UK Server"
    val regionId: String, // e.g., "UK"
    val templateId: String, // e.g., "nordvpn"
    val serverHostname: String // e.g., "uk1860.nordvpn.com"
)
