package com.multiregionvpn.ui.settings

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.common.truth.Truth.assertThat
import com.multiregionvpn.MainCoroutineRule
import com.multiregionvpn.data.database.AppRule
import com.multiregionvpn.data.database.ProviderCredentials
import com.multiregionvpn.data.database.VpnConfig
import com.multiregionvpn.data.repository.SettingsRepository
import com.multiregionvpn.network.NordVpnApiService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {

    @get:Rule
    val coroutineRule = MainCoroutineRule() // Manages coroutine dispatchers
    
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule() // For LiveData/ViewModel

    // Mocks
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var nordVpnApiService: NordVpnApiService
    private lateinit var application: Application
    private lateinit var packageManager: PackageManager
    private lateinit var localBroadcastManager: LocalBroadcastManager

    // The class we are testing
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setup() {
        settingsRepo = mockk(relaxed = true)
        nordVpnApiService = mockk(relaxed = true)
        application = mockk(relaxed = true)
        packageManager = mockk(relaxed = true)
        localBroadcastManager = mockk(relaxed = true)
        
        mockkStatic(LocalBroadcastManager::class)
        
        // Mock the application context and package manager
        every { application.packageManager } returns packageManager
        every { application.applicationContext } returns application
        every { application.packageName } returns "com.multiregionvpn"
        every { LocalBroadcastManager.getInstance(application) } returns localBroadcastManager
        
        // Mock the return of getInstalledApplications (empty list by default)
        every { packageManager.getInstalledApplications(PackageManager.GET_META_DATA) } returns emptyList()
    }
    
    @org.junit.After
    fun tearDown() {
        unmockkStatic(LocalBroadcastManager::class)
    }
    
    private fun createViewModel() {
        viewModel = SettingsViewModel(settingsRepo, nordVpnApiService, application)
    }

    @Test
    fun `given repository has data, when ViewModel is initialized, then uiState is updated with all data`() = runTest {
        // GIVEN: We define what the repository will return
        val testConfigs = listOf(
            VpnConfig("id1", "UK VPN", "UK", "nordvpn", "uk1.com"),
            VpnConfig("id2", "FR VPN", "FR", "nordvpn", "fr1.com")
        )
        val testRules = listOf(
            AppRule("com.bbc.iplayer", "id1"),
            AppRule("com.itv.hub", "id1")
        )
        val testCreds = ProviderCredentials("nordvpn", "test_username", "test_password")
        
        // Mock the flows and suspend functions
        every { settingsRepo.getAllVpnConfigs() } returns flowOf(testConfigs)
        every { settingsRepo.getAllAppRules() } returns flowOf(testRules)
        coEvery { settingsRepo.getProviderCredentials("nordvpn") } returns testCreds
        
        // WHEN: The ViewModel is created
        createViewModel()

        // Give it a moment to process the flows
        kotlinx.coroutines.delay(100)

        // THEN: The uiState is updated correctly after the combine()
        val state = viewModel.uiState.value
        
        assertThat(state.isLoading).isFalse()
        assertThat(state.vpnConfigs).isEqualTo(testConfigs)
        assertThat(state.nordCredentials?.username).isNotNull()
        assertThat(state.appRules).hasSize(2)
        assertThat(state.appRules).containsEntry("com.bbc.iplayer", "id1")
        assertThat(state.appRules).containsEntry("com.itv.hub", "id1")
    }

    @Test
    fun `given user enters a token, when saveNordToken is called, then repository is called and state is updated`() = runTest {
        // GIVEN: A loaded ViewModel
        every { settingsRepo.getAllVpnConfigs() } returns flowOf(emptyList())
        every { settingsRepo.getAllAppRules() } returns flowOf(emptyList())
        coEvery { settingsRepo.getProviderCredentials(any()) } returns null
        coEvery { settingsRepo.saveProviderCredentials(any()) } returns Unit
        
        createViewModel()
        
        // Give it a moment to initialize
        kotlinx.coroutines.delay(100)

        // WHEN: the user saves new credentials
        val username = "my-username"
        val password = "my-password"
        viewModel.saveNordCredentials(username, password)

        // Give it a moment to process
        kotlinx.coroutines.delay(100)

        // THEN: 
        // 1. The repository's save function was called with the correct data
        coVerify(exactly = 1) { 
            settingsRepo.saveProviderCredentials(
                ProviderCredentials(templateId = "nordvpn", username = username, password = password)
            ) 
        }
        
        // 2. The UI state is updated
        assertThat(viewModel.uiState.value.nordCredentials?.username).isEqualTo(username)
    }

    @Test
    fun `given a valid VpnConfig, when saveVpnConfig is called, then repository save is invoked`() = runTest {
        // GIVEN: A loaded ViewModel
        every { settingsRepo.getAllVpnConfigs() } returns flowOf(emptyList())
        every { settingsRepo.getAllAppRules() } returns flowOf(emptyList())
        coEvery { settingsRepo.getProviderCredentials(any()) } returns null
        coEvery { settingsRepo.saveVpnConfig(any()) } returns Unit
        
        createViewModel()
        kotlinx.coroutines.delay(100)

        // WHEN: saveVpnConfig is called
        val config = VpnConfig("new-id", "New VPN", "UK", "nordvpn", "uk1.com")
        viewModel.saveVpnConfig(config)

        // THEN: The repository's save function is called
        coVerify(exactly = 1) { settingsRepo.saveVpnConfig(config) }
    }

    @Test
    fun `given a config id, when deleteVpnConfig is called, then repository delete is invoked`() = runTest {
        // GIVEN: A loaded ViewModel
        every { settingsRepo.getAllVpnConfigs() } returns flowOf(emptyList())
        every { settingsRepo.getAllAppRules() } returns flowOf(emptyList())
        coEvery { settingsRepo.getProviderCredentials(any()) } returns null
        coEvery { settingsRepo.deleteVpnConfig(any()) } returns Unit
        
        createViewModel()
        kotlinx.coroutines.delay(100)

        // WHEN: deleteVpnConfig is called
        val configId = "test-id"
        viewModel.deleteVpnConfig(configId)

        // THEN: The repository's delete function is called
        coVerify(exactly = 1) { settingsRepo.deleteVpnConfig(configId) }
    }

    @Test
    fun `given a package name and vpn config id, when saveAppRule is called, then repository save is invoked`() = runTest {
        // GIVEN: A loaded ViewModel
        every { settingsRepo.getAllVpnConfigs() } returns flowOf(emptyList())
        every { settingsRepo.getAllAppRules() } returns flowOf(emptyList())
        coEvery { settingsRepo.getProviderCredentials(any()) } returns null
        coEvery { settingsRepo.createAppRule(any(), any()) } returns Unit
        
        createViewModel()
        kotlinx.coroutines.delay(100)

        // WHEN: saveAppRule is called
        val packageName = "com.bbc.iplayer"
        val vpnConfigId = "vpn-uk-id"
        viewModel.saveAppRule(packageName, vpnConfigId)

        // THEN: The repository's save function is called (creates rule)
        coVerify(exactly = 1) { 
            settingsRepo.createAppRule(packageName, vpnConfigId)
        }
    }

    @Test
    fun `given installed apps exist, when ViewModel initializes, then installedApps list contains non-system apps`() = runTest {
        // GIVEN: Package manager returns some apps
        val appInfo1 = mockk<ApplicationInfo>().apply {
            packageName = "com.user.app1"
            flags = 0 // Not a system app
        }
        val appInfo2 = mockk<ApplicationInfo>().apply {
            packageName = "android.system"
            flags = ApplicationInfo.FLAG_SYSTEM // System app
        }
        
        every { packageManager.getInstalledApplications(PackageManager.GET_META_DATA) } returns listOf(appInfo1, appInfo2)
        every { appInfo1.loadLabel(packageManager) } returns "User App 1"
        every { appInfo2.loadLabel(packageManager) } returns "System App"
        every { appInfo1.loadIcon(packageManager) } returns mockk()
        every { appInfo2.loadIcon(packageManager) } returns mockk()
        
        every { settingsRepo.getAllVpnConfigs() } returns flowOf(emptyList())
        every { settingsRepo.getAllAppRules() } returns flowOf(emptyList())
        coEvery { settingsRepo.getProviderCredentials(any()) } returns null

        // WHEN: ViewModel is initialized
        createViewModel()
        kotlinx.coroutines.delay(100)

        // THEN: Only non-system apps are in the list
        val state = viewModel.uiState.value
        assertThat(state.installedApps).hasSize(1)
        assertThat(state.installedApps.first().packageName).isEqualTo("com.user.app1")
    }

    @Test
    fun `given repository returns no token, when ViewModel initializes, then nordToken is null`() = runTest {
        // GIVEN: Repository returns no token
        every { settingsRepo.getAllVpnConfigs() } returns flowOf(emptyList())
        every { settingsRepo.getAllAppRules() } returns flowOf(emptyList())
        coEvery { settingsRepo.getProviderCredentials("nordvpn") } returns null

        // WHEN: ViewModel is initialized
        createViewModel()
        kotlinx.coroutines.delay(100)

        // THEN: nordCredentials is null
        assertThat(viewModel.uiState.value.nordCredentials).isNull()
    }

    @Test
    fun `given app rules exist, when ViewModel initializes, then uiState appRules map is populated`() = runTest {
        // GIVEN: Repository returns rules
        val rules = listOf(
            AppRule("com.bbc.iplayer", "id1"),
            AppRule("com.itv.hub", "id1")
        )
        
        every { settingsRepo.getAllVpnConfigs() } returns flowOf(emptyList())
        every { settingsRepo.getAllAppRules() } returns flowOf(rules)
        coEvery { settingsRepo.getProviderCredentials(any()) } returns null

        // WHEN: ViewModel is initialized
        createViewModel()
        kotlinx.coroutines.delay(200)

        // THEN: The appRules map contains the rules
        val state = viewModel.uiState.value
        assertThat(state.appRules).hasSize(2)
        assertThat(state.appRules).containsKey("com.bbc.iplayer")
        assertThat(state.appRules).containsKey("com.itv.hub")
        assertThat(state.appRules["com.bbc.iplayer"]).isEqualTo("id1")
    }
}
