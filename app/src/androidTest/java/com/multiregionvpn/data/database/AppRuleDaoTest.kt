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
class AppRuleDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var appRuleDao: AppRuleDao
    private lateinit var vpnConfigDao: VpnConfigDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        appRuleDao = db.appRuleDao()
        vpnConfigDao = db.vpnConfigDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun test_givenDatabaseIsEmpty_whenAppRuleIsSaved_thenRuleCanBeRetrieved() = runTest {
        // GIVEN: an empty DB, a VPN config exists, and a new rule
        val vpnConfig = VpnConfig("vpn-uk-id", "UK VPN", "UK", "nordvpn", "uk1.com")
        vpnConfigDao.save(vpnConfig)
        
        val newRule = AppRule(
            packageName = "com.bbc.iplayer",
            vpnConfigId = "vpn-uk-id"
        )

        // WHEN: the rule is saved
        appRuleDao.save(newRule)

        // THEN: the rule can be retrieved
        val retrievedRule = appRuleDao.getRuleForPackage("com.bbc.iplayer")
        
        assertThat(retrievedRule).isNotNull()
        assertThat(retrievedRule!!.packageName).isEqualTo("com.bbc.iplayer")
        assertThat(retrievedRule.vpnConfigId).isEqualTo("vpn-uk-id")
    }

    @Test
    fun test_givenRuleExists_whenItIsDeleted_thenGetRuleForPackageReturnsNull() = runTest {
        // GIVEN: a VPN config and rule exist
        val vpnConfig = VpnConfig("vpn-uk-id", "UK VPN", "UK", "nordvpn", "uk1.com")
        vpnConfigDao.save(vpnConfig)
        val rule = AppRule("com.bbc.iplayer", "vpn-uk-id")
        appRuleDao.save(rule)

        // WHEN: the rule is deleted
        appRuleDao.delete("com.bbc.iplayer")

        // THEN: getRuleForPackage returns null
        val result = appRuleDao.getRuleForPackage("com.bbc.iplayer")
        assertThat(result).isNull()
    }

    @Test
    fun test_givenMultipleRulesExist_whenGetAllRulesIsCalled_thenAllRulesAreReturned() = runTest {
        // GIVEN: VPN config and multiple rules exist
        val vpnConfig = VpnConfig("vpn-uk-id", "UK VPN", "UK", "nordvpn", "uk1.com")
        vpnConfigDao.save(vpnConfig)
        
        val rule1 = AppRule("com.bbc.iplayer", "vpn-uk-id")
        val rule2 = AppRule("com.itv.hub", "vpn-uk-id")
        val rule3 = AppRule("fr.francetv.pluzz", null) // Direct internet
        
        appRuleDao.save(rule1)
        appRuleDao.save(rule2)
        appRuleDao.save(rule3)

        // WHEN: getAllRules is called
        val allRules = appRuleDao.getAllRules().first()

        // THEN: all rules are returned
        assertThat(allRules).hasSize(3)
        assertThat(allRules.map { it.packageName }).containsExactly(
            "com.bbc.iplayer",
            "com.itv.hub",
            "fr.francetv.pluzz"
        )
    }

    @Test
    fun test_givenRulesExist_whenClearAllIsCalled_thenAllRulesAreRemoved() = runTest {
        // GIVEN: VPN config and multiple rules exist
        val vpnConfig = VpnConfig("vpn-uk-id", "UK VPN", "UK", "nordvpn", "uk1.com")
        vpnConfigDao.save(vpnConfig)
        appRuleDao.save(AppRule("com.bbc.iplayer", "vpn-uk-id"))
        appRuleDao.save(AppRule("com.itv.hub", "vpn-uk-id"))

        // WHEN: clearAll is called
        appRuleDao.clearAll()

        // THEN: all rules are removed
        val allRules = appRuleDao.getAllRules().first()
        assertThat(allRules).isEmpty()
    }

    @Test
    fun test_givenRuleWithNullVpnConfigId_whenSaved_thenItRepresentsDirectInternetRouting() = runTest {
        // GIVEN: a rule with null vpnConfigId (direct internet)
        val rule = AppRule("com.example.app", null)

        // WHEN: the rule is saved
        appRuleDao.save(rule)

        // THEN: the rule can be retrieved with null vpnConfigId
        val retrievedRule = appRuleDao.getRuleForPackage("com.example.app")
        assertThat(retrievedRule).isNotNull()
        assertThat(retrievedRule!!.vpnConfigId).isNull()
    }
}
