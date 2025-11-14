package com.multiregionvpn.data.repository

import com.multiregionvpn.core.VpnEngineService
import com.multiregionvpn.data.database.AppRule
import com.multiregionvpn.data.database.AppRuleDao
import com.multiregionvpn.data.database.ProviderCredentials
import com.multiregionvpn.data.database.ProviderCredentialsDao
import com.multiregionvpn.data.database.VpnConfig
import com.multiregionvpn.data.database.VpnConfigDao
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class SettingsRepositoryTest {

    // Create mocks for all dependencies
    private lateinit var vpnConfigDao: VpnConfigDao
    private lateinit var appRuleDao: AppRuleDao
    private lateinit var credsDao: ProviderCredentialsDao

    // The class we are testing
    private lateinit var repository: SettingsRepository

    private lateinit var presetRuleDao: com.multiregionvpn.data.database.PresetRuleDao

    @Before
    fun setup() {
        vpnConfigDao = mockk()
        appRuleDao = mockk()
        credsDao = mockk()
        presetRuleDao = mockk()
        repository = SettingsRepository(vpnConfigDao, appRuleDao, credsDao, presetRuleDao)
    }

    @Test
    fun `given a valid config, when saveVpnConfig is called, then it calls the DAO's save function`() = runTest {
        // GIVEN: a valid VpnConfig and the DAO's save function is ready
        val config = VpnConfig("test-id", "Test", "UK", "nordvpn", "uk1.com")
        coEvery { vpnConfigDao.save(any()) } returns Unit

        // WHEN: the repository's save function is called
        repository.saveVpnConfig(config)

        // THEN: we verify the DAO's save function was called exactly once with the correct data
        coVerify(exactly = 1) { vpnConfigDao.save(config) }
    }

    @Test
    fun `given a config id, when deleteVpnConfig is called, then it calls the DAO's delete function`() = runTest {
        // GIVEN: a config id
        val configId = "test-id"
        coEvery { vpnConfigDao.delete(any()) } returns Unit

        // WHEN: deleteVpnConfig is called
        repository.deleteVpnConfig(configId)

        // THEN: the DAO's delete function is called with the correct id
        coVerify(exactly = 1) { vpnConfigDao.delete(configId) }
    }

    @Test
    fun `given repository has configs, when getAllVpnConfigs is called, then it returns the Flow from DAO`() = runTest {
        // GIVEN: the DAO returns a flow with configs
        val expectedConfigs = listOf(
            VpnConfig("id1", "UK VPN", "UK", "nordvpn", "uk1.com"),
            VpnConfig("id2", "FR VPN", "FR", "nordvpn", "fr1.com")
        )
        every { vpnConfigDao.getAll() } returns flowOf(expectedConfigs)

        // WHEN: getAllVpnConfigs is called
        val result = repository.getAllVpnConfigs().first()

        // THEN: the flow contains the expected configs
        assertThat(result).hasSize(2)
        assertThat(result).isEqualTo(expectedConfigs)
    }

    @Test
    fun `given a rule exists, when getRuleForPackage is called, then it returns the correct rule`() = runTest {
        // GIVEN: the DAO will return a specific rule
        val expectedRule = AppRule("com.bbc.iplayer", "vpn-uk-id")
        coEvery { appRuleDao.getRuleForPackage("com.bbc.iplayer") } returns expectedRule

        // WHEN: we ask the repository for that rule
        val actualRule = repository.getAppRuleByPackageName("com.bbc.iplayer")

        // THEN: the repository returns the exact rule from the DAO
        assertThat(actualRule).isEqualTo(expectedRule)
    }

    @Test
    fun `given a valid rule, when saveAppRule is called, then it calls the DAO's save function`() = runTest {
        // GIVEN: a valid AppRule
        val rule = AppRule("com.bbc.iplayer", "vpn-uk-id")
        coEvery { appRuleDao.save(any()) } returns Unit

        // WHEN: saveAppRule is called
        repository.saveAppRule(rule)

        // THEN: the DAO's save function is called with the correct rule
        coVerify(exactly = 1) { appRuleDao.save(rule) }
    }

    @Test
    fun `saveAppRule notifies engine service of change`() = runTest {
        mockkObject(VpnEngineService.Companion)
        try {
            val rule = AppRule("com.example.app", "vpn-config-id")
            coEvery { appRuleDao.save(any()) } returns Unit

            repository.saveAppRule(rule)

            verify(exactly = 1) { VpnEngineService.notifyAppRuleChanged("com.example.app", "vpn-config-id") }
        } finally {
            unmockkObject(VpnEngineService.Companion)
        }
    }

    @Test
    fun `given a package name, when deleteAppRule is called, then it calls the DAO's delete function`() = runTest {
        // GIVEN: a package name
        val packageName = "com.bbc.iplayer"
        coEvery { appRuleDao.delete(any()) } returns Unit

        // WHEN: deleteAppRule is called
        repository.deleteAppRule(packageName)

        // THEN: the DAO's delete function is called with the correct package name
        coVerify(exactly = 1) { appRuleDao.delete(packageName) }
    }

    @Test
    fun `deleteAppRule notifies engine service`() = runTest {
        mockkObject(VpnEngineService.Companion)
        try {
            val packageName = "com.example.remove"
            coEvery { appRuleDao.delete(any()) } returns Unit

            repository.deleteAppRule(packageName)

            verify(exactly = 1) { VpnEngineService.notifyAppRuleRemoved(packageName) }
        } finally {
            unmockkObject(VpnEngineService.Companion)
        }
    }

    @Test
    fun `given repository has rules, when getAllAppRules is called, then it returns the Flow from DAO`() = runTest {
        // GIVEN: the DAO returns a flow with rules
        val expectedRules = listOf(
            AppRule("com.bbc.iplayer", "vpn-uk-id"),
            AppRule("com.itv.hub", "vpn-uk-id")
        )
        every { appRuleDao.getAllRules() } returns flowOf(expectedRules)

        // WHEN: getAllAppRules is called
        val result = repository.getAllAppRules().first()

        // THEN: the flow contains the expected rules
        assertThat(result).hasSize(2)
        assertThat(result).isEqualTo(expectedRules)
    }

    @Test
    fun `given credentials exist, when getProviderCredentials is called, then it returns the credentials`() = runTest {
        // GIVEN: the DAO will return credentials
        val expectedCreds = ProviderCredentials("nordvpn", "test-username", "test-password")
        coEvery { credsDao.get("nordvpn") } returns expectedCreds

        // WHEN: getProviderCredentials is called
        val result = repository.getProviderCredentials("nordvpn")

        // THEN: the repository returns the credentials from the DAO
        assertThat(result).isEqualTo(expectedCreds)
        assertThat(result!!.username).isEqualTo("test-username")
        assertThat(result.password).isEqualTo("test-password")
    }

    @Test
    fun `given credentials do not exist, when getProviderCredentials is called, then it returns null`() = runTest {
        // GIVEN: the DAO returns null (no credentials found)
        coEvery { credsDao.get("nordvpn") } returns null

        // WHEN: getProviderCredentials is called
        val result = repository.getProviderCredentials("nordvpn")

        // THEN: the repository returns null
        assertThat(result).isNull()
    }

    @Test
    fun `given valid credentials, when saveProviderCredentials is called, then it calls the DAO's save function`() = runTest {
        // GIVEN: valid credentials
        val creds = ProviderCredentials("nordvpn", "test-username", "test-password")
        coEvery { credsDao.save(any()) } returns Unit

        // WHEN: saveProviderCredentials is called
        repository.saveProviderCredentials(creds)

        // THEN: the DAO's save function is called with the correct credentials
        coVerify(exactly = 1) { credsDao.save(creds) }
    }

    @Test
    fun `given a region id, when findVpnForRegion is called, then it returns the config for that region`() = runTest {
        // GIVEN: the DAO returns a config for the region
        val expectedConfig = VpnConfig("id1", "UK VPN", "UK", "nordvpn", "uk1.com")
        coEvery { vpnConfigDao.findByRegion("UK") } returns expectedConfig

        // WHEN: findVpnForRegion is called
        val result = repository.findVpnForRegion("UK")

        // THEN: the repository returns the config from the DAO
        assertThat(result).isEqualTo(expectedConfig)
    }

    @Test
    fun `createAppRule notifies engine service after persistence`() = runTest {
        mockkObject(VpnEngineService.Companion)
        try {
            coEvery { appRuleDao.save(any()) } returns Unit
            coEvery { appRuleDao.getRuleForPackage("com.example.create") } returns AppRule("com.example.create", "vpn-config-id")

            repository.createAppRule("com.example.create", "vpn-config-id")

            coVerify(exactly = 1) { appRuleDao.getRuleForPackage("com.example.create") }
            verify(exactly = 1) { VpnEngineService.notifyAppRuleChanged("com.example.create", "vpn-config-id") }
        } finally {
            unmockkObject(VpnEngineService.Companion)
        }
    }

    @Test
    fun `updateAppRule notifies engine service`() = runTest {
        mockkObject(VpnEngineService.Companion)
        try {
            coEvery { appRuleDao.save(any()) } returns Unit

            repository.updateAppRule("com.example.update", "vpn-config-id")

            verify(exactly = 1) { VpnEngineService.notifyAppRuleChanged("com.example.update", "vpn-config-id") }
        } finally {
            unmockkObject(VpnEngineService.Companion)
        }
    }

    @Test
    fun `clearAllAppRules notifies engine service`() = runTest {
        mockkObject(VpnEngineService.Companion)
        try {
            coEvery { appRuleDao.clearAll() } returns Unit

            repository.clearAllAppRules()

            verify(exactly = 1) { VpnEngineService.notifyAllAppRulesCleared() }
        } finally {
            unmockkObject(VpnEngineService.Companion)
        }
    }
}
