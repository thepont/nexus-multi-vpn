package com.multiregionvpn.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class VpnErrorSecurityTest {

    @Test
    fun `fromException should not include stack trace in details`() {
        val exception = RuntimeException("Sensitive error occurred")
        val vpnError = VpnError.fromException(exception)

        // The details should not contain stack trace markers like "at " or the class name of the exception
        // if it's not part of the message.
        // More importantly, it should not be a long string containing multiple lines.

        val details = vpnError.details ?: ""

        assertFalse(details.contains("at com.multiregionvpn"), "Details should not contain stack trace")
        assertFalse(details.contains("\tat "), "Details should not contain stack trace tab markers")

        // In our fix, details should be equal to the message
        assertEquals(exception.message, vpnError.details)
    }

    @Test
    fun `fromException should map authentication errors correctly without leaking details`() {
        val exception = RuntimeException("auth failed for user admin")
        val vpnError = VpnError.fromException(exception)

        assertEquals(VpnError.ErrorType.AUTHENTICATION_FAILED, vpnError.type)
        assertEquals("auth failed for user admin", vpnError.message)
        assertEquals("auth failed for user admin", vpnError.details)
        assertFalse(vpnError.details?.contains("at ") == true)
    }
}
