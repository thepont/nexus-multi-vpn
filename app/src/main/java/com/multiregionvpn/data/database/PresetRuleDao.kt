package com.multiregionvpn.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PresetRuleDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(rules: List<PresetRule>)

    @Query("SELECT * FROM preset_rules")
    fun getAll(): Flow<List<PresetRule>>
    
    @Query("SELECT * FROM preset_rules WHERE packageName = :packageName LIMIT 1")
    suspend fun findByPackageName(packageName: String): PresetRule?
}
