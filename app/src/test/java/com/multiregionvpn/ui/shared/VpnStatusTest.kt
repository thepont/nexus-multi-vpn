package com.multiregionvpn.ui.shared

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for VpnStatus enum
 */
class VpnStatusTest {
    
    @Test
    fun `VpnStatus should have all expected states`() {
        // GIVEN: VpnStatus enum
        val allStates = VpnStatus.entries
        
        // THEN: Should have exactly 4 states
        assertEquals(4, allStates.size, "VpnStatus should have 4 states")
        
        // AND: Should contain all expected states
        assertTrue(allStates.contains(VpnStatus.PROTECTED), "Should have PROTECTED state")
        assertTrue(allStates.contains(VpnStatus.DISPROTECTED), "Should have DISPROTECTED state")
        assertTrue(allStates.contains(VpnStatus.CONNECTING), "Should have CONNECTING state")
        assertTrue(allStates.contains(VpnStatus.ERROR), "Should have ERROR state")
    }
    
    @Test
    fun `VpnStatus states should be distinguishable`() {
        // GIVEN: Different VpnStatus values
        val connected = VpnStatus.PROTECTED
        val disconnected = VpnStatus.DISPROTECTED
        val connecting = VpnStatus.CONNECTING
        val error = VpnStatus.ERROR
        
        // THEN: All should be different
        assertTrue(connected != disconnected, "PROTECTED != DISPROTECTED")
        assertTrue(connected != connecting, "PROTECTED != CONNECTING")
        assertTrue(connected != error, "PROTECTED != ERROR")
        assertTrue(disconnected != connecting, "DISPROTECTED != CONNECTING")
        assertTrue(disconnected != error, "DISPROTECTED != ERROR")
        assertTrue(connecting != error, "CONNECTING != ERROR")
    }
    
    @Test
    fun `VpnStatus should have correct string representation`() {
        // GIVEN: VpnStatus values
        // WHEN: Converting to string
        // THEN: Should match enum name
        assertEquals("PROTECTED", VpnStatus.PROTECTED.name)
        assertEquals("DISPROTECTED", VpnStatus.DISPROTECTED.name)
        assertEquals("CONNECTING", VpnStatus.CONNECTING.name)
        assertEquals("ERROR", VpnStatus.ERROR.name)
    }
    
    @Test
    fun `VpnStatus should support when expressions`() {
        // GIVEN: A function that uses when with VpnStatus
        fun getStatusMessage(status: VpnStatus): String = when (status) {
            VpnStatus.PROTECTED -> "VPN is active"
            VpnStatus.DISPROTECTED -> "VPN is off"
            VpnStatus.CONNECTING -> "Establishing connection..."
            VpnStatus.ERROR -> "Connection failed"
        }
        
        // THEN: Should work correctly for all states
        assertEquals("VPN is active", getStatusMessage(VpnStatus.PROTECTED))
        assertEquals("VPN is off", getStatusMessage(VpnStatus.DISPROTECTED))
        assertEquals("Establishing connection...", getStatusMessage(VpnStatus.CONNECTING))
        assertEquals("Connection failed", getStatusMessage(VpnStatus.ERROR))
    }
}

