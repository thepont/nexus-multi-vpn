package com.multiregionvpn.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnErrorTest {

    @Test
    fun `fromException should not include full stack trace in details`() {
        val exceptionMessage = "Connection refused"
        val exception = RuntimeException(exceptionMessage)

        val vpnError = VpnError.fromException(exception)

        // The details should equal the message or the string representation, but NOT the full stack trace
        assertTrue("Details should be concise", vpnError.details?.length ?: 0 < 500)
        assertFalse("Details should not contain stack trace markers", vpnError.details?.contains("at com.multiregionvpn") ?: false)
        assertEquals(exceptionMessage, vpnError.details)
    }

    @Test
    fun `fromException should categorize authentication errors correctly`() {
        val exception = RuntimeException("Authentication failed for user")
        val vpnError = VpnError.fromException(exception)

        assertEquals(VpnError.ErrorType.AUTHENTICATION_FAILED, vpnError.type)
        assertEquals("Authentication failed for user", vpnError.message)
    }

    @Test
    fun `getUserMessage should include details when available`() {
        val vpnError = VpnError(
            type = VpnError.ErrorType.CONNECTION_FAILED,
            message = "Generic error",
            details = "Specific connection timeout"
        )

        val userMessage = vpnError.getUserMessage()
        assertTrue(userMessage.contains("Specific connection timeout"))
    }
}
