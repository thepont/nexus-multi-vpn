package com.multiregionvpn.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.multiregionvpn.data.dao.AppRuleDao
import com.multiregionvpn.data.dao.PresetRuleDao
import com.multiregionvpn.data.dao.ProviderCredentialsDao
import com.multiregionvpn.data.dao.VpnConfigDao
import com.multiregionvpn.data.entity.AppRule
import com.multiregionvpn.data.entity.PresetRule
import com.multiregionvpn.data.entity.ProviderCredentials
import com.multiregionvpn.data.entity.VpnConfig

/**
 * Legacy AppDatabase - DEPRECATED
 * Use com.multiregionvpn.data.database.AppDatabase instead
 * This file is kept for backwards compatibility but should not be used
 */
@Database(
    entities = [VpnConfig::class, AppRule::class, ProviderCredentials::class, PresetRule::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vpnConfigDao(): VpnConfigDao
    abstract fun appRuleDao(): AppRuleDao
    abstract fun providerCredentialsDao(): ProviderCredentialsDao
    abstract fun presetRuleDao(): PresetRuleDao
}

