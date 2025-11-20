package com.multiregionvpn.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Database migrations for AppDatabase.
 * 
 * Each migration represents a schema change between two database versions.
 * Migrations are required to preserve user data during app updates.
 * 
 * IMPORTANT: Never use fallbackToDestructiveMigration() in production code,
 * as it will delete all user data if a migration is missing.
 * 
 * Example migration from version 1 to 2:
 * ```
 * val MIGRATION_1_2 = object : Migration(1, 2) {
 *     override fun migrate(database: SupportSQLiteDatabase) {
 *         // Add new column to existing table
 *         database.execSQL("ALTER TABLE vpn_configs ADD COLUMN dns_servers TEXT DEFAULT NULL")
 *     }
 * }
 * ```
 * 
 * To add a migration:
 * 1. Increment the database version in AppDatabase
 * 2. Create a new Migration object (e.g., MIGRATION_2_3)
 * 3. Add it to AppDatabase.getDatabase() using .addMigrations()
 * 4. Write tests to verify the migration works correctly
 * 5. Export the schema for version control (see AppDatabase exportSchema setting)
 */
object Migrations {
    
    // Future migrations will be defined here
    // Example:
    // val MIGRATION_1_2 = object : Migration(1, 2) {
    //     override fun migrate(database: SupportSQLiteDatabase) {
    //         // Migration SQL goes here
    //     }
    // }
}
