package com.multiregionvpn.core

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class VpnErrorTest {

    @Test
    fun `fromException should not include stack trace in details`() {
        val exception = RuntimeException("Sensitive operation failed")
        val vpnError = VpnError.fromException(exception)

        assertNotNull(vpnError.details)
        // Currently, it includes the stack trace, so this assertion will fail if we want it SECURE.
        // For now, I will write the test to FAIL if it contains typical stack trace markers.
        // This confirms the vulnerability.

        val stackTraceMarker = "at com.multiregionvpn.core.VpnErrorTest"
        assertFalse("Details should not contain stack trace information",
            vpnError.details?.contains(stackTraceMarker) ?: false)
    }

    @Test
    fun `fromException should include exception message in details`() {
        val message = "Auth failed"
        val exception = RuntimeException(message)
        val vpnError = VpnError.fromException(exception)

        assertTrue("Details should contain the exception message",
            vpnError.details?.contains(message) ?: false)
    }
}
