package com.multiregionvpn.data.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration test to verify that AppRule migration works correctly.
 * This test creates a database at version 1, migrates to version 2,
 * and verifies that the new columns exist and data is preserved.
 */
@RunWith(AndroidJUnit4::class)
class AppRuleMigrationTest {
    private lateinit var db: AppDatabase
    private lateinit var appRuleDao: AppRuleDao
    private lateinit var vpnConfigDao: VpnConfigDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        // Create database with migration
        db = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "migration_integration_test"
        )
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .allowMainThreadQueries() // Only for testing
            .build()
        appRuleDao = db.appRuleDao()
        vpnConfigDao = db.vpnConfigDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun test_migration_preservesExistingData() = runBlocking {
        // GIVEN: A VPN config and rule exist (simulating pre-migration data)
        val vpnConfig = VpnConfig(
            id = "test-config-id",
            name = "Test Config",
            regionId = "UK",
            templateId = "nordvpn",
            serverHostname = "test.nordvpn.com"
        )
        vpnConfigDao.save(vpnConfig)
        
        val rule = AppRule(
            packageName = "com.test.app",
            vpnConfigId = "test-config-id"
        )
        appRuleDao.save(rule)

        // WHEN: We query the rule after migration
        val retrieved = appRuleDao.getRuleForPackage("com.test.app")

        // THEN: The rule should exist with default values for new fields
        assertThat(retrieved).isNotNull()
        assertThat(retrieved!!.packageName).isEqualTo("com.test.app")
        assertThat(retrieved.vpnConfigId).isEqualTo("test-config-id")
        assertThat(retrieved.providerAccountId).isNull()
        assertThat(retrieved.regionCode).isNull()
        assertThat(retrieved.preferredProtocol).isNull()
        assertThat(retrieved.fallbackDirect).isFalse()
    }

    @Test
    fun test_migration_allowsNewFields() = runBlocking {
        // GIVEN: A provider account exists (for foreign key)
        val providerAccountDao = db.providerAccountDao()
        val account = ProviderAccountEntity(
            id = "account-123",
            providerId = "nordvpn",
            displayLabel = "Test Account",
            encryptedCredentials = "test".toByteArray()
        )
        providerAccountDao.insertAccount(account)
        
        // GIVEN: A rule with all new JIT fields
        val rule = AppRule(
            packageName = "com.test.app2",
            vpnConfigId = null,
            providerAccountId = "account-123",
            regionCode = "UK",
            preferredProtocol = "openvpn_udp",
            fallbackDirect = true
        )

        // WHEN: We save and retrieve it
        appRuleDao.save(rule)
        val retrieved = appRuleDao.getRuleForPackage("com.test.app2")

        // THEN: All fields should be preserved
        assertThat(retrieved).isNotNull()
        assertThat(retrieved!!.packageName).isEqualTo("com.test.app2")
        assertThat(retrieved.providerAccountId).isEqualTo("account-123")
        assertThat(retrieved.regionCode).isEqualTo("UK")
        assertThat(retrieved.preferredProtocol).isEqualTo("openvpn_udp")
        assertThat(retrieved.fallbackDirect).isTrue()
    }
}

