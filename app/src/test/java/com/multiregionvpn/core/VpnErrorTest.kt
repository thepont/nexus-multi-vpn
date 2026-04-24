package com.multiregionvpn.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnErrorTest {

    @Test
    fun `fromException should not include stack trace in details`() {
        val exception = RuntimeException("Sensitive internal error")
        val tunnelId = "test_tunnel"

        val vpnError = VpnError.fromException(exception, tunnelId)

        // The details should not contain common stack trace markers
        val details = vpnError.details ?: ""
        assertFalse("Details should not contain stack trace: $details",
            details.contains("at com.multiregionvpn") || details.contains("RuntimeException"))
    }

    @Test
    fun `fromException should categorize authentication errors correctly`() {
        val exception = RuntimeException("authentication failed")
        val vpnError = VpnError.fromException(exception)

        assertTrue(vpnError.type == VpnError.ErrorType.AUTHENTICATION_FAILED)
    }
}
