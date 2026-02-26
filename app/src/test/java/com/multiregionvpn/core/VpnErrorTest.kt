package com.multiregionvpn.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VpnErrorTest {

    @Test
    fun `fromException should not include stack trace in details`() {
        val exception = RuntimeException("Sensitive error message")
        val vpnError = VpnError.fromException(exception)

        assertEquals("Sensitive error message", vpnError.message)
        assertEquals("Sensitive error message", vpnError.details)

        // Ensure stack trace info is NOT present in details
        val stackTrace = exception.stackTraceToString()
        assertFalse("Details should not contain stack trace info", vpnError.details!!.contains("at com.multiregionvpn"))
    }

    @Test
    fun `fromException should map authentication errors correctly`() {
        val exception = RuntimeException("authentication failed for user")
        val vpnError = VpnError.fromException(exception)

        assertEquals(VpnError.ErrorType.AUTHENTICATION_FAILED, vpnError.type)
    }

    @Test
    fun `fromException should map connection errors correctly`() {
        val exception = RuntimeException("connection timeout to server")
        val vpnError = VpnError.fromException(exception)

        assertEquals(VpnError.ErrorType.CONNECTION_FAILED, vpnError.type)
    }
}
