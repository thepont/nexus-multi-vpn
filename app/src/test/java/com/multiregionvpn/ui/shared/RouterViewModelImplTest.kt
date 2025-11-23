package com.multiregionvpn.ui.shared

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.common.truth.Truth.assertThat
import com.multiregionvpn.MainCoroutineRule
import com.multiregionvpn.core.VpnEngineService
import com.multiregionvpn.data.database.AppRule as DbAppRule
import com.multiregionvpn.data.database.VpnConfig
import com.multiregionvpn.data.repository.SettingsRepository
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class RouterViewModelImplTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()
    @get:Rule
    val mainDispatcherRule = MainCoroutineRule(testDispatcher)

    private lateinit var viewModel: RouterViewModelImpl
    private lateinit var mockApplication: Application
    private lateinit var mockSettingsRepository: SettingsRepository
    private lateinit var mockPackageManager: PackageManager

    @Before
    fun setup() {
        mockApplication = mockk(relaxed = true)
        mockPackageManager = mockk(relaxed = true)
        every { mockApplication.packageManager } returns mockPackageManager
        every { mockApplication.packageName } returns "com.multiregionvpn"
        every { mockPackageManager.getApplicationLabel(any()) } returns "Mock App"
        every { mockPackageManager.getApplicationIcon(any<ApplicationInfo>()) } returns ColorDrawable(0xFF000000.toInt())
        every { mockPackageManager.getApplicationIcon(any<String>()) } returns ColorDrawable(0xFF000000.toInt())
        every { mockPackageManager.getInstalledApplications(any<Int>()) } returns emptyList()

        mockSettingsRepository = mockk(relaxed = true)
        every { mockSettingsRepository.getAllVpnConfigs() } returns flowOf(emptyList())
        every { mockSettingsRepository.getAllAppRules() } returns flowOf(emptyList())
        coEvery { mockSettingsRepository.appRuleDao.getAllRulesList() } returns emptyList()

        mockkObject(VpnEngineService.Companion)
        every { VpnEngineService.vpnStatus } returns MutableStateFlow(VpnStatus.DISCONNECTED)

        viewModel = RouterViewModelImpl(mockApplication, mockSettingsRepository)
    }

    @After
    fun tearDown() {
        unmockkObject(VpnEngineService.Companion)
    }

    @Test
    fun `loadServerGroups groups VPN configs by region`() = testScope.runTest {
        val vpnConfigs = listOf(
            VpnConfig("uk1", "UK Server 1", "uk", "nordvpn", "uk1.nordvpn.com"),
            VpnConfig("us1", "US Server 1", "us", "nordvpn", "us1.nordvpn.com")
        )
        every { mockSettingsRepository.getAllVpnConfigs() } returns flowOf(vpnConfigs)

        viewModel = RouterViewModelImpl(mockApplication, mockSettingsRepository)
        runCurrent()

        val serverGroups = viewModel.allServerGroups.value
        assertThat(serverGroups).hasSize(2)
    }

    @Test
    fun `loadServerGroups marks groups as active when app rules exist`() = testScope.runTest {
        val vpnConfigs = listOf(
            VpnConfig("uk1", "UK Server 1", "uk", "nordvpn", "uk1.nordvpn.com"),
            VpnConfig("us1", "US Server 1", "us", "nordvpn", "us1.nordvpn.com")
        )
        val appRules = listOf(DbAppRule("com.bbc.iplayer", "uk1"))
        every { mockSettingsRepository.getAllVpnConfigs() } returns flowOf(vpnConfigs)
        coEvery { mockSettingsRepository.appRuleDao.getAllRulesList() } returns appRules

        viewModel = RouterViewModelImpl(mockApplication, mockSettingsRepository)
        runCurrent()

        val serverGroups = viewModel.allServerGroups.value
        val ukGroup = serverGroups.first { it.id == "uk" }
        val usGroup = serverGroups.first { it.id == "us" }
        assertThat(ukGroup.isActive).isTrue()
        assertThat(usGroup.isActive).isFalse()
    }

    @Test
    fun `loadAppRules loads installed apps with routing rules`() = testScope.runTest {
        val mockAppInfo = mockk<ApplicationInfo>(relaxed = true)
        mockAppInfo.flags = 0
        mockAppInfo.packageName = "com.bbc.iplayer"
        every { mockPackageManager.getInstalledApplications(any<Int>()) } returns listOf(mockAppInfo)
        every { mockPackageManager.getApplicationLabel(any()) } returns "BBC iPlayer"
        every { mockPackageManager.getApplicationIcon(any<String>()) } returns ColorDrawable()
        val appRules = listOf(DbAppRule("com.bbc.iplayer", "uk1"))
        val vpnConfig = VpnConfig("uk1", "UK Server 1", "uk", "nordvpn", "uk1.nordvpn.com")
        every { mockSettingsRepository.getAllAppRules() } returns flowOf(appRules)
        coEvery { mockSettingsRepository.getVpnConfigById("uk1") } returns vpnConfig
        every { mockPackageManager.getApplicationInfo("com.bbc.iplayer", 0) } returns mockAppInfo

        viewModel = RouterViewModelImpl(mockApplication, mockSettingsRepository)
        runCurrent()

        val loadedRules = viewModel.allAppRules.value
        assertThat(loadedRules).hasSize(1)
        val rule = loadedRules[0]
        assertThat(rule.packageName).isEqualTo("com.bbc.iplayer")
        assertThat(rule.appName).isEqualTo("BBC iPlayer")
        assertThat(rule.routedGroupId).isEqualTo("uk")
    }

    @Test
    fun `onToggleVpn(true) starts VPN service`() = testScope.runTest {
        viewModel.onToggleVpn(true)
        runCurrent()
        verify { mockApplication.startService(any()) }
    }

    @Test
    fun `onToggleVpn(false) stops VPN service`() = testScope.runTest {
        viewModel.onToggleVpn(false)
        runCurrent()
        verify { mockApplication.startService(any()) }
    }

    @Test
    fun `onAppRuleChange saves to repository and updates local state`() = testScope.runTest {
        val vpnConfigs = listOf(VpnConfig("uk1", "UK Server 1", "uk", "nordvpn", "uk1.nordvpn.com"))
        every { mockSettingsRepository.getAllVpnConfigs() } returns flowOf(vpnConfigs)
        coEvery { mockSettingsRepository.saveAppRule(any()) } just Runs
        viewModel = RouterViewModelImpl(mockApplication, mockSettingsRepository)
        runCurrent()

        val app = AppRule("com.bbc.iplayer", "BBC iPlayer", null, null)
        viewModel.onAppRuleChange(app, "uk")
        runCurrent()

        coVerify { mockSettingsRepository.saveAppRule(DbAppRule("com.bbc.iplayer", "uk1")) }
    }

    @Test
    fun `onAppRuleChange with null groupId saves bypass rule`() = testScope.runTest {
        coEvery { mockSettingsRepository.saveAppRule(any()) } just Runs
        val app = AppRule("com.netflix.mediaclient", "Netflix", null, "uk")
        viewModel.onAppRuleChange(app, null)
        runCurrent()
        coVerify { mockSettingsRepository.saveAppRule(DbAppRule("com.netflix.mediaclient", null)) }
    }

    @Test
    fun `onRemoveServerGroup deletes all configs in group`() = testScope.runTest {
        val vpnConfigs = mutableListOf(
            VpnConfig("uk1", "UK Server 1", "uk", "nordvpn", "uk1.nordvpn.com"),
            VpnConfig("uk2", "UK Server 2", "uk", "nordvpn", "uk2.nordvpn.com"),
            VpnConfig("us1", "US Server 1", "us", "nordvpn", "us1.nordvpn.com")
        )
        val vpnConfigsFlow = MutableStateFlow(vpnConfigs.toList())
        every { mockSettingsRepository.getAllVpnConfigs() } returns vpnConfigsFlow
        coEvery { mockSettingsRepository.deleteVpnConfig(any()) } answers {
            val id = it.invocation.args[0] as String
            vpnConfigs.removeIf { config -> config.id == id }
            vpnConfigsFlow.value = vpnConfigs.toList()
        }
        viewModel = RouterViewModelImpl(mockApplication, mockSettingsRepository)
        runCurrent()

        val ukGroup = viewModel.allServerGroups.value.first { it.id == "uk" }
        viewModel.onRemoveServerGroup(ukGroup)
        runCurrent()

        coVerify {
            mockSettingsRepository.deleteVpnConfig("uk1")
            mockSettingsRepository.deleteVpnConfig("uk2")
        }
        val remainingGroups = viewModel.allServerGroups.value
        assertThat(remainingGroups).hasSize(1)
        assertThat(remainingGroups.first().id).isEqualTo("us")
    }

    @Test
    fun `onServerGroupSelected updates selected group`() = testScope.runTest {
        val vpnConfigs = listOf(VpnConfig("uk1", "UK Server 1", "uk", "nordvpn", "uk1.nordvpn.com"))
        every { mockSettingsRepository.getAllVpnConfigs() } returns flowOf(vpnConfigs)
        viewModel = RouterViewModelImpl(mockApplication, mockSettingsRepository)
        runCurrent()

        val ukGroup = viewModel.allServerGroups.value.first { it.id == "uk" }
        viewModel.onServerGroupSelected(ukGroup)

        assertThat(viewModel.selectedServerGroup.value).isEqualTo(ukGroup)
    }

    @Test
    fun `vpnStatus reflects VpnEngineService status`() = testScope.runTest {
        val vpnStatusFlow = MutableStateFlow(VpnStatus.DISCONNECTED)
        every { VpnEngineService.vpnStatus } returns vpnStatusFlow

        viewModel = RouterViewModelImpl(mockApplication, mockSettingsRepository)
        runCurrent()
        assertThat(viewModel.vpnStatus.value).isEqualTo(VpnStatus.DISCONNECTED)

        vpnStatusFlow.value = VpnStatus.CONNECTED
        runCurrent()
        assertThat(viewModel.vpnStatus.value).isEqualTo(VpnStatus.CONNECTED)
    }
}