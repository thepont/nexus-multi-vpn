package com.multiregionvpn.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VpnErrorTest {

    @Test
    fun `fromException should not include stack trace in details`() {
        val exception = RuntimeException("Test error message")
        val vpnError = VpnError.fromException(exception)

        // The details should equal the message, not the stack trace
        assertEquals("Test error message", vpnError.details)

        // Ensure it doesn't contain common stack trace markers
        val stackTrace = exception.stackTraceToString()
        assertFalse("Details should not contain full stack trace",
            vpnError.details == stackTrace)
    }

    @Test
    fun `fromException should correctly categorize authentication errors`() {
        val authException = RuntimeException("Authentication failed: invalid credentials")
        val vpnError = VpnError.fromException(authException)

        assertEquals(VpnError.ErrorType.AUTHENTICATION_FAILED, vpnError.type)
        assertEquals("Authentication failed: invalid credentials", vpnError.details)
    }
}
