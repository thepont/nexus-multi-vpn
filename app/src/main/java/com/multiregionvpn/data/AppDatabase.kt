package com.multiregionvpn.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.multiregionvpn.data.dao.AppRuleDao
import com.multiregionvpn.data.dao.PresetRuleDao
import com.multiregionvpn.data.dao.ProviderCredentialsDao
import com.multiregionvpn.data.dao.VpnConfigDao
import com.multiregionvpn.data.entity.AppRule
import com.multiregionvpn.data.entity.PresetRule
import com.multiregionvpn.data.entity.ProviderCredentials
import com.multiregionvpn.data.entity.VpnConfig

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

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "multi_region_vpn_database"
                )
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Pre-seed preset rules
                            db.execSQL(
                                """
                                INSERT INTO preset_rule (packageName, targetRegionId) VALUES
                                ('com.bbc.iplayer', 'UK'),
                                ('au.com.seven', 'AU'),
                                ('fr.francetv', 'FR'),
                                ('com.tf1.android', 'FR')
                                """.trimIndent()
                            )
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
