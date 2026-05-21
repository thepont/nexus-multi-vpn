package com.multiregionvpn.core

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VpnErrorTest {

    @Test
    fun `fromException should not include full stack trace in details`() {
        val message = "Authentication failed"
        val exception = RuntimeException(message)

        val vpnError = VpnError.fromException(exception)

        // details should contain the exception class and message
        assertEquals("java.lang.RuntimeException: $message", vpnError.details)

        // details should NOT contain typical stack trace elements
        assertFalse(vpnError.details?.contains("at com.multiregionvpn") == true, "Details should not contain stack trace")
    }

    @Test
    fun `fromException should correctly identify authentication errors`() {
        val exception = RuntimeException("Invalid credentials")
        val vpnError = VpnError.fromException(exception)

        assertEquals(VpnError.ErrorType.AUTHENTICATION_FAILED, vpnError.type)
    }

    @Test
    fun `fromException should correctly identify connection errors`() {
        val exception = RuntimeException("Connection timeout")
        val vpnError = VpnError.fromException(exception)

        assertEquals(VpnError.ErrorType.CONNECTION_FAILED, vpnError.type)
    }
}
