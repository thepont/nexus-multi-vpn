package com.multiregionvpn.data.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Tests for database migrations.
 * 
 * These tests ensure that database migrations preserve user data and correctly
 * transform the schema from one version to another.
 * 
 * IMPORTANT: These tests validate that we never lose user data during app updates.
 * All migrations must be tested before release.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    companion object {
        private const val TEST_DB_NAME = "migration-test"
    }

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(), // Auto-migrations not used, we use manual migrations
        FrameworkSQLiteOpenHelperFactory()
    )

    /**
     * Test that database version 1 can be created successfully.
     * This is the baseline test - all migration tests start from version 1.
     */
    @Test
    @Throws(IOException::class)
    fun testDatabaseVersion1Creation() {
        // Create version 1 database
        val db = helper.createDatabase(TEST_DB_NAME, 1)
        
        // Verify all tables exist
        val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table'")
        val tableNames = mutableListOf<String>()
        while (cursor.moveToNext()) {
            tableNames.add(cursor.getString(0))
        }
        cursor.close()
        
        // Check that all expected tables exist
        assertThat(tableNames).contains("vpn_configs")
        assertThat(tableNames).contains("app_rules")
        assertThat(tableNames).contains("provider_credentials")
        assertThat(tableNames).contains("preset_rules")
        
        db.close()
    }

    /**
     * Test that version 1 database can insert and retrieve data.
     * This validates the baseline schema functionality.
     */
    @Test
    @Throws(IOException::class)
    fun testDatabaseVersion1DataIntegrity() {
        val db = helper.createDatabase(TEST_DB_NAME, 1)
        
        // Insert a VPN config
        db.execSQL(
            "INSERT INTO vpn_configs (id, name, regionId, templateId, serverHostname) " +
            "VALUES ('test-id', 'Test VPN', 'UK', 'nordvpn', 'uk123.nordvpn.com')"
        )
        
        // Insert an app rule
        db.execSQL(
            "INSERT INTO app_rules (packageName, vpnConfigId) " +
            "VALUES ('com.bbc.iplayer', 'test-id')"
        )
        
        // Insert provider credentials
        db.execSQL(
            "INSERT INTO provider_credentials (providerId, username, password) " +
            "VALUES ('nordvpn', 'testuser', 'testpass')"
        )
        
        // Verify data can be retrieved
        val vpnCursor = db.query("SELECT * FROM vpn_configs WHERE id = 'test-id'")
        assertThat(vpnCursor.moveToFirst()).isTrue()
        assertThat(vpnCursor.getString(vpnCursor.getColumnIndexOrThrow("name"))).isEqualTo("Test VPN")
        assertThat(vpnCursor.getString(vpnCursor.getColumnIndexOrThrow("regionId"))).isEqualTo("UK")
        vpnCursor.close()
        
        val ruleCursor = db.query("SELECT * FROM app_rules WHERE packageName = 'com.bbc.iplayer'")
        assertThat(ruleCursor.moveToFirst()).isTrue()
        assertThat(ruleCursor.getString(ruleCursor.getColumnIndexOrThrow("vpnConfigId"))).isEqualTo("test-id")
        ruleCursor.close()
        
        val credCursor = db.query("SELECT * FROM provider_credentials WHERE providerId = 'nordvpn'")
        assertThat(credCursor.moveToFirst()).isTrue()
        assertThat(credCursor.getString(credCursor.getColumnIndexOrThrow("username"))).isEqualTo("testuser")
        credCursor.close()
        
        db.close()
    }

    /**
     * Example migration test template for future use.
     * 
     * When creating migration from version 1 to 2:
     * 1. Uncomment this test
     * 2. Update the version numbers
     * 3. Add the migration to the migrations list
     * 4. Test with real data scenarios
     */
    /*
    @Test
    @Throws(IOException::class)
    fun testMigration1To2() {
        // Create version 1 database with test data
        val db = helper.createDatabase(TEST_DB_NAME, 1)
        
        // Insert test data that should survive the migration
        db.execSQL(
            "INSERT INTO vpn_configs (id, name, regionId, templateId, serverHostname) " +
            "VALUES ('test-id', 'Test VPN', 'UK', 'nordvpn', 'uk123.nordvpn.com')"
        )
        db.execSQL(
            "INSERT INTO app_rules (packageName, vpnConfigId) " +
            "VALUES ('com.bbc.iplayer', 'test-id')"
        )
        
        db.close()
        
        // Migrate to version 2
        val migratedDb = helper.runMigrationsAndValidate(
            TEST_DB_NAME, 
            2, 
            true, 
            Migrations.MIGRATION_1_2  // Add your migration here
        )
        
        // Verify data survived migration
        val cursor = migratedDb.query("SELECT * FROM vpn_configs WHERE id = 'test-id'")
        assertThat(cursor.moveToFirst()).isTrue()
        assertThat(cursor.getString(cursor.getColumnIndexOrThrow("name"))).isEqualTo("Test VPN")
        
        // Verify new schema changes (e.g., new columns have correct defaults)
        // Example: if you added a new column 'dns_servers'
        // val dnsServers = cursor.getString(cursor.getColumnIndexOrThrow("dns_servers"))
        // assertThat(dnsServers).isNull() // or whatever the default should be
        
        cursor.close()
        migratedDb.close()
    }
    */

    /**
     * Test that the current database version can be created from scratch.
     * This ensures AppDatabase.kt is correctly configured.
     */
    @Test
    fun testCurrentVersionCreation() {
        // This creates the database using the current AppDatabase configuration
        // Room will use the latest schema
        val db = helper.createDatabase(TEST_DB_NAME, 1) // Update version as schema evolves
        
        // Basic sanity check
        val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table'")
        val tableNames = mutableListOf<String>()
        while (cursor.moveToNext()) {
            tableNames.add(cursor.getString(0))
        }
        cursor.close()
        
        assertThat(tableNames.size).isGreaterThan(0)
        assertThat(tableNames).contains("vpn_configs")
        
        db.close()
    }
}
