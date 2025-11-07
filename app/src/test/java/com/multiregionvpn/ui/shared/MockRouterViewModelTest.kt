package com.multiregionvpn.ui.shared

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for MockRouterViewModel
 * 
 * Tests the mock implementation used for UI development and previews.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MockRouterViewModelTest {
    
    private lateinit var viewModel: MockRouterViewModel
    
    @Before
    fun setup() {
        viewModel = MockRouterViewModel()
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // INITIAL STATE TESTS
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Test
    fun `initial vpnStatus should be DISCONNECTED`() {
        assertEquals(VpnStatus.DISCONNECTED, viewModel.vpnStatus.value)
    }
    
    @Test
    fun `initial allServerGroups should contain mock data`() {
        val groups = viewModel.allServerGroups.value
        
        assertTrue(groups.isNotEmpty(), "Should have mock server groups")
        assertTrue(groups.size >= 3, "Should have at least 3 mock groups")
        
        // Verify some expected groups
        val ukGroup = groups.find { it.region == "uk" }
        assertNotNull(ukGroup, "Should have UK group")
        assertEquals("UK Streaming", ukGroup.name)
    }
    
    @Test
    fun `initial allAppRules should contain mock data`() {
        val rules = viewModel.allAppRules.value
        
        assertTrue(rules.isNotEmpty(), "Should have mock app rules")
        assertTrue(rules.size >= 5, "Should have at least 5 mock apps")
        
        // Verify some expected apps
        val bbcApp = rules.find { it.packageName == "com.bbc.iplayer" }
        assertNotNull(bbcApp, "Should have BBC iPlayer")
        assertEquals("BBC iPlayer", bbcApp.appName)
        assertEquals("group_uk", bbcApp.routedGroupId)
    }
    
    @Test
    fun `initial selectedServerGroup should be null`() {
        assertNull(viewModel.selectedServerGroup.value)
    }
    
    @Test
    fun `initial liveStats should be zero`() {
        val stats = viewModel.liveStats.value
        assertEquals(VpnStats(), stats)
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // VPN TOGGLE TESTS
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Test
    fun `onToggleVpn true should change status to CONNECTING`() {
        viewModel.onToggleVpn(enable = true)
        assertEquals(VpnStatus.CONNECTING, viewModel.vpnStatus.value)
    }
    
    @Test
    fun `onToggleVpn false should change status to DISCONNECTED`() = runTest {
        // First connect
        viewModel.onToggleVpn(enable = true)
        delay(100)
        
        // Then disconnect
        viewModel.onToggleVpn(enable = false)
        assertEquals(VpnStatus.DISCONNECTED, viewModel.vpnStatus.value)
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // APP RULE CHANGE TESTS
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Test
    fun `onAppRuleChange should update app routing`() {
        // GIVEN: Initial app rules
        val initialRules = viewModel.allAppRules.value
        val youtubeApp = initialRules.find { it.packageName == "com.google.android.youtube" }
        assertNotNull(youtubeApp)
        
        // Initial state: YouTube bypasses VPN
        assertEquals(null, youtubeApp.routedGroupId)
        
        // WHEN: User routes YouTube through UK group
        viewModel.onAppRuleChange(youtubeApp, "group_uk")
        
        // THEN: YouTube should be routed through UK
        val updatedRules = viewModel.allAppRules.value
        val updatedYoutube = updatedRules.find { it.packageName == "com.google.android.youtube" }
        assertNotNull(updatedYoutube)
        assertEquals("group_uk", updatedYoutube.routedGroupId)
    }
    
    @Test
    fun `onAppRuleChange should update server group active status`() {
        // GIVEN: France group initially inactive
        val initialGroups = viewModel.allServerGroups.value
        val franceGroup = initialGroups.find { it.region == "fr" }
        assertNotNull(franceGroup)
        assertEquals(false, franceGroup.isActive)
        
        // WHEN: User routes an app through France
        val youtubeApp = viewModel.allAppRules.value.first()
        viewModel.onAppRuleChange(youtubeApp, "group_fr")
        
        // THEN: France group should become active
        val updatedGroups = viewModel.allServerGroups.value
        val updatedFrance = updatedGroups.find { it.region == "fr" }
        assertNotNull(updatedFrance)
        assertEquals(true, updatedFrance.isActive)
    }
    
    @Test
    fun `onAppRuleChange to null should bypass VPN`() {
        // GIVEN: BBC iPlayer routed through UK
        val initialRules = viewModel.allAppRules.value
        val bbcApp = initialRules.find { it.packageName == "com.bbc.iplayer" }
        assertNotNull(bbcApp)
        assertEquals("group_uk", bbcApp.routedGroupId)
        
        // WHEN: User sets BBC to bypass
        viewModel.onAppRuleChange(bbcApp, null)
        
        // THEN: BBC should bypass VPN
        val updatedRules = viewModel.allAppRules.value
        val updatedBbc = updatedRules.find { it.packageName == "com.bbc.iplayer" }
        assertNotNull(updatedBbc)
        assertNull(updatedBbc.routedGroupId)
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // SERVER GROUP SELECTION TESTS
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Test
    fun `onServerGroupSelected should update selectedServerGroup`() {
        // GIVEN: No selection
        assertNull(viewModel.selectedServerGroup.value)
        
        // WHEN: User selects UK group
        val ukGroup = viewModel.allServerGroups.value.find { it.region == "uk" }
        assertNotNull(ukGroup)
        viewModel.onServerGroupSelected(ukGroup)
        
        // THEN: UK group should be selected
        assertEquals(ukGroup, viewModel.selectedServerGroup.value)
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // SERVER GROUP MANAGEMENT TESTS
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Test
    fun `onAddServerGroup should add new group`() {
        // GIVEN: Initial group count
        val initialCount = viewModel.allServerGroups.value.size
        
        // WHEN: User adds a new group
        viewModel.onAddServerGroup()
        
        // THEN: Group count should increase
        val newCount = viewModel.allServerGroups.value.size
        assertEquals(initialCount + 1, newCount)
        
        // New group should have expected properties
        val newGroup = viewModel.allServerGroups.value.last()
        assertEquals("New Server Group", newGroup.name)
        assertEquals(false, newGroup.isActive)
    }
    
    @Test
    fun `onRemoveServerGroup should remove group`() {
        // GIVEN: Initial groups
        val initialGroups = viewModel.allServerGroups.value
        val franceGroup = initialGroups.find { it.region == "fr" }
        assertNotNull(franceGroup)
        val initialCount = initialGroups.size
        
        // WHEN: User removes France group
        viewModel.onRemoveServerGroup(franceGroup)
        
        // THEN: Group should be removed
        val updatedGroups = viewModel.allServerGroups.value
        assertEquals(initialCount - 1, updatedGroups.size)
        assertNull(updatedGroups.find { it.region == "fr" })
    }
    
    @Test
    fun `onRemoveServerGroup should reset app rules using that group`() {
        // GIVEN: BBC iPlayer routed through UK
        val ukGroup = viewModel.allServerGroups.value.find { it.region == "uk" }
        assertNotNull(ukGroup)
        
        val bbcApp = viewModel.allAppRules.value.find { it.packageName == "com.bbc.iplayer" }
        assertNotNull(bbcApp)
        assertEquals("group_uk", bbcApp.routedGroupId)
        
        // WHEN: User removes UK group
        viewModel.onRemoveServerGroup(ukGroup)
        
        // THEN: BBC should be set to bypass
        val updatedRules = viewModel.allAppRules.value
        val updatedBbc = updatedRules.find { it.packageName == "com.bbc.iplayer" }
        assertNotNull(updatedBbc)
        assertNull(updatedBbc.routedGroupId, "App rule should be reset to bypass")
    }
    
    @Test
    fun `onRemoveServerGroup should clear selection if selected group removed`() {
        // GIVEN: UK group selected
        val ukGroup = viewModel.allServerGroups.value.find { it.region == "uk" }
        assertNotNull(ukGroup)
        viewModel.onServerGroupSelected(ukGroup)
        assertEquals(ukGroup, viewModel.selectedServerGroup.value)
        
        // WHEN: User removes UK group
        viewModel.onRemoveServerGroup(ukGroup)
        
        // THEN: Selection should be cleared
        assertNull(viewModel.selectedServerGroup.value)
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // MOCK DATA FACTORY TESTS
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Test
    fun `createMockServerGroups should return valid groups`() {
        val groups = MockRouterViewModel.createMockServerGroups()
        
        assertTrue(groups.isNotEmpty())
        
        // Verify all have required properties
        groups.forEach { group ->
            assertTrue(group.id.isNotEmpty())
            assertTrue(group.name.isNotEmpty())
            assertTrue(group.region.isNotEmpty())
            assertTrue(group.serverCount > 0)
        }
    }
    
    @Test
    fun `createMockAppRules should return valid rules`() {
        val rules = MockRouterViewModel.createMockAppRules()
        
        assertTrue(rules.isNotEmpty())
        
        // Verify all have required properties
        rules.forEach { rule ->
            assertTrue(rule.packageName.isNotEmpty())
            assertTrue(rule.appName.isNotEmpty())
            assertNotNull(rule.icon)
        }
    }
    
    @Test
    fun `mock data should include variety of routing types`() {
        val rules = MockRouterViewModel.createMockAppRules()
        
        // Should have apps with VPN routing
        val routedApps = rules.filter { it.routedGroupId != null && it.routedGroupId != "block" }
        assertTrue(routedApps.isNotEmpty(), "Should have VPN-routed apps")
        
        // Should have apps with bypass
        val bypassApps = rules.filter { it.routedGroupId == null }
        assertTrue(bypassApps.isNotEmpty(), "Should have bypass apps")
        
        // Should have blocked apps
        val blockedApps = rules.filter { it.routedGroupId == "block" }
        assertTrue(blockedApps.isNotEmpty(), "Should have blocked apps")
    }
}

