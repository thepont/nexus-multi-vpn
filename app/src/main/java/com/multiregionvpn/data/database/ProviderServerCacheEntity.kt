package com.multiregionvpn.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Caches server lists fetched from provider APIs.
 * Stores JSON payload and metadata for cache invalidation.
 */
@Entity(
    tableName = "provider_server_cache",
    indices = [
        Index(value = ["providerId", "regionCode"]),
        Index(value = ["fetchedAt"])
    ]
)
data class ProviderServerCacheEntity(
    @PrimaryKey
    val id: String, // Composite key: "${providerId}_${regionCode}"
    val providerId: String, // e.g., "nordvpn"
    val regionCode: String, // e.g., "UK", "FR"
    val payloadJson: String, // JSON array of ProviderServer objects
    val fetchedAt: Long = System.currentTimeMillis(), // Timestamp when cache was created
    val ttlSeconds: Long = 3600L, // Time-to-live in seconds (default 1 hour)
    val latencyMs: Long? = null // Average latency in milliseconds, null if not measured
)


