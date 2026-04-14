package com.multiregionvpn.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VpnErrorTest {

    @Test
    fun `fromException should not include stack trace in details`() {
        val exceptionMessage = "Test exception message"
        val exception = RuntimeException(exceptionMessage)

        val vpnError = VpnError.fromException(exception)

        // The details should be the exception message, not the stack trace
        assertEquals(exceptionMessage, vpnError.details)

        // Ensure it doesn't contain common stack trace elements
        val details = vpnError.details ?: ""
        assertFalse("Details should not contain class name", details.contains("java.lang.RuntimeException"))
        assertFalse("Details should not contain 'at '", details.contains("at "))
    }

    @Test
    fun `fromException handles null message gracefully`() {
        val exception = RuntimeException()

        val vpnError = VpnError.fromException(exception)

        assertEquals("Unknown error", vpnError.message)
        assertEquals(null, vpnError.details)
    }

    @Test
    fun `fromException correctly identifies error types`() {
        val authException = RuntimeException("Authentication failed")
        val connException = RuntimeException("Connection timed out")

        val authError = VpnError.fromException(authException)
        val connError = VpnError.fromException(connException)

        assertEquals(VpnError.ErrorType.AUTHENTICATION_FAILED, authError.type)
        assertEquals(VpnError.ErrorType.CONNECTION_FAILED, connError.type)
    }
}
