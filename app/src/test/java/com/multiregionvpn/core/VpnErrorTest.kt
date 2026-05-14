package com.multiregionvpn.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VpnErrorTest {

    @Test
    fun `fromException should not include stack trace in details`() {
        val message = "Test exception message"
        val exception = Exception(message)

        val vpnError = VpnError.fromException(exception)

        assertEquals(message, vpnError.message)
        // e.toString() typically returns "java.lang.Exception: message"
        assertEquals(exception.toString(), vpnError.details)

        // Ensure it doesn't contain multiple lines (typical of stack traces)
        assertFalse("Details should not contain stack trace lines", vpnError.details?.contains("\tat ") ?: false)
    }

    @Test
    fun `fromException should categorize authentication errors`() {
        val exception = Exception("Authentication failed")
        val vpnError = VpnError.fromException(exception)

        assertEquals(VpnError.ErrorType.AUTHENTICATION_FAILED, vpnError.type)
    }

    @Test
    fun `getUserMessage should include details`() {
        val exception = Exception("Internal error")
        val vpnError = VpnError.fromException(exception)

        val userMessage = vpnError.getUserMessage()
        assertEquals("An unexpected error occurred:\n\njava.lang.Exception: Internal error", userMessage)
    }
}
