package com.multiregionvpn.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The main Room database for the application.
 * This class ties all the Entities and DAOs together.
 */
@Database(
    entities = [
        VpnConfig::class,
        AppRule::class,
        ProviderCredentials::class,
        PresetRule::class,
        ProviderAccountEntity::class,
        ProviderServerCacheEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    // DAOs
    abstract fun vpnConfigDao(): VpnConfigDao
    abstract fun appRuleDao(): AppRuleDao
    abstract fun providerCredentialsDao(): ProviderCredentialsDao
    abstract fun presetRuleDao(): PresetRuleDao
    abstract fun providerAccountDao(): ProviderAccountDao
    abstract fun providerServerCacheDao(): ProviderServerCacheDao

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
                // Add migrations
                .addMigrations(MIGRATION_1_2)
                // Add the pre-seeding callback
                .addCallback(PresetRuleCallback(context))
                .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Migration from version 1 to 2:
         * - Add provider_accounts table
         * - Add provider_server_cache table
         * - Add new columns to app_rules (providerAccountId, regionCode, preferredProtocol, fallbackDirect)
         * - Add new columns to vpn_configs (sourceProviderId, generatedAt)
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create provider_accounts table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS provider_accounts (
                        id TEXT NOT NULL PRIMARY KEY,
                        providerId TEXT NOT NULL,
                        displayLabel TEXT NOT NULL,
                        encryptedCredentials BLOB NOT NULL,
                        lastAuthState TEXT,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())

                // Create provider_server_cache table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS provider_server_cache (
                        id TEXT NOT NULL PRIMARY KEY,
                        providerId TEXT NOT NULL,
                        regionCode TEXT NOT NULL,
                        payloadJson TEXT NOT NULL,
                        fetchedAt INTEGER NOT NULL,
                        ttlSeconds INTEGER NOT NULL,
                        latencyMs INTEGER
                    )
                """.trimIndent())

                // Create indices for provider_server_cache
                database.execSQL("CREATE INDEX IF NOT EXISTS index_provider_server_cache_providerId_regionCode ON provider_server_cache(providerId, regionCode)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_provider_server_cache_fetchedAt ON provider_server_cache(fetchedAt)")

                // Recreate app_rules table with new columns and foreign keys
                // SQLite doesn't support adding foreign keys via ALTER TABLE, so we must recreate
                database.execSQL("""
                    CREATE TABLE app_rules_new (
                        packageName TEXT NOT NULL PRIMARY KEY,
                        vpnConfigId TEXT,
                        providerAccountId TEXT,
                        regionCode TEXT,
                        preferredProtocol TEXT,
                        fallbackDirect INTEGER NOT NULL,
                        FOREIGN KEY(vpnConfigId) REFERENCES vpn_configs(id) ON DELETE SET NULL,
                        FOREIGN KEY(providerAccountId) REFERENCES provider_accounts(id) ON DELETE SET NULL
                    )
                """.trimIndent())

                // Copy existing data
                database.execSQL("""
                    INSERT INTO app_rules_new (packageName, vpnConfigId, providerAccountId, regionCode, preferredProtocol, fallbackDirect)
                    SELECT packageName, vpnConfigId, NULL, NULL, NULL, 0 FROM app_rules
                """.trimIndent())

                // Drop old table and rename new one
                database.execSQL("DROP TABLE app_rules")
                database.execSQL("ALTER TABLE app_rules_new RENAME TO app_rules")

                // Create indices
                database.execSQL("CREATE INDEX IF NOT EXISTS index_app_rules_vpnConfigId ON app_rules(vpnConfigId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_app_rules_providerAccountId ON app_rules(providerAccountId)")

                // Add new columns to vpn_configs
                database.execSQL("ALTER TABLE vpn_configs ADD COLUMN sourceProviderId TEXT")
                database.execSQL("ALTER TABLE vpn_configs ADD COLUMN generatedAt INTEGER")
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
