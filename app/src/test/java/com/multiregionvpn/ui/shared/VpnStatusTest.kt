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
        assertTrue(allStates.contains(VpnStatus.CONNECTED), "Should have CONNECTED state")
        assertTrue(allStates.contains(VpnStatus.DISCONNECTED), "Should have DISCONNECTED state")
        assertTrue(allStates.contains(VpnStatus.CONNECTING), "Should have CONNECTING state")
        assertTrue(allStates.contains(VpnStatus.ERROR), "Should have ERROR state")
    }
    
    @Test
    fun `VpnStatus states should be distinguishable`() {
        // GIVEN: Different VpnStatus values
        val connected = VpnStatus.CONNECTED
        val disconnected = VpnStatus.DISCONNECTED
        val connecting = VpnStatus.CONNECTING
        val error = VpnStatus.ERROR
        
        // THEN: All should be different
        assertTrue(connected != disconnected, "CONNECTED != DISCONNECTED")
        assertTrue(connected != connecting, "CONNECTED != CONNECTING")
        assertTrue(connected != error, "CONNECTED != ERROR")
        assertTrue(disconnected != connecting, "DISCONNECTED != CONNECTING")
        assertTrue(disconnected != error, "DISCONNECTED != ERROR")
        assertTrue(connecting != error, "CONNECTING != ERROR")
    }
    
    @Test
    fun `VpnStatus should have correct string representation`() {
        // GIVEN: VpnStatus values
        // WHEN: Converting to string
        // THEN: Should match enum name
        assertEquals("CONNECTED", VpnStatus.CONNECTED.name)
        assertEquals("DISCONNECTED", VpnStatus.DISCONNECTED.name)
        assertEquals("CONNECTING", VpnStatus.CONNECTING.name)
        assertEquals("ERROR", VpnStatus.ERROR.name)
    }
    
    @Test
    fun `VpnStatus should support when expressions`() {
        // GIVEN: A function that uses when with VpnStatus
        fun getStatusMessage(status: VpnStatus): String = when (status) {
            VpnStatus.CONNECTED -> "VPN is active"
            VpnStatus.DISCONNECTED -> "VPN is off"
            VpnStatus.CONNECTING -> "Establishing connection..."
            VpnStatus.ERROR -> "Connection failed"
        }
        
        // THEN: Should work correctly for all states
        assertEquals("VPN is active", getStatusMessage(VpnStatus.CONNECTED))
        assertEquals("VPN is off", getStatusMessage(VpnStatus.DISCONNECTED))
        assertEquals("Establishing connection...", getStatusMessage(VpnStatus.CONNECTING))
        assertEquals("Connection failed", getStatusMessage(VpnStatus.ERROR))
    }
}

