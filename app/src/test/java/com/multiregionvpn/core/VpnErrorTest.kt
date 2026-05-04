package com.multiregionvpn.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VpnErrorTest {

    @Test
    fun testFromExceptionDoesNotIncludeStackTrace() {
        val exception = RuntimeException("Sensitive error message")
        val vpnError = VpnError.fromException(exception)

        // details should be e.toString(), which is "java.lang.RuntimeException: Sensitive error message"
        // It should NOT contain the full stack trace.
        assertEquals(exception.toString(), vpnError.details)

        val stackTraceIndicator = "at com.multiregionvpn.core.VpnErrorTest"
        assertFalse("Error details should not contain stack trace",
            vpnError.details?.contains(stackTraceIndicator) ?: false)
    }

    @Test
    fun testAuthenticationErrorCategorization() {
        val authException = RuntimeException("authentication failed")
        val vpnError = VpnError.fromException(authException)

        assertEquals(VpnError.ErrorType.AUTHENTICATION_FAILED, vpnError.type)
    }
}
