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

        // Verify that it doesn't contain common stack trace markers
        val details = vpnError.details ?: ""
        assertFalse("Details should not contain stack trace markers", details.contains("at com.multiregionvpn"))
        assertFalse("Details should not contain stack trace markers", details.contains("\n\tat "))
    }

    @Test
    fun `fromException should correctly categorize authentication errors`() {
        val exception = RuntimeException("Authentication failed for user")
        val vpnError = VpnError.fromException(exception)

        assertEquals(VpnError.ErrorType.AUTHENTICATION_FAILED, vpnError.type)
    }

    @Test
    fun `fromException should correctly categorize connection errors`() {
        val exception = RuntimeException("Connection timed out")
        val vpnError = VpnError.fromException(exception)

        assertEquals(VpnError.ErrorType.CONNECTION_FAILED, vpnError.type)
    }
}
