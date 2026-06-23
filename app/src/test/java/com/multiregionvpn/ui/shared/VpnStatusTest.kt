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
        
        // THEN: Should have expected states
        assertTrue(allStates.contains(VpnStatus.PROTECTED), "Should have PROTECTED state")
        assertTrue(allStates.contains(VpnStatus.CONNECTED), "Should have CONNECTED state")
        assertTrue(allStates.contains(VpnStatus.DISCONNECTED), "Should have DISCONNECTED state")
        assertTrue(allStates.contains(VpnStatus.CONNECTING), "Should have CONNECTING state")
        assertTrue(allStates.contains(VpnStatus.ERROR), "Should have ERROR state")
    }
    
    @Test
    fun `VpnStatus should have correct display text`() {
        assertEquals("Protected", VpnStatus.PROTECTED.displayText)
        assertEquals("Connected", VpnStatus.CONNECTED.displayText)
        assertEquals("Disconnected", VpnStatus.DISCONNECTED.displayText)
        assertEquals("Connecting", VpnStatus.CONNECTING.displayText)
        assertEquals("Error", VpnStatus.ERROR.displayText)
    }
}
