package com.multiregionvpn.core

import org.junit.Test
import kotlin.test.*

/**
 * Unit tests for VpnError
 */
class VpnErrorTest {

    @Test
    fun `fromException does NOT include stack trace in details`() {
        val exception = Exception("Test exception message")
        val vpnError = VpnError.fromException(exception)

        assertNotNull(vpnError.details)
        // Check if details contains sanitized info
        assertEquals("Exception: Test exception message", vpnError.details)
        // Ensure it does NOT contain stack trace indicators
        assertFalse(vpnError.details!!.contains("at com.multiregionvpn.core.VpnErrorTest"))
    }
}
