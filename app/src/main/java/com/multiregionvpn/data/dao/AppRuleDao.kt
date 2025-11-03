package com.multiregionvpn.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.multiregionvpn.data.entity.AppRule

@Dao
interface AppRuleDao {
    @Query("SELECT * FROM app_rule")
    suspend fun getAll(): List<AppRule>

    @Query("SELECT * FROM app_rule WHERE packageName = :packageName")
    suspend fun getByPackageName(packageName: String): AppRule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(appRule: AppRule)

    @Delete
    suspend fun delete(appRule: AppRule)

    @Query("DELETE FROM app_rule WHERE packageName = :packageName")
    suspend fun deleteByPackageName(packageName: String)
}
