package com.multiregionvpn.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Security test for VpnError to ensure sensitive information is not leaked.
 */
class VpnErrorSecurityTest {

    @Test
    fun `fromException does not leak stack trace in message or details`() {
        val exception = RuntimeException("Sensitive error message")
        val tunnelId = "test-tunnel"

        val vpnError = VpnError.fromException(exception, tunnelId)

        // The details field should only contain the class name, not the stack trace
        assertEquals("RuntimeException", vpnError.details)

        // Verify stack trace markers are not present
        val stackTraceIndicator = "at com.multiregionvpn"
        assertFalse("Details should not contain stack trace: ${vpnError.details}",
            vpnError.details?.contains(stackTraceIndicator) ?: false)

        // The message should still be the exception message (user-provided or system)
        assertEquals("Sensitive error message", vpnError.message)
    }

    @Test
    fun `fromException handles null message gracefully`() {
        val exception = NullPointerException()
        val vpnError = VpnError.fromException(exception)

        assertEquals("NullPointerException", vpnError.details)
        assertEquals("Unknown error", vpnError.message)
    }
}
