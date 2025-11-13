package com.multiregionvpn.ui.shared

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Unit tests for ServerGroup data class
 */
class ServerGroupTest {
    
    @Test
    fun `ServerGroup should create with all properties`() {
        // GIVEN: ServerGroup parameters
        val id = "group_uk_1"
        val name = "UK Streaming"
        val region = "uk"
        val serverCount = 42
        val isActive = true
        
        // WHEN: Creating ServerGroup
        val group = ServerGroup(
            id = id,
            name = name,
            region = region,
            serverCount = serverCount,
            isActive = isActive
        )
        
        // THEN: All properties should match
        assertEquals(id, group.id)
        assertEquals(name, group.name)
        assertEquals(region, group.region)
        assertEquals(serverCount, group.serverCount)
        assertEquals(isActive, group.isActive)
    }
    
    @Test
    fun `ServerGroup should have default isActive false`() {
        // WHEN: Creating ServerGroup without isActive
        val group = ServerGroup(
            id = "test",
            name = "Test Group",
            region = "us",
            serverCount = 10
        )
        
        // THEN: isActive should default to false
        assertFalse(group.isActive, "isActive should default to false")
    }
    
    @Test
    fun `ServerGroup equality should work correctly`() {
        // GIVEN: Two identical ServerGroups
        val group1 = ServerGroup(
            id = "group1",
            name = "UK Group",
            region = "uk",
            serverCount = 5,
            isActive = true
        )
        
        val group2 = ServerGroup(
            id = "group1",
            name = "UK Group",
            region = "uk",
            serverCount = 5,
            isActive = true
        )
        
        // THEN: Should be equal
        assertEquals(group1, group2, "Identical ServerGroups should be equal")
        assertEquals(group1.hashCode(), group2.hashCode(), "Hash codes should match")
    }
    
    @Test
    fun `ServerGroup with different IDs should not be equal`() {
        // GIVEN: Two ServerGroups with different IDs
        val group1 = ServerGroup("id1", "Name", "uk", 5)
        val group2 = ServerGroup("id2", "Name", "uk", 5)
        
        // THEN: Should not be equal
        assertNotEquals(group1, group2, "ServerGroups with different IDs should not be equal")
    }
    
    @Test
    fun `ServerGroup copy should work correctly`() {
        // GIVEN: A ServerGroup
        val original = ServerGroup(
            id = "original",
            name = "Original Name",
            region = "uk",
            serverCount = 10,
            isActive = false
        )
        
        // WHEN: Copying with modified name
        val modified = original.copy(name = "Modified Name")
        
        // THEN: Should have new name but other properties same
        assertEquals("Modified Name", modified.name)
        assertEquals(original.id, modified.id)
        assertEquals(original.region, modified.region)
        assertEquals(original.serverCount, modified.serverCount)
        assertEquals(original.isActive, modified.isActive)
    }
    
    @Test
    fun `ServerGroup copy should allow toggling isActive`() {
        // GIVEN: An inactive ServerGroup
        val inactive = ServerGroup("id", "Name", "us", 5, isActive = false)
        
        // WHEN: Copying with isActive = true
        val active = inactive.copy(isActive = true)
        
        // THEN: Should be active
        assertFalse(inactive.isActive, "Original should be inactive")
        assertTrue(active.isActive, "Copy should be active")
    }
    
    @Test
    fun `ServerGroup should handle zero servers`() {
        // GIVEN: ServerGroup with 0 servers
        val emptyGroup = ServerGroup("id", "Empty", "uk", serverCount = 0)
        
        // THEN: Should be valid
        assertEquals(0, emptyGroup.serverCount)
    }
    
    @Test
    fun `ServerGroup should handle large server counts`() {
        // GIVEN: ServerGroup with many servers
        val largeGroup = ServerGroup("id", "Large", "us", serverCount = 10000)
        
        // THEN: Should handle large numbers
        assertEquals(10000, largeGroup.serverCount)
    }
    
    @Test
    fun `ServerGroup regions should be lowercase by convention`() {
        // GIVEN: Recommended region codes
        val ukGroup = ServerGroup("id1", "UK", "uk", 5)
        val usGroup = ServerGroup("id2", "US", "us", 10)
        val frGroup = ServerGroup("id3", "FR", "fr", 8)
        
        // THEN: Region codes should be lowercase
        assertEquals("uk", ukGroup.region)
        assertEquals("us", usGroup.region)
        assertEquals("fr", frGroup.region)
    }
}

