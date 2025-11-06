package com.multiregionvpn.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vpn_config")
data class VpnConfig(
    @PrimaryKey
    val id: String,
    val name: String,
    val regionId: String,
    val templateId: String,
    val serverHostname: String
)

