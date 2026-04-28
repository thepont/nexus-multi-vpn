package com.multiregionvpn.core

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VpnErrorTest {

    @Test
    fun `fromException should not include full stack trace in details`() {
        val exception = RuntimeException("Sensitive connection error")
        val vpnError = VpnError.fromException(exception)

        // The details should be just the string representation of the exception, not the full stack trace
        assertEquals(exception.toString(), vpnError.details)

        // A stack trace would typically contain multiple lines with "at com.multiregionvpn..."
        assertFalse(vpnError.details?.contains("\tat ") == true)
        assertFalse(vpnError.details?.contains("\n") == true)
    }

    @Test
    fun `fromException should correctly identify authentication errors`() {
        val authException = RuntimeException("Authentication failed: invalid password")
        val vpnError = VpnError.fromException(authException)

        assertEquals(VpnError.ErrorType.AUTHENTICATION_FAILED, vpnError.type)
        assertTrue(vpnError.message.contains("Authentication failed"))
    }
}
