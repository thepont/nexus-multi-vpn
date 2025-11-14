package com.multiregionvpn.ui.shared

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.common.truth.Truth.assertThat
import com.multiregionvpn.MainCoroutineRule
import com.multiregionvpn.core.VpnServiceStateTracker
import com.multiregionvpn.ui.shared.VpnStats
import com.multiregionvpn.data.database.AppRule as DbAppRule
import com.multiregionvpn.data.database.VpnConfig
import com.multiregionvpn.data.repository.SettingsRepository
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for RouterViewModelImpl (real implementation with repository integration).
 * 
 * Tests verify:
 * - Loading server groups from repository
 * - Loading app rules from repository
 * - Saving app rule changes to database
 * - Removing server groups from database
 * - VPN status observation
 * - Live stats observation
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RouterViewModelImplTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()
    @get:Rule
    val mainDispatcherRule = MainCoroutineRule()

    private lateinit var viewModel: RouterViewModelImpl
    private lateinit var mockApplication: Application
    private lateinit var mockSettingsRepository: SettingsRepository
    private lateinit var mockPackageManager: PackageManager
    @Before
    fun setup() {
        // Mock Application
        mockApplication = mockk(relaxed = true)
        mockPackageManager = mockk(relaxed = true)
        every { mockApplication.packageManager } returns mockPackageManager
        every { mockApplication.packageName } returns "com.multiregionvpn"
        every { mockPackageManager.getApplicationLabel(any()) } returns "Mock App"
        every { mockPackageManager.getApplicationIcon(any<ApplicationInfo>()) } returns ColorDrawable(0xFF000000.toInt())
        every { mockPackageManager.getApplicationIcon(any<String>()) } returns ColorDrawable(0xFF000000.toInt())
        every { mockPackageManager.getInstalledApplications(any<Int>()) } returns emptyList()
        
        // Mock SettingsRepository
        mockSettingsRepository = mockk(relaxed = true)
        
        // Default mock behaviors
        every { mockSettingsRepository.getAllVpnConfigs() } returns flowOf(emptyList())
        every { mockSettingsRepository.getAllAppRules() } returns flowOf(emptyList())
        coEvery { mockSettingsRepository.appRuleDao.getAllRulesList() } returns emptyList()
        
        VpnServiceStateTracker.reset()
    }

    @After
    fun tearDown() {
        VpnServiceStateTracker.reset()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // LOADING TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `loadServerGroups groups VPN configs by region`() = runTest() {
        // GIVEN: 3 UK configs and 2 US configs in database
        val vpnConfigs = listOf(
            VpnConfig("uk1", "UK Server 1", "uk", "nordvpn", "uk1.nordvpn.com"),
            VpnConfig("uk2", "UK Server 2", "uk", "nordvpn", "uk2.nordvpn.com"),
            VpnConfig("uk3", "UK Server 3", "uk", "nordvpn", "uk3.nordvpn.com"),
            VpnConfig("us1", "US Server 1", "us", "nordvpn", "us1.nordvpn.com"),
            VpnConfig("us2", "US Server 2", "us", "nordvpn", "us2.nordvpn.com")
        )
        every { mockSettingsRepository.getAllVpnConfigs() } returns flowOf(vpnConfigs)
        coEvery { mockSettingsRepository.appRuleDao.getAllRulesList() } returns emptyList()
        
        // WHEN: ViewModel is created
        viewModel = RouterViewModelImpl(mockApplication, mockSettingsRepository)
        runCurrent()
        
        // THEN: ServerGroups are created for each region
        val serverGroups = viewModel.allServerGroups.value
        assertThat(serverGroups).hasSize(2)
        
        val ukGroup = serverGroups.first { it.id == "uk" }
        assertThat(ukGroup.name).isEqualTo("United Kingdom")
        assertThat(ukGroup.serverCount).isEqualTo(3)
        assertThat(ukGroup.isActive).isFalse()  // No app rules
        
        val usGroup = serverGroups.first { it.id == "us" }
        assertThat(usGroup.name).isEqualTo("United States")
        assertThat(usGroup.serverCount).isEqualTo(2)
        assertThat(usGroup.isActive).isFalse()
    }

    @Test
    fun `loadServerGroups marks groups as active when app rules exist`() = runTest() {
        // GIVEN: VPN configs and app rules that use UK config
        val vpnConfigs = listOf(
            VpnConfig("uk1", "UK Server 1", "uk", "nordvpn", "uk1.nordvpn.com"),
            VpnConfig("us1", "US Server 1", "us", "nordvpn", "us1.nordvpn.com")
        )
        val appRules = listOf(
            DbAppRule("com.bbc.iplayer", "uk1")  // Uses UK server
        )
        
        every { mockSettingsRepository.getAllVpnConfigs() } returns flowOf(vpnConfigs)
        coEvery { mockSettingsRepository.appRuleDao.getAllRulesList() } returns appRules
        
        // WHEN: ViewModel is created
        viewModel = RouterViewModelImpl(mockApplication, mockSettingsRepository)
        runCurrent()
        
        // THEN: UK group is marked as active
        val serverGroups = viewModel.allServerGroups.value
        val ukGroup = serverGroups.first { it.id == "uk" }
        val usGroup = serverGroups.first { it.id == "us" }
        
        assertThat(ukGroup.isActive).isTrue()
        assertThat(usGroup.isActive).isFalse()
    }

    @Test
    fun `loadAppRules loads installed apps with routing rules`() = runTest() {
        // GIVEN: Installed app with a rule
        val mockAppInfo = mockk<ApplicationInfo>(relaxed = true)
        mockAppInfo.flags = 0  // Not a system app
        mockAppInfo.packageName = "com.bbc.iplayer"
        
        every { mockPackageManager.getInstalledApplications(any<Int>()) } returns listOf(mockAppInfo)
        every { mockPackageManager.getApplicationLabel(any()) } returns "BBC iPlayer"
        every { mockPackageManager.getApplicationIcon(any<String>()) } returns ColorDrawable()
        
        val appRules = listOf(
            DbAppRule("com.bbc.iplayer", "uk1")
        )
        val vpnConfig = VpnConfig("uk1", "UK Server 1", "uk", "nordvpn", "uk1.nordvpn.com")
        
        every { mockSettingsRepository.getAllAppRules() } returns flowOf(appRules)
        coEvery { mockSettingsRepository.getVpnConfigById("uk1") } returns vpnConfig
        every { mockPackageManager.getApplicationInfo("com.bbc.iplayer", 0) } returns mockAppInfo
        
        // WHEN: ViewModel is created
        viewModel = RouterViewModelImpl(mockApplication, mockSettingsRepository)
        runCurrent()
        
        // THEN: AppRule is loaded with correct group mapping
        val loadedRules = viewModel.allAppRules.value
        assertThat(loadedRules).hasSize(1)
        
        val rule = loadedRules[0]
        assertThat(rule.packageName).isEqualTo("com.bbc.iplayer")
        assertThat(rule.appName).isEqualTo("BBC iPlayer")
        assertThat(rule.routedGroupId).isEqualTo("uk")  // Mapped from vpnConfigId to regionId
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // EVENT HANDLER TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `onToggleVpn(true) starts VPN service`() = runTest() {
        // GIVEN: ViewModel initialized
        every { mockSettingsRepository.getAllVpnConfigs() } returns flowOf(emptyList())
        every { mockSettingsRepository.getAllAppRules() } returns flowOf(emptyList())
        viewModel = RouterViewModelImpl(mockApplication, mockSettingsRepository)
        runCurrent()
        
        // WHEN: User enables VPN
        viewModel.onToggleVpn(true)
        runCurrent()
        
        // THEN: VPN service start intent is sent
        verify { mockApplication.startService(any()) }
        assertThat(viewModel.vpnStatus.value).isEqualTo(VpnStatus.CONNECTING)
    }

    @Test
    fun `onToggleVpn(false) stops VPN service`() = runTest() {
        // GIVEN: ViewModel initialized
        every { mockSettingsRepository.getAllVpnConfigs() } returns flowOf(emptyList())
        every { mockSettingsRepository.getAllAppRules() } returns flowOf(emptyList())
        viewModel = RouterViewModelImpl(mockApplication, mockSettingsRepository)
        
        // WHEN: User disables VPN
        viewModel.onToggleVpn(false)
        advanceTimeBy(100)
        
        // THEN: VPN service stop intent is sent
        verify { mockApplication.startService(any()) }
        assertThat(viewModel.vpnStatus.value).isEqualTo(VpnStatus.DISCONNECTED)
    }

    @Test
    fun `onAppRuleChange saves to repository and updates local state`() = runTest() {
        // GIVEN: ViewModel with a server group
        val vpnConfigs = listOf(
            VpnConfig("uk1", "UK Server 1", "uk", "nordvpn", "uk1.nordvpn.com")
        )
        every { mockSettingsRepository.getAllVpnConfigs() } returns MutableStateFlow(vpnConfigs)
        every { mockSettingsRepository.getAllAppRules() } returns flowOf(emptyList())
        coEvery { mockSettingsRepository.saveAppRule(any()) } just Runs
        
        viewModel = RouterViewModelImpl(mockApplication, mockSettingsRepository)
        runCurrent()
        
        // WHEN: User changes app rule to UK group
        val app = AppRule("com.bbc.iplayer", "BBC iPlayer", null, null)
        viewModel.onAppRuleChange(app, "uk")
        runCurrent()
        
        // THEN: Repository is called to save app rule
        coVerify {
            mockSettingsRepository.saveAppRule(
                DbAppRule("com.bbc.iplayer", "uk1")  // Mapped to actual config ID
            )
        }
    }

    @Test
    fun `onAppRuleChange with null groupId saves bypass rule`() = runTest() {
        // GIVEN: ViewModel initialized
        every { mockSettingsRepository.getAllVpnConfigs() } returns flowOf(emptyList())
        every { mockSettingsRepository.getAllAppRules() } returns flowOf(emptyList())
        coEvery { mockSettingsRepository.saveAppRule(any()) } just Runs
        
        viewModel = RouterViewModelImpl(mockApplication, mockSettingsRepository)
        runCurrent()
        
        // WHEN: User changes app rule to bypass (null)
        val app = AppRule("com.netflix.mediaclient", "Netflix", null, "uk")
        viewModel.onAppRuleChange(app, null)
        runCurrent()
        
        // THEN: Repository is called to save bypass rule (vpnConfigId = null)
        coVerify {
            mockSettingsRepository.saveAppRule(
                DbAppRule("com.netflix.mediaclient", null)
            )
        }
    }

    @Test
    fun `onRemoveServerGroup deletes all configs in group`() = runTest() {
        // GIVEN: ViewModel with multiple configs in UK group
        val vpnConfigs = listOf(
            VpnConfig("uk1", "UK Server 1", "uk", "nordvpn", "uk1.nordvpn.com"),
            VpnConfig("uk2", "UK Server 2", "uk", "nordvpn", "uk2.nordvpn.com"),
            VpnConfig("us1", "US Server 1", "us", "nordvpn", "us1.nordvpn.com")
        )
        every { mockSettingsRepository.getAllVpnConfigs() } returns MutableStateFlow(vpnConfigs)
        coEvery { mockSettingsRepository.appRuleDao.getAllRulesList() } returns emptyList()
        coEvery { mockSettingsRepository.deleteVpnConfig(any()) } just Runs
        
        viewModel = RouterViewModelImpl(mockApplication, mockSettingsRepository)
        runCurrent()
        
        // WHEN: User removes UK group
        val ukGroup = viewModel.allServerGroups.value.first { it.id == "uk" }
        viewModel.onRemoveServerGroup(ukGroup)
        runCurrent()
        
        // THEN: Both UK configs are deleted
        coVerify {
            mockSettingsRepository.deleteVpnConfig("uk1")
            mockSettingsRepository.deleteVpnConfig("uk2")
        }
        
        // AND: Local state is updated
        val remainingGroups = viewModel.allServerGroups.value
        assertThat(remainingGroups).hasSize(1)
        assertThat(remainingGroups.first().id).isEqualTo("us")
    }

    @Test
    fun `onServerGroupSelected updates selected group`() = runTest() {
        // GIVEN: ViewModel with server groups
        val vpnConfigs = listOf(
            VpnConfig("uk1", "UK Server 1", "uk", "nordvpn", "uk1.nordvpn.com")
        )
        every { mockSettingsRepository.getAllVpnConfigs() } returns flowOf(vpnConfigs)
        coEvery { mockSettingsRepository.appRuleDao.getAllRulesList() } returns emptyList()
        
        viewModel = RouterViewModelImpl(mockApplication, mockSettingsRepository)
        runCurrent()
        
        // WHEN: User selects UK group
        val ukGroup = viewModel.allServerGroups.value.first { it.id == "uk" }
        viewModel.onServerGroupSelected(ukGroup)
        
        // THEN: Selected group is updated
        assertThat(viewModel.selectedServerGroup.value).isEqualTo(ukGroup)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // OBSERVATION TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `observeVpnStatus updates status when service starts`() = runTest() {
        // GIVEN: ViewModel initialized with service stopped
        every { mockSettingsRepository.getAllVpnConfigs() } returns flowOf(emptyList())
        every { mockSettingsRepository.getAllAppRules() } returns flowOf(emptyList())
        
        viewModel = RouterViewModelImpl(mockApplication, mockSettingsRepository)
        runCurrent()
        
        assertThat(viewModel.vpnStatus.value).isEqualTo(VpnStatus.DISCONNECTED)
        
        // WHEN: Service starts
        VpnServiceStateTracker.updateStatus(VpnStatus.CONNECTED)
        runCurrent()
        
        // THEN: Status is updated to CONNECTED
        assertThat(viewModel.vpnStatus.value).isEqualTo(VpnStatus.CONNECTED)
    }

    @Test
    fun `observeLiveStats tracks connection time`() = runTest() {
        // GIVEN: ViewModel initialized with service running
        every { mockSettingsRepository.getAllVpnConfigs() } returns flowOf(emptyList())
        every { mockSettingsRepository.getAllAppRules() } returns flowOf(emptyList())
        
        viewModel = RouterViewModelImpl(mockApplication, mockSettingsRepository)
        runCurrent()
        
        // WHEN: Stats update emitted from service
        VpnServiceStateTracker.updateStats(VpnStats(connectionTimeSeconds = 5, activeConnections = 2))
        runCurrent()
        
        // THEN: Connection time is tracked
        val stats = viewModel.liveStats.value
        assertThat(stats.connectionTimeSeconds).isEqualTo(5)
        assertThat(stats.activeConnections).isEqualTo(2)
    }

    @Test
    fun `observeLiveStats resets when service stops`() = runTest() {
        // GIVEN: ViewModel with service running
        every { mockSettingsRepository.getAllVpnConfigs() } returns flowOf(emptyList())
        every { mockSettingsRepository.getAllAppRules() } returns flowOf(emptyList())
        
        viewModel = RouterViewModelImpl(mockApplication, mockSettingsRepository)
        runCurrent()
        VpnServiceStateTracker.updateStats(VpnStats(connectionTimeSeconds = 10, activeConnections = 1))
        runCurrent()
        
        assertThat(viewModel.liveStats.value.connectionTimeSeconds).isGreaterThan(0)
        
        // WHEN: Service stops
        VpnServiceStateTracker.updateStats(VpnStats())
        runCurrent()
        
        // THEN: Stats are reset
        val stats = viewModel.liveStats.value
        assertThat(stats.connectionTimeSeconds).isEqualTo(0)
        assertThat(stats.activeConnections).isEqualTo(0)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // REGION DISPLAY NAME TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `getRegionDisplayName returns human-readable names`() = runTest() {
        // GIVEN: VPN configs with various region codes
        val vpnConfigs = listOf(
            VpnConfig("uk1", "Server 1", "uk", "nordvpn", "uk1.nordvpn.com"),
            VpnConfig("us1", "Server 2", "us", "nordvpn", "us1.nordvpn.com"),
            VpnConfig("fr1", "Server 3", "fr", "nordvpn", "fr1.nordvpn.com"),
            VpnConfig("xx1", "Server 4", "xx", "nordvpn", "xx1.nordvpn.com")  // Unknown code
        )
        every { mockSettingsRepository.getAllVpnConfigs() } returns flowOf(vpnConfigs)
        coEvery { mockSettingsRepository.appRuleDao.getAllRulesList() } returns emptyList()
        
        viewModel = RouterViewModelImpl(mockApplication, mockSettingsRepository)
        advanceTimeBy(100)
        
        // THEN: Display names are human-readable
        val serverGroups = viewModel.allServerGroups.value
        assertThat(serverGroups.first { it.id == "uk" }.name).isEqualTo("United Kingdom")
        assertThat(serverGroups.first { it.id == "us" }.name).isEqualTo("United States")
        assertThat(serverGroups.first { it.id == "fr" }.name).isEqualTo("France")
        assertThat(serverGroups.first { it.id == "xx" }.name).isEqualTo("XX")  // Fallback
    }
}

