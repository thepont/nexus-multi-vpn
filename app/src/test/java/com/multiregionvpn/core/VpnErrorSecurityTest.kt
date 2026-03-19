package com.multiregionvpn.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VpnErrorSecurityTest {

    @Test
    fun `fromException should not leak stack trace in details`() {
        val exceptionMessage = "Something went wrong"
        val exception = RuntimeException(exceptionMessage)

        val vpnError = VpnError.fromException(exception)

        // The details field should only contain the class name, not the stack trace
        assertEquals("RuntimeException", vpnError.details)

        // Double check that it doesn't contain common stack trace elements
        val details = vpnError.details ?: ""
        assertFalse("Details should not contain 'at ' (stack trace element)", details.contains("at "))
        assertFalse("Details should not contain method name", details.contains("fromException"))
    }

    @Test
    fun `fromException should identify auth errors while maintaining privacy`() {
        val exception = IllegalArgumentException("invalid credentials provided")
        val vpnError = VpnError.fromException(exception)

        assertEquals(VpnError.ErrorType.AUTHENTICATION_FAILED, vpnError.type)
        assertEquals("IllegalArgumentException", vpnError.details)
    }
}
