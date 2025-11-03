package com.multiregionvpn.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores the credentials for a specific VPN provider template.
 * NOTE: This table will be encrypted.
 */
@Entity(tableName = "provider_credentials")
data class ProviderCredentials(
    @PrimaryKey
    val templateId: String, // e.g., "nordvpn"
    
    // --- UPDATED FIELDS ---
    val username: String, // The NordVPN Service Username
    val password: String  // The NordVPN Service Password
)
