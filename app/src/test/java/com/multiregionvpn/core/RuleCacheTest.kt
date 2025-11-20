package com.multiregionvpn.core

import com.google.common.truth.Truth.assertThat
import com.multiregionvpn.MainCoroutineRule
import com.multiregionvpn.data.database.AppRule
import com.multiregionvpn.data.database.AppRuleDao
import com.multiregionvpn.data.database.VpnConfig
import com.multiregionvpn.data.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for RuleCache - in-memory caching layer for packet routing rules.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RuleCacheTest {

    @get:Rule
    val coroutineRule = MainCoroutineRule()

    private lateinit var mockSettingsRepo: SettingsRepository
    private lateinit var mockAppRuleDao: AppRuleDao
    private lateinit var rulesFlow: MutableStateFlow<List<AppRule>>
    private lateinit var ruleCache: RuleCache

    @Before
    fun setup() {
        mockSettingsRepo = mockk()
        mockAppRuleDao = mockk()
        rulesFlow = MutableStateFlow(emptyList())
        
        // Mock the SettingsRepository to return our flow and DAO
        every { mockSettingsRepo.appRuleDao } returns mockAppRuleDao
        every { mockSettingsRepo.getAllAppRules() } returns rulesFlow
        
        // Mock DAO to return empty list initially
        coEvery { mockAppRuleDao.getAllRulesList() } returns emptyList()
    }

    @Test
    fun `given no rules, when getTunnelIdForPackage is called, then returns null`() = runTest {
        // GIVEN: Empty rules
        ruleCache = RuleCache(mockSettingsRepo)
        advanceUntilIdle() // Allow initialization to complete
        
        // WHEN: Looking up a package
        val result = ruleCache.getTunnelIdForPackage("com.test.app")
        
        // THEN: Returns null (no rule exists)
        assertThat(result).isNull()
    }

    @Test
    fun `given rule exists, when getTunnelIdForPackage is called, then returns tunnel ID`() = runTest {
        // GIVEN: One rule exists
        val vpnConfigId = "vpn-uk-id"
        val packageName = "com.test.app"
        val vpnConfig = VpnConfig(
            id = vpnConfigId,
            name = "UK VPN",
            regionId = "UK",
            templateId = "nordvpn",
            serverHostname = "uk.nordvpn.com"
        )
        val appRule = AppRule(packageName = packageName, vpnConfigId = vpnConfigId)
        
        // Mock DAO to return the rule
        coEvery { mockAppRuleDao.getAllRulesList() } returns listOf(appRule)
        
        // Mock getVpnConfigById to return the config
        coEvery { mockSettingsRepo.getVpnConfigById(vpnConfigId) } returns vpnConfig
        
        // Create cache (will initialize with rule)
        ruleCache = RuleCache(mockSettingsRepo)
        advanceUntilIdle() // Allow initialization to complete
        
        // WHEN: Looking up the package
        val result = ruleCache.getTunnelIdForPackage(packageName)
        
        // THEN: Returns the correct tunnel ID
        assertThat(result).isEqualTo("nordvpn_UK")
    }

    @Test
    fun `given multiple rules, when getTunnelIdForPackage is called, then returns correct tunnel IDs`() = runTest {
        // GIVEN: Multiple rules for different packages
        val vpnConfigUk = VpnConfig("vpn-uk", "UK VPN", "UK", "nordvpn", "uk.nordvpn.com")
        val vpnConfigFr = VpnConfig("vpn-fr", "FR VPN", "FR", "nordvpn", "fr.nordvpn.com")
        
        val appRule1 = AppRule("com.app1", "vpn-uk")
        val appRule2 = AppRule("com.app2", "vpn-fr")
        val appRule3 = AppRule("com.app3", null) // No VPN config
        
        coEvery { mockAppRuleDao.getAllRulesList() } returns listOf(appRule1, appRule2, appRule3)
        coEvery { mockSettingsRepo.getVpnConfigById("vpn-uk") } returns vpnConfigUk
        coEvery { mockSettingsRepo.getVpnConfigById("vpn-fr") } returns vpnConfigFr
        
        ruleCache = RuleCache(mockSettingsRepo)
        advanceUntilIdle()
        
        // WHEN/THEN: Each package returns correct tunnel ID
        assertThat(ruleCache.getTunnelIdForPackage("com.app1")).isEqualTo("nordvpn_UK")
        assertThat(ruleCache.getTunnelIdForPackage("com.app2")).isEqualTo("nordvpn_FR")
        assertThat(ruleCache.getTunnelIdForPackage("com.app3")).isNull() // No config
        assertThat(ruleCache.getTunnelIdForPackage("com.unknown")).isNull() // Not in cache
    }

    @Test
    fun `given rules change via Flow, when cache updates, then getTunnelIdForPackage returns new values`() = runTest {
        // GIVEN: Initial empty cache
        coEvery { mockAppRuleDao.getAllRulesList() } returns emptyList()
        ruleCache = RuleCache(mockSettingsRepo)
        advanceUntilIdle()
        
        // Verify initially empty
        assertThat(ruleCache.getTunnelIdForPackage("com.test.app")).isNull()
        
        // WHEN: Rules are updated via Flow
        val vpnConfig = VpnConfig("vpn-uk", "UK VPN", "UK", "nordvpn", "uk.nordvpn.com")
        val newRule = AppRule("com.test.app", "vpn-uk")
        
        coEvery { mockSettingsRepo.getVpnConfigById("vpn-uk") } returns vpnConfig
        rulesFlow.value = listOf(newRule)
        advanceUntilIdle()
        
        // THEN: Cache is updated with new rule
        assertThat(ruleCache.getTunnelIdForPackage("com.test.app")).isEqualTo("nordvpn_UK")
    }

    @Test
    fun `given rule is removed via Flow, when cache updates, then getTunnelIdForPackage returns null`() = runTest {
        // GIVEN: Cache with one rule
        val vpnConfig = VpnConfig("vpn-uk", "UK VPN", "UK", "nordvpn", "uk.nordvpn.com")
        val appRule = AppRule("com.test.app", "vpn-uk")
        
        coEvery { mockAppRuleDao.getAllRulesList() } returns listOf(appRule)
        coEvery { mockSettingsRepo.getVpnConfigById("vpn-uk") } returns vpnConfig
        
        ruleCache = RuleCache(mockSettingsRepo)
        advanceUntilIdle()
        
        // Verify rule exists
        assertThat(ruleCache.getTunnelIdForPackage("com.test.app")).isEqualTo("nordvpn_UK")
        
        // WHEN: Rule is removed via Flow
        rulesFlow.value = emptyList()
        advanceUntilIdle()
        
        // THEN: Cache no longer contains the rule
        assertThat(ruleCache.getTunnelIdForPackage("com.test.app")).isNull()
    }

    @Test
    fun `given clear is called, when getTunnelIdForPackage is called, then returns null`() = runTest {
        // GIVEN: Cache with rules
        val vpnConfig = VpnConfig("vpn-uk", "UK VPN", "UK", "nordvpn", "uk.nordvpn.com")
        val appRule = AppRule("com.test.app", "vpn-uk")
        
        coEvery { mockAppRuleDao.getAllRulesList() } returns listOf(appRule)
        coEvery { mockSettingsRepo.getVpnConfigById("vpn-uk") } returns vpnConfig
        
        ruleCache = RuleCache(mockSettingsRepo)
        advanceUntilIdle()
        
        // Verify rule exists
        assertThat(ruleCache.getTunnelIdForPackage("com.test.app")).isEqualTo("nordvpn_UK")
        
        // WHEN: Cache is cleared
        ruleCache.clear()
        
        // THEN: All rules are removed
        assertThat(ruleCache.getTunnelIdForPackage("com.test.app")).isNull()
    }

    @Test
    fun `given VPN config is missing, when cache initializes, then package has no tunnel ID`() = runTest {
        // GIVEN: Rule exists but VPN config doesn't
        val appRule = AppRule("com.test.app", "vpn-missing")
        
        coEvery { mockAppRuleDao.getAllRulesList() } returns listOf(appRule)
        coEvery { mockSettingsRepo.getVpnConfigById("vpn-missing") } returns null
        
        // WHEN: Cache initializes
        ruleCache = RuleCache(mockSettingsRepo)
        advanceUntilIdle()
        
        // THEN: Package has no tunnel ID (config not found)
        assertThat(ruleCache.getTunnelIdForPackage("com.test.app")).isNull()
    }

    @Test
    fun `given rule update changes VPN config, when cache updates, then tunnel ID changes`() = runTest {
        // GIVEN: Rule with UK VPN
        val vpnConfigUk = VpnConfig("vpn-uk", "UK VPN", "UK", "nordvpn", "uk.nordvpn.com")
        val vpnConfigFr = VpnConfig("vpn-fr", "FR VPN", "FR", "nordvpn", "fr.nordvpn.com")
        val appRule = AppRule("com.test.app", "vpn-uk")
        
        coEvery { mockAppRuleDao.getAllRulesList() } returns listOf(appRule)
        coEvery { mockSettingsRepository.getVpnConfigById("vpn-uk") } returns vpnConfigUk
        coEvery { mockSettingsRepo.getVpnConfigById("vpn-fr") } returns vpnConfigFr
        
        ruleCache = RuleCache(mockSettingsRepo)
        advanceUntilIdle()
        
        // Verify UK tunnel
        assertThat(ruleCache.getTunnelIdForPackage("com.test.app")).isEqualTo("nordvpn_UK")
        
        // WHEN: Rule updated to FR VPN
        val updatedRule = AppRule("com.test.app", "vpn-fr")
        rulesFlow.value = listOf(updatedRule)
        advanceUntilIdle()
        
        // THEN: Tunnel ID changes to FR
        assertThat(ruleCache.getTunnelIdForPackage("com.test.app")).isEqualTo("nordvpn_FR")
    }
}
