package com.multiregionvpn.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The main Room database for the application.
 * This class ties all the Entities and DAOs together.
 * 
 * IMPORTANT: This database uses proper migration strategies to preserve user data.
 * Never use fallbackToDestructiveMigration() in production as it will delete all user data.
 * 
 * When changing the schema:
 * 1. Increment the version number
 * 2. Create a migration in Migrations.kt
 * 3. Add the migration using .addMigrations() in getDatabase()
 * 4. Test the migration thoroughly
 */
@Database(
    entities = [
        VpnConfig::class,
        AppRule::class,
        ProviderCredentials::class,
        PresetRule::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    // DAOs
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
                    "region_router_db"
                )
                // Add database migrations to preserve user data during schema changes
                // Example: .addMigrations(Migrations.MIGRATION_1_2, Migrations.MIGRATION_2_3)
                // DO NOT use .fallbackToDestructiveMigration() as it deletes user data
                
                // Add the pre-seeding callback
                .addCallback(PresetRuleCallback(context))
                .build()
                INSTANCE = instance
                instance
            }
        }
    }

    /**
     * Database Callback to pre-seed the PresetRule table on first creation.
     */
    private class PresetRuleCallback(private val context: Context) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            // Pre-seed preset rules using direct SQL insertions
            // This is more reliable than using coroutines in onCreate
            val presetRules = getPresetRules()
            db.beginTransaction()
            try {
                presetRules.forEach { rule ->
                    db.execSQL(
                        "INSERT OR IGNORE INTO preset_rules (packageName, targetRegionId) VALUES (?, ?)",
                        arrayOf(rule.packageName, rule.targetRegionId)
                    )
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
        
        // This is our hardcoded list of known apps and their regions
        private fun getPresetRules(): List<PresetRule> {
            return listOf(
                // --- UK ---
                PresetRule("com.bbc.iplayer", "UK"),
                PresetRule("com.itv.hub", "UK"), // ITVX
                PresetRule("com.channel4.ondemand", "UK"), // Channel 4
                PresetRule("com.channel5.my5", "UK"), // My5

                // --- Australia ---
                PresetRule("au.com.abc.iview", "AU"),
                PresetRule("au.com.sbs.ondemand", "AU"),
                PresetRule("au.com.nine.jumpin", "AU"), // 9Now
                PresetRule("au.com.seven.plus", "AU"), // 7plus
                PresetRule("au.com.networkten.tenplay", "AU"), // 10 Play

                // --- France ---
                PresetRule("fr.francetv.pluzz", "FR"), // France.tv
                PresetRule("fr.tf1.mytf1", "FR"), // TF1+
                PresetRule("fr.m6.m6replay", "FR")  // 6play
            )
        }
    }
}
