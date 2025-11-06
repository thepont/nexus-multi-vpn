package com.multiregionvpn.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey

@Entity(
    tableName = "app_rule",
    foreignKeys = [
        ForeignKey(
            entity = VpnConfig::class,
            parentColumns = ["id"],
            childColumns = ["vpnConfigId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class AppRule(
    @PrimaryKey
    val packageName: String,
    val vpnConfigId: String
)

