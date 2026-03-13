package com.multiregionvpn.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnErrorSecurityTest {

    @Test
    fun `fromException should not leak stack trace in details`() {
        val exception = RuntimeException("Sensitive operation failed")
        val vpnError = VpnError.fromException(exception)

        // The details should be the simple name of the class, not the stack trace
        assertTrue("Details should be the exception class name", vpnError.details == "RuntimeException")

        // Ensure stack trace markers are not present
        assertFalse("Details should not contain stack trace markers", vpnError.details?.contains("at ") == true)
        assertFalse("Details should not contain package names", vpnError.details?.contains("java.lang.") == true)
    }

    @Test
    fun `fromException should only include exception message in message field`() {
        val exceptionMessage = "Auth failed"
        val exception = IllegalArgumentException(exceptionMessage)
        val vpnError = VpnError.fromException(exception)

        assertTrue("Message should match exception message", vpnError.message == exceptionMessage)
        assertFalse("Message should not contain stack trace", vpnError.message.contains("at com.multiregionvpn"))
    }
}
