package com.multiregionvpn.core

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals

class VpnErrorSecurityTest {

    @Test
    fun `fromException should not include stack trace in details or message`() {
        val exception = RuntimeException("Connection timeout")

        val vpnError = VpnError.fromException(exception)

        // Ensure stack trace is not in details
        val stackTrace = exception.stackTraceToString()
        assertFalse(vpnError.details?.contains("at com.multiregionvpn") == true,
            "Details should not contain stack trace elements")

        // If details exists, it should be equal to message or some other non-stacktrace string
        if (vpnError.details != null) {
            assertEquals(exception.message, vpnError.details,
                "Details should be the exception message, not stack trace")
        }
    }

    @Test
    fun `fromException should handle null message securely`() {
        val exception = RuntimeException()
        val vpnError = VpnError.fromException(exception)

        assertEquals("Unknown error", vpnError.message)
        assertFalse(vpnError.details?.contains("at com.multiregionvpn") == true,
            "Details should not contain stack trace elements even for null message")
    }
}
