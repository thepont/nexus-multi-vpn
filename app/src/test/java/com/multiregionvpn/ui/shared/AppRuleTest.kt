package com.multiregionvpn.ui.shared

import android.graphics.drawable.Drawable
import io.mockk.mockk
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * Unit tests for AppRule data class
 */
class AppRuleTest {
    
    @Test
    fun `AppRule should create with all properties`() {
        // GIVEN: AppRule parameters
        val packageName = "com.bbc.iplayer"
        val appName = "BBC iPlayer"
        val icon = mockk<Drawable>()
        val routedGroupId = "group_uk_1"
        
        // WHEN: Creating AppRule
        val rule = AppRule(
            packageName = packageName,
            appName = appName,
            icon = icon,
            routedGroupId = routedGroupId
        )
        
        // THEN: All properties should match
        assertEquals(packageName, rule.packageName)
        assertEquals(appName, rule.appName)
        assertEquals(icon, rule.icon)
        assertEquals(routedGroupId, rule.routedGroupId)
    }
    
    @Test
    fun `AppRule should have default null routedGroupId`() {
        // WHEN: Creating AppRule without routedGroupId
        val rule = AppRule(
            packageName = "com.test",
            appName = "Test App",
            icon = null
        )
        
        // THEN: routedGroupId should be null (bypass)
        assertNull(rule.routedGroupId)
    }
    
    @Test
    fun `AppRule getRoutingDescription should return correct text for bypass`() {
        // GIVEN: AppRule with null routedGroupId (bypass)
        val rule = AppRule("com.test", "Test", null, routedGroupId = null)
        
        // WHEN: Getting routing description
        val description = rule.getRoutingDescription()
        
        // THEN: Should indicate direct internet
        assertEquals("Direct Internet", description)
    }
    
    @Test
    fun `AppRule getRoutingDescription should return correct text for block`() {
        // GIVEN: AppRule with "block" routedGroupId
        val rule = AppRule("com.test", "Test", null, routedGroupId = "block")
        
        // WHEN: Getting routing description
        val description = rule.getRoutingDescription()
        
        // THEN: Should indicate blocked
        assertEquals("Blocked", description)
    }
    
    @Test
    fun `AppRule getRoutingDescription should return correct text for VPN routing`() {
        // GIVEN: AppRule with a server group ID
        val rule = AppRule("com.test", "Test", null, routedGroupId = "group_uk_1")
        
        // WHEN: Getting routing description
        val description = rule.getRoutingDescription()
        
        // THEN: Should indicate VPN routing
        assertEquals("Routed via VPN", description)
    }
    
    @Test
    fun `AppRule equality should work correctly`() {
        // GIVEN: Two identical AppRules (ignoring icon for simplicity)
        val rule1 = AppRule("com.test", "Test", null, "group1")
        val rule2 = AppRule("com.test", "Test", null, "group1")
        
        // THEN: Should be equal
        assertEquals(rule1, rule2)
        assertEquals(rule1.hashCode(), rule2.hashCode())
    }
    
    @Test
    fun `AppRule with different packageNames should not be equal`() {
        // GIVEN: Two AppRules with different package names
        val rule1 = AppRule("com.test1", "Test", null)
        val rule2 = AppRule("com.test2", "Test", null)
        
        // THEN: Should not be equal
        assertNotEquals(rule1, rule2)
    }
    
    @Test
    fun `AppRule copy should work correctly`() {
        // GIVEN: An AppRule
        val original = AppRule(
            packageName = "com.original",
            appName = "Original",
            icon = null,
            routedGroupId = "group1"
        )
        
        // WHEN: Copying with modified routedGroupId
        val modified = original.copy(routedGroupId = "group2")
        
        // THEN: Should have new routedGroupId but other properties same
        assertEquals("group2", modified.routedGroupId)
        assertEquals(original.packageName, modified.packageName)
        assertEquals(original.appName, modified.appName)
    }
    
    @Test
    fun `AppRule should handle null icon`() {
        // GIVEN: AppRule with null icon
        val rule = AppRule("com.test", "Test", icon = null)
        
        // THEN: Should be valid
        assertNull(rule.icon)
    }
    
    @Test
    fun `AppRule should support changing routing from bypass to VPN`() {
        // GIVEN: App initially bypassing VPN
        val bypass = AppRule("com.test", "Test", null, routedGroupId = null)
        
        // WHEN: Changing to route through UK group
        val routed = bypass.copy(routedGroupId = "group_uk")
        
        // THEN: Descriptions should reflect change
        assertEquals("Direct Internet", bypass.getRoutingDescription())
        assertEquals("Routed via VPN", routed.getRoutingDescription())
    }
    
    @Test
    fun `AppRule should support changing routing from VPN to block`() {
        // GIVEN: App routed through VPN
        val routed = AppRule("com.test", "Test", null, routedGroupId = "group_uk")
        
        // WHEN: Changing to blocked
        val blocked = routed.copy(routedGroupId = "block")
        
        // THEN: Descriptions should reflect change
        assertEquals("Routed via VPN", routed.getRoutingDescription())
        assertEquals("Blocked", blocked.getRoutingDescription())
    }
    
    @Test
    fun `AppRule should support all routing transitions`() {
        // GIVEN: An app rule
        val rule = AppRule("com.test", "Test", null)
        
        // WHEN/THEN: All transitions should be supported
        val bypass = rule.copy(routedGroupId = null)
        assertEquals("Direct Internet", bypass.getRoutingDescription())
        
        val blocked = rule.copy(routedGroupId = "block")
        assertEquals("Blocked", blocked.getRoutingDescription())
        
        val vpn = rule.copy(routedGroupId = "group_id")
        assertEquals("Routed via VPN", vpn.getRoutingDescription())
    }
}

