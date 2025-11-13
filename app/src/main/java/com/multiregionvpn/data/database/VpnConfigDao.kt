package com.multiregionvpn.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface VpnConfigDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(config: VpnConfig)

    @Update
    suspend fun update(config: VpnConfig)

    @Query("DELETE FROM vpn_configs WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM vpn_configs WHERE regionId = :regionId LIMIT 1")
    suspend fun findByRegion(regionId: String): VpnConfig?

    @Query("SELECT * FROM vpn_configs WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): VpnConfig?

    @Query("SELECT * FROM vpn_configs ORDER BY name ASC")
    fun getAll(): Flow<List<VpnConfig>>
    
    @Query("DELETE FROM vpn_configs")
    suspend fun clearAll()
}
