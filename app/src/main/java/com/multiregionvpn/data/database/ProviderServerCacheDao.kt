package com.multiregionvpn.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderServerCacheDao {
    @Query("SELECT * FROM provider_server_cache WHERE providerId = :providerId AND regionCode = :regionCode LIMIT 1")
    suspend fun getCache(providerId: String, regionCode: String): ProviderServerCacheEntity?

    @Query("""
        SELECT * FROM provider_server_cache 
        WHERE providerId = :providerId 
        AND regionCode = :regionCode 
        AND (fetchedAt + (ttlSeconds * 1000)) > :maxAge
        LIMIT 1
    """)
    suspend fun getValidCache(providerId: String, regionCode: String, maxAge: Long = System.currentTimeMillis()): ProviderServerCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCache(cache: ProviderServerCacheEntity)

    @Query("DELETE FROM provider_server_cache WHERE providerId = :providerId")
    suspend fun clearCacheForProvider(providerId: String)

    @Query("DELETE FROM provider_server_cache WHERE providerId = :providerId AND regionCode = :regionCode")
    suspend fun clearCacheForRegion(providerId: String, regionCode: String)

    @Query("DELETE FROM provider_server_cache WHERE fetchedAt < :cutoffTime")
    suspend fun clearExpiredCache(cutoffTime: Long = System.currentTimeMillis() - (7 * 24 * 3600 * 1000L)) // 7 days
}


