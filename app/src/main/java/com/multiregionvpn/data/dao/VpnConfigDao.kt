package com.multiregionvpn.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.multiregionvpn.data.entity.VpnConfig

@Dao
interface VpnConfigDao {
    @Query("SELECT * FROM vpn_config")
    suspend fun getAll(): List<VpnConfig>

    @Query("SELECT * FROM vpn_config WHERE id = :id")
    suspend fun getById(id: String): VpnConfig?

    @Query("SELECT * FROM vpn_config WHERE regionId = :regionId")
    suspend fun findByRegion(regionId: String): List<VpnConfig>

    @Query("SELECT * FROM vpn_config WHERE regionId = :regionId LIMIT 1")
    suspend fun findFirstByRegion(regionId: String): VpnConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vpnConfig: VpnConfig)

    @Update
    suspend fun update(vpnConfig: VpnConfig)

    @Delete
    suspend fun delete(vpnConfig: VpnConfig)

    @Query("DELETE FROM vpn_config WHERE id = :id")
    suspend fun deleteById(id: String)
}
