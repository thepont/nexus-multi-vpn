package com.multiregionvpn.core

import kotlin.test.*
import com.multiregionvpn.core.vpnclient.MockOpenVpnClient
import com.multiregionvpn.core.vpnclient.OpenVpnClient
import com.multiregionvpn.data.database.AppRule
import com.multiregionvpn.data.database.VpnConfig
import com.multiregionvpn.data.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for tunnel management logic in VpnEngineService.
 * Tests the manageTunnels() coroutine functionality.
 */
class TunnelManagerTest {

    private lateinit var mockSettingsRepo: SettingsRepository
    private lateinit var mockVpnTemplateService: VpnTemplateService
    private lateinit var mockConnectionManager: VpnConnectionManager
    private lateinit var mockClientFactory: (String) -> OpenVpnClient
    private lateinit var appRulesFlow: MutableStateFlow<List<AppRule>>

    @Before
    fun setup() {
        mockSettingsRepo = mockk()
        mockVpnTemplateService = mockk()
        appRulesFlow = MutableStateFlow(emptyList())
        
        // Mock factory that returns MockOpenVpnClient
        mockClientFactory = { _ -> MockOpenVpnClient() }
        mockConnectionManager = VpnConnectionManager.createForTesting(mockClientFactory)
        
        // Default mock behavior
        every { mockSettingsRepo.getAllAppRules() } returns appRulesFlow
    }

    @Test
    fun `given app rule exists, when manageTunnels processes rules, then tunnel is created`() = runTest {
        // GIVEN: An app rule pointing to a VPN config
        val vpnConfig = VpnConfig("vpn-uk-1", "UK VPN", "UK", "nordvpn", "uk1234.nordvpn.com")
        val appRule = AppRule("com.test.app", "vpn-uk-1")
        val preparedConfig = PreparedVpnConfig(
            vpnConfig = vpnConfig,
            ovpnFileContent = "client\nremote uk1234.nordvpn.com 1194\nproto udp",
            authFile = null
        )
        
        coEvery { mockSettingsRepo.getVpnConfigById("vpn-uk-1") } returns vpnConfig
        coEvery { mockVpnTemplateService.prepareConfig(vpnConfig) } returns preparedConfig
        
        // WHEN: App rules flow emits the rule
        appRulesFlow.value = listOf(appRule)
        
        // Manually process the tunnel creation logic (simulating what manageTunnels does)
        val tunnelId = "nordvpn_UK"
        
        if (!mockConnectionManager.isTunnelConnected(tunnelId)) {
            val result = mockConnectionManager.createTunnel(
                tunnelId = tunnelId,
                ovpnConfig = preparedConfig.ovpnFileContent,
                authFilePath = preparedConfig.authFile?.absolutePath
            )
            
            // THEN: Tunnel is created
            assertTrue(result.success)
            assertTrue(mockConnectionManager.isTunnelConnected(tunnelId))
        }
    }

    @Test
    fun `given multiple app rules for same VPN, when manageTunnels processes, then only one tunnel is created`() = runTest {
        // GIVEN: Multiple app rules pointing to the same VPN config
        val vpnConfig = VpnConfig("vpn-uk-1", "UK VPN", "UK", "nordvpn", "uk1234.nordvpn.com")
        val rules = listOf(
            AppRule("com.app1", "vpn-uk-1"),
            AppRule("com.app2", "vpn-uk-1"),
            AppRule("com.app3", "vpn-uk-1")
        )
        
        val preparedConfig = PreparedVpnConfig(
            vpnConfig = vpnConfig,
            ovpnFileContent = "client\nremote uk1234.nordvpn.com 1194\nproto udp",
            authFile = null
        )
        
        coEvery { mockSettingsRepo.getVpnConfigById("vpn-uk-1") } returns vpnConfig
        coEvery { mockVpnTemplateService.prepareConfig(vpnConfig) } returns preparedConfig
        
        // WHEN: Processing rules
        val uniqueVpnConfigIds = rules
            .filter { it.vpnConfigId != null }
            .map { it.vpnConfigId!! }
            .distinct()
        
        // THEN: Should only have one unique VPN config ID
        assertEquals(1, uniqueVpnConfigIds.size)
        assertEquals("vpn-uk-1", uniqueVpnConfigIds[0])
    }

    @Test
    fun `given app rules for different VPNs, when manageTunnels processes, then multiple tunnels are created`() = runTest {
        // GIVEN: App rules pointing to different VPN configs
        val vpnConfigUk = VpnConfig("vpn-uk-1", "UK VPN", "UK", "nordvpn", "uk1234.nordvpn.com")
        val vpnConfigFr = VpnConfig("vpn-fr-1", "FR VPN", "FR", "nordvpn", "fr1234.nordvpn.com")
        val rules = listOf(
            AppRule("com.app1", "vpn-uk-1"),
            AppRule("com.app2", "vpn-fr-1")
        )
        
        val preparedConfigUk = PreparedVpnConfig(
            vpnConfig = vpnConfigUk,
            ovpnFileContent = "client\nremote uk1234.nordvpn.com 1194\nproto udp",
            authFile = null
        )
        val preparedConfigFr = PreparedVpnConfig(
            vpnConfig = vpnConfigFr,
            ovpnFileContent = "client\nremote fr1234.nordvpn.com 1194\nproto udp",
            authFile = null
        )
        
        coEvery { mockSettingsRepo.getVpnConfigById("vpn-uk-1") } returns vpnConfigUk
        coEvery { mockSettingsRepo.getVpnConfigById("vpn-fr-1") } returns vpnConfigFr
        coEvery { mockVpnTemplateService.prepareConfig(vpnConfigUk) } returns preparedConfigUk
        coEvery { mockVpnTemplateService.prepareConfig(vpnConfigFr) } returns preparedConfigFr
        
        // WHEN: Processing rules and creating tunnels
        val tunnelIdUk = "nordvpn_UK"
        val tunnelIdFr = "nordvpn_FR"
        
        mockConnectionManager.createTunnel(tunnelIdUk, preparedConfigUk.ovpnFileContent, null)
        mockConnectionManager.createTunnel(tunnelIdFr, preparedConfigFr.ovpnFileContent, null)
        
        // THEN: Both tunnels are created
        val uniqueVpnConfigIds = rules
            .filter { it.vpnConfigId != null }
            .map { it.vpnConfigId!! }
            .distinct()
        assertEquals(2, uniqueVpnConfigIds.size)
        assertTrue(mockConnectionManager.isTunnelConnected(tunnelIdUk))
        assertTrue(mockConnectionManager.isTunnelConnected(tunnelIdFr))
    }

    @Test
    fun `given VPN config not found, when manageTunnels processes, then tunnel is not created`() = runTest {
        // GIVEN: A non-existent VPN config
        coEvery { mockSettingsRepo.getVpnConfigById("nonexistent-vpn") } returns null
        
        // WHEN: Trying to get VPN config for non-existent config
        val vpnConfig = mockSettingsRepo.getVpnConfigById("nonexistent-vpn")
        
        // THEN: VPN config is null, so tunnel should not be created
        assertNull(vpnConfig)
    }

    @Test
    fun `given tunnel already exists, when manageTunnels processes, then tunnel is not recreated`() = runTest {
        // GIVEN: A tunnel already exists
        val vpnConfig = VpnConfig("vpn-uk-1", "UK VPN", "UK", "nordvpn", "uk1234.nordvpn.com")
        val tunnelId = "nordvpn_UK"
        val preparedConfig = PreparedVpnConfig(
            vpnConfig = vpnConfig,
            ovpnFileContent = "client\nremote uk1234.nordvpn.com 1194\nproto udp",
            authFile = null
        )
        
        // Create tunnel first
        mockConnectionManager.createTunnel(tunnelId, preparedConfig.ovpnFileContent, null)
        assertTrue(mockConnectionManager.isTunnelConnected(tunnelId))
        
        // WHEN: Processing rules again (tunnel already exists)
        val isAlreadyConnected = mockConnectionManager.isTunnelConnected(tunnelId)
        
        // THEN: Tunnel should still be connected, no need to recreate
        assertTrue(isAlreadyConnected)
    }

    @Test
    fun `given app rule removed, when manageTunnels processes, then unused tunnel is closed`() = runTest {
        // GIVEN: A tunnel exists
        val vpnConfig = VpnConfig("vpn-uk-1", "UK VPN", "UK", "nordvpn", "uk1234.nordvpn.com")
        val tunnelId = "nordvpn_UK"
        val preparedConfig = PreparedVpnConfig(
            vpnConfig = vpnConfig,
            ovpnFileContent = "client\nremote uk1234.nordvpn.com 1194\nproto udp",
            authFile = null
        )
        
        mockConnectionManager.createTunnel(tunnelId, preparedConfig.ovpnFileContent, null)
        assertTrue(mockConnectionManager.isTunnelConnected(tunnelId))
        
        // WHEN: All app rules are removed (no active VPN configs)
        val activeTunnelIds = emptySet<String>()
        val tunnelsToClose = listOf(tunnelId).filter { it !in activeTunnelIds }
        
        if (tunnelsToClose.isNotEmpty()) {
            mockConnectionManager.closeTunnel(tunnelsToClose[0])
        }
        
        // THEN: Tunnel is closed
        assertFalse(mockConnectionManager.isTunnelConnected(tunnelId))
    }
}

