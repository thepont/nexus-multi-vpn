package com.multiregionvpn.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnErrorSecurityTest {

    @Test
    fun `fromException should not leak stack trace in details`() {
        val exception = RuntimeException("Sensitive error message")
        val vpnError = VpnError.fromException(exception)

        val details = vpnError.details ?: ""

        // This is expected to FAIL before the fix
        // Stack traces usually contain "at com.multiregionvpn..."
        assertFalse("Stack trace should not be present in details: $details",
            details.contains("at ") && details.contains("VpnErrorSecurityTest"))
    }

    @Test
    fun `fromException should contain message in details instead of stack trace`() {
        val message = "Auth failed"
        val exception = RuntimeException(message)
        val vpnError = VpnError.fromException(exception)

        // This will verify that we still have some useful info, just not the stack trace
        assertTrue("Details should contain message or class name",
            vpnError.details?.contains(message) == true || vpnError.details?.contains("RuntimeException") == true)
    }
}
