package com.multiregionvpn.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a user's account with a VPN provider.
 * Credentials are stored encrypted in the encryptedCredentials field.
 */
@Entity(tableName = "provider_accounts")
data class ProviderAccountEntity(
    @PrimaryKey
    val id: String, // UUID
    val providerId: String, // e.g., "nordvpn", "protonvpn"
    val displayLabel: String, // User-friendly label, e.g., "My NordVPN Account"
    val encryptedCredentials: ByteArray, // Encrypted credential blob
    val lastAuthState: String? = null, // "authenticated", "failed", null = unknown
    val updatedAt: Long = System.currentTimeMillis() // Timestamp of last update
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ProviderAccountEntity

        if (id != other.id) return false
        if (providerId != other.providerId) return false
        if (displayLabel != other.displayLabel) return false
        if (!encryptedCredentials.contentEquals(other.encryptedCredentials)) return false
        if (lastAuthState != other.lastAuthState) return false
        if (updatedAt != other.updatedAt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + providerId.hashCode()
        result = 31 * result + displayLabel.hashCode()
        result = 31 * result + encryptedCredentials.contentHashCode()
        result = 31 * result + (lastAuthState?.hashCode() ?: 0)
        result = 31 * result + updatedAt.hashCode()
        return result
    }
}


