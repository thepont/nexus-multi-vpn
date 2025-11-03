package com.multiregionvpn.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.multiregionvpn.data.entity.PresetRule

@Dao
interface PresetRuleDao {
    @Query("SELECT * FROM preset_rule")
    suspend fun getAll(): List<PresetRule>

    @Query("SELECT * FROM preset_rule WHERE packageName = :packageName")
    suspend fun getByPackageName(packageName: String): PresetRule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(presetRule: PresetRule)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(presetRules: List<PresetRule>)
}
