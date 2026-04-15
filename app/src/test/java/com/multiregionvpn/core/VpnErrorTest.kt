package com.multiregionvpn.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VpnErrorTest {

    @Test
    fun `fromException should sanitize details and not include stack trace`() {
        val exceptionMessage = "Auth failed"
        val exception = Exception(exceptionMessage)
        val tunnelId = "test_tunnel"

        val vpnError = VpnError.fromException(exception, tunnelId)

        // Message should be preserved
        assertEquals(exceptionMessage, vpnError.message)

        // Details should match the message, NOT contain the stack trace
        assertEquals(exceptionMessage, vpnError.details)

        // Verify it doesn't contain common stack trace markers
        val stackTraceString = exception.stackTraceToString()
        assertFalse("Details should not contain stack trace", vpnError.details?.contains("at com.multiregionvpn") ?: true)
        assertFalse("Details should not contain stack trace", vpnError.details?.contains(".kt:") ?: true)
    }

    @Test
    fun `fromException should correctly categorize authentication errors`() {
        val exception = Exception("Invalid credentials")
        val vpnError = VpnError.fromException(exception)

        assertEquals(VpnError.ErrorType.AUTHENTICATION_FAILED, vpnError.type)
    }
}
