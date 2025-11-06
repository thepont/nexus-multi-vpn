package com.multiregionvpn.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A pre-seeded, read-only table that maps known apps to their required region.
 * This is used by the AutoRuleService to suggest or create rules.
 */
@Entity(tableName = "preset_rules")
data class PresetRule(
    @PrimaryKey
    val packageName: String, // e.g., "com.bbc.iplayer"
    val targetRegionId: String // e.g., "UK"
)

