package com.multiregionvpn.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.multiregionvpn.data.entity.ProviderCredentials

@Dao
interface ProviderCredentialsDao {
    @Query("SELECT * FROM provider_credentials WHERE templateId = :templateId")
    suspend fun getByTemplateId(templateId: String): ProviderCredentials?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(providerCredentials: ProviderCredentials)

    @Update
    suspend fun update(providerCredentials: ProviderCredentials)
}

