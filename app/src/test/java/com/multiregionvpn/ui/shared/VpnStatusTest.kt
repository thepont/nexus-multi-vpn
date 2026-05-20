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
        
        // THEN: Verify all 4 states are present
        assertEquals(4, allStates.size, "VpnStatus should have 4 states")
        assertTrue(allStates.contains(VpnStatus.PROTECTED), "Should have PROTECTED state")
        assertTrue(allStates.contains(VpnStatus.DISCONNECTED), "Should have DISCONNECTED state")
        assertTrue(allStates.contains(VpnStatus.CONNECTING), "Should have CONNECTING state")
        assertTrue(allStates.contains(VpnStatus.ERROR), "Should have ERROR state")
    }

    @Test
    fun `VpnStatus states should be distinguishable`() {
        // GIVEN: Different VpnStatus values
        val protected = VpnStatus.PROTECTED
        val disconnected = VpnStatus.DISCONNECTED
        val connecting = VpnStatus.CONNECTING
        val error = VpnStatus.ERROR
        
        // THEN: Verify they are not equal
        assertTrue(protected != disconnected)
        assertTrue(protected != connecting)
        assertTrue(protected != error)
        assertTrue(disconnected != connecting)
        assertTrue(disconnected != error)
        assertTrue(connecting != error)
    }

    @Test
    fun `VpnStatus should have correct string representation`() {
        // GIVEN: VpnStatus values

        // THEN: Verify name matches enum declaration
        assertEquals("PROTECTED", VpnStatus.PROTECTED.name)
        assertEquals("DISCONNECTED", VpnStatus.DISCONNECTED.name)
        assertEquals("CONNECTING", VpnStatus.CONNECTING.name)
        assertEquals("ERROR", VpnStatus.ERROR.name)
    }

    @Test
    fun `VpnStatus should have correct display text`() {
        // GIVEN: VpnStatus values

        // THEN: Verify displayText property
        assertEquals("Protected", VpnStatus.PROTECTED.displayText)
        assertEquals("Disconnected", VpnStatus.DISCONNECTED.displayText)
        assertEquals("Connecting", VpnStatus.CONNECTING.displayText)
        assertEquals("Error", VpnStatus.ERROR.displayText)
    }

    @Test
    fun `VpnStatus should support when expressions`() {
        // GIVEN: A function that uses when with VpnStatus
        fun getStatusMessage(status: VpnStatus): String = when (status) {
            VpnStatus.PROTECTED -> "VPN is active"
            VpnStatus.DISCONNECTED -> "VPN is off"
            VpnStatus.CONNECTING -> "Establishing connection..."
            VpnStatus.ERROR -> "Connection failed"
        }
        
        // THEN: Verify when expression works
        assertEquals("VPN is active", getStatusMessage(VpnStatus.PROTECTED))
        assertEquals("VPN is off", getStatusMessage(VpnStatus.DISCONNECTED))
        assertEquals("Establishing connection...", getStatusMessage(VpnStatus.CONNECTING))
        assertEquals("Connection failed", getStatusMessage(VpnStatus.ERROR))
    }
}
