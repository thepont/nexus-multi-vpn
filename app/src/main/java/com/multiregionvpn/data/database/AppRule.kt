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
        )
    ],
    indices = [Index("vpnConfigId")]
)
data class AppRule(
    @PrimaryKey
    val packageName: String, // e.g., "com.bbc.iplayer"
    val vpnConfigId: String? // e.g., "uuid-12345". Null means "Direct Internet".
)

