package com.multiregionvpn.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Maps a specific Android app (by package name) to a VPN configuration.
 */
@Entity(
    tableName = "app_rules",
    foreignKeys = [
        ForeignKey(
            entity = VpnConfig::class,
            parentColumns = ["id"],
            childColumns = ["vpnConfigId"],
            onDelete = ForeignKey.SET_NULL // If a VPN is deleted, set rule to null (direct internet)
        ),
        ForeignKey(
            entity = ProviderAccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["providerAccountId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("vpnConfigId"), Index("providerAccountId")]
)
data class AppRule(
    @PrimaryKey
    val packageName: String, // e.g., "com.bbc.iplayer"
    val vpnConfigId: String? = null, // e.g., "uuid-12345". Null means "Direct Internet".
    // JIT VPN fields:
    val providerAccountId: String? = null, // Reference to ProviderAccountEntity
    val regionCode: String? = null, // e.g., "UK", "FR" - used for JIT server selection
    val preferredProtocol: String? = null, // "openvpn_udp", "wireguard", etc.
    val fallbackDirect: Boolean = false // If true, fallback to direct internet on connection failure
)

