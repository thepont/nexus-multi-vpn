package com.multiregionvpn.ui.shared

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Unit tests for RouterViewModel contract
 * 
 * Tests that the abstract RouterViewModel interface defines the correct contract
 * and that implementations can satisfy it correctly.
 */
class RouterViewModelContractTest {
    
    private lateinit var mockViewModel: TestRouterViewModel
    
    /**
     * Test implementation of RouterViewModel for contract testing
     */
    private class TestRouterViewModel : RouterViewModel() {
        override val vpnStatus = MutableStateFlow(VpnStatus.DISCONNECTED)
        override val allServerGroups = MutableStateFlow<List<ServerGroup>>(emptyList())
        override val allAppRules = MutableStateFlow<List<AppRule>>(emptyList())
        override val allInstalledApps = MutableStateFlow<List<AppRule>>(emptyList())
        override val selectedServerGroup = MutableStateFlow<ServerGroup?>(null)
        override val liveStats = MutableStateFlow(VpnStats())
        
        var toggleVpnCalled = false
        var appRuleChangeCalled = false
        var serverGroupSelectedCalled = false
        var addServerGroupCalled = false
        var removeServerGroupCalled = false
        
        override fun onToggleVpn(enable: Boolean) {
            toggleVpnCalled = true
            vpnStatus.value = if (enable) VpnStatus.CONNECTING else VpnStatus.DISCONNECTED
        }
        
        override fun onAppRuleChange(app: AppRule, newGroupId: String?) {
            appRuleChangeCalled = true
        }
        
        override fun onServerGroupSelected(group: ServerGroup) {
            serverGroupSelectedCalled = true
            selectedServerGroup.value = group
        }
        
        override fun onAddServerGroup() {
            addServerGroupCalled = true
        }
        
        override fun onRemoveServerGroup(group: ServerGroup) {
            removeServerGroupCalled = true
        }
    }
    
    @Before
    fun setup() {
        mockViewModel = TestRouterViewModel()
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // STATE FLOW TESTS
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Test
    fun `RouterViewModel should expose vpnStatus StateFlow`() {
        // GIVEN: RouterViewModel implementation
        // THEN: Should have vpnStatus StateFlow
        assertNotNull(mockViewModel.vpnStatus, "vpnStatus should not be null")
        assertEquals(VpnStatus.DISCONNECTED, mockViewModel.vpnStatus.value)
    }
    
    @Test
    fun `RouterViewModel should expose allServerGroups StateFlow`() {
        // GIVEN: RouterViewModel implementation
        // THEN: Should have allServerGroups StateFlow
        assertNotNull(mockViewModel.allServerGroups, "allServerGroups should not be null")
        assertEquals(emptyList(), mockViewModel.allServerGroups.value)
    }
    
    @Test
    fun `RouterViewModel should expose allAppRules StateFlow`() {
        // GIVEN: RouterViewModel implementation
        // THEN: Should have allAppRules StateFlow
        assertNotNull(mockViewModel.allAppRules, "allAppRules should not be null")
        assertEquals(emptyList(), mockViewModel.allAppRules.value)
    }
    
    @Test
    fun `RouterViewModel should expose allInstalledApps StateFlow`() {
        // GIVEN: RouterViewModel implementation
        // THEN: Should have allInstalledApps StateFlow
        assertNotNull(mockViewModel.allInstalledApps, "allInstalledApps should not be null")
        assertEquals(emptyList(), mockViewModel.allInstalledApps.value)
    }
    
    @Test
    fun `RouterViewModel should expose selectedServerGroup StateFlow`() {
        // GIVEN: RouterViewModel implementation
        // THEN: Should have selectedServerGroup StateFlow
        assertNotNull(mockViewModel.selectedServerGroup, "selectedServerGroup should not be null")
        assertEquals(null, mockViewModel.selectedServerGroup.value)
    }
    
    @Test
    fun `RouterViewModel should expose liveStats StateFlow`() {
        // GIVEN: RouterViewModel implementation
        // THEN: Should have liveStats StateFlow
        assertNotNull(mockViewModel.liveStats, "liveStats should not be null")
        assertEquals(VpnStats(), mockViewModel.liveStats.value)
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // EVENT METHOD TESTS
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Test
    fun `onToggleVpn should be callable with enable true`() {
        // WHEN: Calling onToggleVpn(true)
        mockViewModel.onToggleVpn(enable = true)
        
        // THEN: Should be called
        assertEquals(true, mockViewModel.toggleVpnCalled)
        assertEquals(VpnStatus.CONNECTING, mockViewModel.vpnStatus.value)
    }
    
    @Test
    fun `onToggleVpn should be callable with enable false`() {
        // WHEN: Calling onToggleVpn(false)
        mockViewModel.onToggleVpn(enable = false)
        
        // THEN: Should be called
        assertEquals(true, mockViewModel.toggleVpnCalled)
        assertEquals(VpnStatus.DISCONNECTED, mockViewModel.vpnStatus.value)
    }
    
    @Test
    fun `onAppRuleChange should be callable`() {
        // GIVEN: An app rule
        val app = AppRule("com.test", "Test", null)
        
        // WHEN: Calling onAppRuleChange
        mockViewModel.onAppRuleChange(app, "group_uk")
        
        // THEN: Should be called
        assertEquals(true, mockViewModel.appRuleChangeCalled)
    }
    
    @Test
    fun `onAppRuleChange should accept null groupId for bypass`() {
        // GIVEN: An app rule
        val app = AppRule("com.test", "Test", null)
        
        // WHEN: Calling onAppRuleChange with null (bypass)
        mockViewModel.onAppRuleChange(app, null)
        
        // THEN: Should be called
        assertEquals(true, mockViewModel.appRuleChangeCalled)
    }
    
    @Test
    fun `onServerGroupSelected should be callable`() {
        // GIVEN: A server group
        val group = ServerGroup("id", "UK", "uk", 5)
        
        // WHEN: Calling onServerGroupSelected
        mockViewModel.onServerGroupSelected(group)
        
        // THEN: Should be called and update selectedServerGroup
        assertEquals(true, mockViewModel.serverGroupSelectedCalled)
        assertEquals(group, mockViewModel.selectedServerGroup.value)
    }
    
    @Test
    fun `onAddServerGroup should be callable`() {
        // WHEN: Calling onAddServerGroup
        mockViewModel.onAddServerGroup()
        
        // THEN: Should be called
        assertEquals(true, mockViewModel.addServerGroupCalled)
    }
    
    @Test
    fun `onRemoveServerGroup should be callable`() {
        // GIVEN: A server group
        val group = ServerGroup("id", "UK", "uk", 5)
        
        // WHEN: Calling onRemoveServerGroup
        mockViewModel.onRemoveServerGroup(group)
        
        // THEN: Should be called
        assertEquals(true, mockViewModel.removeServerGroupCalled)
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // STATE UPDATES TESTS
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Test
    fun `vpnStatus should be mutable by implementation`() {
        // GIVEN: Initial state
        assertEquals(VpnStatus.DISCONNECTED, mockViewModel.vpnStatus.value)
        
        // WHEN: Implementation updates state
        mockViewModel.vpnStatus.value = VpnStatus.CONNECTED
        
        // THEN: State should change
        assertEquals(VpnStatus.CONNECTED, mockViewModel.vpnStatus.value)
    }
    
    @Test
    fun `allServerGroups should be mutable by implementation`() {
        // GIVEN: Initial empty list
        assertEquals(emptyList(), mockViewModel.allServerGroups.value)
        
        // WHEN: Implementation updates list
        val groups = listOf(
            ServerGroup("id1", "UK", "uk", 5),
            ServerGroup("id2", "US", "us", 10)
        )
        mockViewModel.allServerGroups.value = groups
        
        // THEN: State should change
        assertEquals(2, mockViewModel.allServerGroups.value.size)
        assertEquals(groups, mockViewModel.allServerGroups.value)
    }
    
    @Test
    fun `allAppRules should be mutable by implementation`() {
        // GIVEN: Initial empty list
        assertEquals(emptyList(), mockViewModel.allAppRules.value)
        
        // WHEN: Implementation updates list
        val rules = listOf(
            AppRule("com.test1", "Test1", null),
            AppRule("com.test2", "Test2", null)
        )
        mockViewModel.allAppRules.value = rules
        
        // THEN: State should change
        assertEquals(2, mockViewModel.allAppRules.value.size)
        assertEquals(rules, mockViewModel.allAppRules.value)
    }
    
    @Test
    fun `liveStats should be mutable by implementation`() {
        // GIVEN: Initial zero stats
        assertEquals(VpnStats(), mockViewModel.liveStats.value)
        
        // WHEN: Implementation updates stats
        val newStats = VpnStats(
            bytesSent = 1000L,
            bytesReceived = 2000L,
            connectionTimeSeconds = 100L,
            activeConnections = 5
        )
        mockViewModel.liveStats.value = newStats
        
        // THEN: State should change
        assertEquals(newStats, mockViewModel.liveStats.value)
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // INTEGRATION TESTS
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Test
    fun `RouterViewModel contract supports complete UI workflow`() {
        // SCENARIO: User opens app, connects VPN, routes an app, selects group for details
        
        // 1. Initial state - VPN disconnected
        assertEquals(VpnStatus.DISCONNECTED, mockViewModel.vpnStatus.value)
        
        // 2. User toggles VPN on
        mockViewModel.onToggleVpn(enable = true)
        assertEquals(VpnStatus.CONNECTING, mockViewModel.vpnStatus.value)
        
        // 3. Add some server groups
        mockViewModel.allServerGroups.value = listOf(
            ServerGroup("uk", "UK Streaming", "uk", 5, isActive = false),
            ServerGroup("us", "US General", "us", 10, isActive = false)
        )
        
        // 4. Add app rules
        val bbcApp = AppRule("com.bbc.iplayer", "BBC iPlayer", null)
        mockViewModel.allAppRules.value = listOf(bbcApp)
        
        // 5. User routes BBC iPlayer through UK group
        mockViewModel.onAppRuleChange(bbcApp, "uk")
        assertEquals(true, mockViewModel.appRuleChangeCalled)
        
        // 6. User selects UK group for details
        val ukGroup = mockViewModel.allServerGroups.value[0]
        mockViewModel.onServerGroupSelected(ukGroup)
        assertEquals(ukGroup, mockViewModel.selectedServerGroup.value)
        
        // 7. User adds a new group
        mockViewModel.onAddServerGroup()
        assertEquals(true, mockViewModel.addServerGroupCalled)
        
        // 8. User removes a group
        mockViewModel.onRemoveServerGroup(ukGroup)
        assertEquals(true, mockViewModel.removeServerGroupCalled)
        
        // All contract methods successfully called
        assertEquals(true, mockViewModel.toggleVpnCalled)
        assertEquals(true, mockViewModel.appRuleChangeCalled)
        assertEquals(true, mockViewModel.serverGroupSelectedCalled)
        assertEquals(true, mockViewModel.addServerGroupCalled)
        assertEquals(true, mockViewModel.removeServerGroupCalled)
    }
}

