package com.multiregionvpn.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "provider_credentials")
data class ProviderCredentials(
    @PrimaryKey
    val templateId: String,
    val token: String
)

