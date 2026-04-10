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

        // Verify that details matches the exception message, not the stack trace
        assertEquals(exceptionMessage, vpnError.details)

        // Ensure it doesn't contain common stack trace elements
        val stackTrace = exception.stackTraceToString()
        assertFalse("Details should not contain stack trace", vpnError.details?.contains("at com.multiregionvpn") ?: false)
    }

    @Test
    fun `fromException should categorize authentication errors`() {
        val exception = RuntimeException("Authentication failed")
        val vpnError = VpnError.fromException(exception)

        assertEquals(VpnError.ErrorType.AUTHENTICATION_FAILED, vpnError.type)
    }

    @Test
    fun `fromException should categorize connection errors`() {
        val exception = RuntimeException("Connection timeout")
        val vpnError = VpnError.fromException(exception)

        assertEquals(VpnError.ErrorType.CONNECTION_FAILED, vpnError.type)
    }
}
