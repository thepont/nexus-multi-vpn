package com.multiregionvpn.data.database

import androidx.room.*

@Dao
interface ProviderCredentialsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(credentials: ProviderCredentials)

    @Query("SELECT * FROM provider_credentials WHERE templateId = :templateId LIMIT 1")
    suspend fun get(templateId: String): ProviderCredentials?
}
