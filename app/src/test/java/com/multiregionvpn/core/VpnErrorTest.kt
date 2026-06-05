package com.multiregionvpn.core

import org.junit.Test
import org.junit.Assert.*

class VpnErrorTest {
    @Test
    fun `fromException should not include stack trace in details`() {
        val exception = RuntimeException("Sensitive error details")
        val vpnError = VpnError.fromException(exception)

        // The details should not be the full stack trace
        val stackTraceIndicator = "at com.multiregionvpn"
        assertFalse("Error details should not contain stack trace",
            vpnError.details?.contains(stackTraceIndicator) ?: false)

        // It should still contain the message
        assertTrue("Error details should contain the message",
            vpnError.details?.contains("Sensitive error details") ?: false)
    }
}
