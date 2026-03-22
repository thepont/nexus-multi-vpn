package com.multiregionvpn.core

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse

class VpnErrorSecurityTest {

    @Test
    fun `fromException should not leak stack trace in details`() {
        val exception = RuntimeException("Sensitive error message")
        val vpnError = VpnError.fromException(exception)

        // Assert that details contains only the simple class name
        assertEquals("RuntimeException", vpnError.details)

        // Assert that details DOES NOT contain parts of the stack trace
        assertFalse("Details should not contain stack trace",
            vpnError.details?.contains("at com.multiregionvpn") ?: false)
    }

    @Test
    fun `fromException should correctly identify authentication errors without leaking stack trace`() {
        val exception = Exception("auth failed")
        val vpnError = VpnError.fromException(exception)

        assertEquals(VpnError.ErrorType.AUTHENTICATION_FAILED, vpnError.type)
        assertEquals("Exception", vpnError.details)
    }
}
