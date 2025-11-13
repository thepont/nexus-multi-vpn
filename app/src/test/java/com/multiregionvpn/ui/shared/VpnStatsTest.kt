package com.multiregionvpn.ui.shared

import org.junit.Test
import kotlin.test.assertEquals

/**
 * Unit tests for VpnStats data class
 */
class VpnStatsTest {
    
    @Test
    fun `VpnStats should create with all properties`() {
        // GIVEN: Stats parameters
        val bytesSent = 1024L * 1024L // 1 MB
        val bytesReceived = 2048L * 1024L // 2 MB
        val connectionTime = 3600L // 1 hour
        val activeConnections = 5
        
        // WHEN: Creating VpnStats
        val stats = VpnStats(
            bytesSent = bytesSent,
            bytesReceived = bytesReceived,
            connectionTimeSeconds = connectionTime,
            activeConnections = activeConnections
        )
        
        // THEN: All properties should match
        assertEquals(bytesSent, stats.bytesSent)
        assertEquals(bytesReceived, stats.bytesReceived)
        assertEquals(connectionTime, stats.connectionTimeSeconds)
        assertEquals(activeConnections, stats.activeConnections)
    }
    
    @Test
    fun `VpnStats should have default zero values`() {
        // WHEN: Creating VpnStats with defaults
        val stats = VpnStats()
        
        // THEN: All values should be zero
        assertEquals(0L, stats.bytesSent)
        assertEquals(0L, stats.bytesReceived)
        assertEquals(0L, stats.connectionTimeSeconds)
        assertEquals(0, stats.activeConnections)
    }
    
    @Test
    fun `VpnStats should handle large byte counts`() {
        // GIVEN: Large byte counts (10 GB)
        val tenGB = 10L * 1024L * 1024L * 1024L
        
        // WHEN: Creating stats with large values
        val stats = VpnStats(bytesSent = tenGB, bytesReceived = tenGB)
        
        // THEN: Should handle large numbers correctly
        assertEquals(tenGB, stats.bytesSent)
        assertEquals(tenGB, stats.bytesReceived)
    }
    
    @Test
    fun `VpnStats should handle long connection times`() {
        // GIVEN: Long connection time (7 days in seconds)
        val sevenDays = 7L * 24L * 60L * 60L
        
        // WHEN: Creating stats
        val stats = VpnStats(connectionTimeSeconds = sevenDays)
        
        // THEN: Should handle large duration
        assertEquals(sevenDays, stats.connectionTimeSeconds)
        assertEquals(604800L, stats.connectionTimeSeconds) // 7 * 24 * 60 * 60
    }
    
    @Test
    fun `VpnStats copy should work correctly`() {
        // GIVEN: Initial stats
        val initial = VpnStats(
            bytesSent = 1000L,
            bytesReceived = 2000L,
            connectionTimeSeconds = 100L,
            activeConnections = 3
        )
        
        // WHEN: Copying with updated values
        val updated = initial.copy(
            bytesSent = 1500L,
            activeConnections = 5
        )
        
        // THEN: Only specified values should change
        assertEquals(1500L, updated.bytesSent)
        assertEquals(2000L, updated.bytesReceived) // Unchanged
        assertEquals(100L, updated.connectionTimeSeconds) // Unchanged
        assertEquals(5, updated.activeConnections)
    }
    
    @Test
    fun `VpnStats equality should work correctly`() {
        // GIVEN: Two identical VpnStats
        val stats1 = VpnStats(1000L, 2000L, 100L, 5)
        val stats2 = VpnStats(1000L, 2000L, 100L, 5)
        
        // THEN: Should be equal
        assertEquals(stats1, stats2)
        assertEquals(stats1.hashCode(), stats2.hashCode())
    }
    
    @Test
    fun `VpnStats should support incremental updates`() {
        // GIVEN: Initial stats
        var stats = VpnStats(bytesSent = 100L, bytesReceived = 200L)
        
        // WHEN: Simulating incremental updates
        stats = stats.copy(bytesSent = stats.bytesSent + 50L)
        stats = stats.copy(bytesReceived = stats.bytesReceived + 100L)
        
        // THEN: Values should accumulate
        assertEquals(150L, stats.bytesSent)
        assertEquals(300L, stats.bytesReceived)
    }
    
    @Test
    fun `VpnStats should handle zero active connections`() {
        // GIVEN: Stats with no active connections
        val stats = VpnStats(
            bytesSent = 1000L,
            bytesReceived = 2000L,
            connectionTimeSeconds = 100L,
            activeConnections = 0
        )
        
        // THEN: Should be valid
        assertEquals(0, stats.activeConnections)
    }
}

