package com.multiregionvpn.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "preset_rule")
data class PresetRule(
    @PrimaryKey
    val packageName: String,
    val targetRegionId: String
)

