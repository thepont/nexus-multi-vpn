package com.multiregionvpn.data.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VpnConfigDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var vpnConfigDao: VpnConfigDao

    @Before
    fun setup() {
        // Create an in-memory database that disappears after the test
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries() // Only for testing!
            .build()
        vpnConfigDao = db.vpnConfigDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun test_givenDatabaseIsEmpty_whenVpnConfigIsSaved_thenConfigCanBeRetrieved() = runTest {
        // GIVEN: an empty DB (from setup) and a new config
        val newConfig = VpnConfig(
            id = "test-uuid-1",
            name = "My Test UK VPN",
            regionId = "UK",
            templateId = "nordvpn",
            serverHostname = "uk1234.nordvpn.com"
        )

        // WHEN: the config is saved
        vpnConfigDao.save(newConfig)

        // THEN: the config can be retrieved
        val configs = vpnConfigDao.getAll().first() // .first() gets the first value from the Flow
        
        assertThat(configs).hasSize(1)
        assertThat(configs.first().name).isEqualTo("My Test UK VPN")
        assertThat(configs.first().regionId).isEqualTo("UK")
        assertThat(configs.first().serverHostname).isEqualTo("uk1234.nordvpn.com")
    }

    @Test
    fun test_givenConfigExists_whenItIsDeleted_thenConfigListIsEmpty() = runTest {
        // GIVEN: a config exists in the database
        val config = VpnConfig("test-uuid-1", "Test", "UK", "nordvpn", "uk1.com")
        vpnConfigDao.save(config)

        // WHEN: that config is deleted
        vpnConfigDao.delete("test-uuid-1")

        // THEN: the config list is empty
        val configs = vpnConfigDao.getAll().first()
        assertThat(configs).isEmpty()
    }

    @Test
    fun test_givenMultipleConfigsExist_whenFindByRegionIsCalled_thenOnlyConfigsForThatRegionAreReturned() = runTest {
        // GIVEN: multiple configs exist for different regions
        val ukConfig = VpnConfig("id1", "UK VPN", "UK", "nordvpn", "uk1.com")
        val frConfig = VpnConfig("id2", "FR VPN", "FR", "nordvpn", "fr1.com")
        val auConfig = VpnConfig("id3", "AU VPN", "AU", "nordvpn", "au1.com")
        
        vpnConfigDao.save(ukConfig)
        vpnConfigDao.save(frConfig)
        vpnConfigDao.save(auConfig)

        // WHEN: we search for UK region
        val result = vpnConfigDao.findByRegion("UK")

        // THEN: only the UK config is returned
        assertThat(result).isNotNull()
        assertThat(result!!.regionId).isEqualTo("UK")
        assertThat(result.name).isEqualTo("UK VPN")
    }

    @Test
    fun test_givenConfigExists_whenItIsUpdated_thenUpdatedValuesAreSaved() = runTest {
        // GIVEN: a config exists
        val config = VpnConfig("test-uuid-1", "Original Name", "UK", "nordvpn", "uk1.com")
        vpnConfigDao.save(config)

        // WHEN: the config is updated
        val updatedConfig = config.copy(name = "Updated Name", serverHostname = "uk999.com")
        vpnConfigDao.update(updatedConfig)

        // THEN: the updated values are persisted
        val savedConfig = vpnConfigDao.getAll().first().first()
        assertThat(savedConfig.name).isEqualTo("Updated Name")
        assertThat(savedConfig.serverHostname).isEqualTo("uk999.com")
    }
}
