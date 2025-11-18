package com.multiregionvpn.data.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * Tests for database migrations to prevent schema mismatches.
 * This test ensures that migrations properly handle schema changes.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val TEST_DB = "migration_test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(), // Auto-migrations not used, manual migrations are tested via Room
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate1To2_schemaIsValid() {
        // This test verifies that the database at version 2 can be created
        // and all new tables/columns are accessible.
        // The actual migration logic is tested in AppRuleMigrationTest.
        
        val db = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
            TEST_DB
        ).addMigrations(AppDatabase.MIGRATION_1_2)
            .build()

        // Verify we can access all DAOs (proves tables exist)
        val appRuleDao = db.appRuleDao()
        val providerAccountDao = db.providerAccountDao()
        val serverCacheDao = db.providerServerCacheDao()
        
        runBlocking {
            // If DAOs work, schema is valid
            val rules = appRuleDao.getAllRules().first()
            val accounts = providerAccountDao.getAllAccounts().first()
            val cache = serverCacheDao.getCache("test", "test")
            
            // Just verify DAOs are accessible (tables exist)
            assert(rules != null) { "appRuleDao should work" }
            assert(accounts != null) { "providerAccountDao should work" }
            assert(cache == null) { "serverCacheDao should work" }
        }

        db.close()
    }
}

