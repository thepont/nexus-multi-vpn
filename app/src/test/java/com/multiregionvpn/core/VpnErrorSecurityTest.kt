package com.multiregionvpn.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class VpnErrorSecurityTest {

    @Test
    fun `fromException should not include stack trace in details`() {
        val exception = RuntimeException("Test exception with stack trace")
        val vpnError = VpnError.fromException(exception)

        val stackTraceIndicator = "at com.multiregionvpn"

        assertNotNull("Details should not be null", vpnError.details)
        assertFalse(
            "VpnError details should not contain stack trace information",
            vpnError.details!!.contains(stackTraceIndicator)
        )
        assertFalse(
            "VpnError message should not contain stack trace information",
            vpnError.message.contains(stackTraceIndicator)
        )
    }

    @Test
    fun `fromException should not include stack trace in details for auth error`() {
        val exception = RuntimeException("Authentication failed")
        val vpnError = VpnError.fromException(exception)

        val stackTraceIndicator = "at com.multiregionvpn"

        assertNotNull("Details should not be null", vpnError.details)
        assertFalse(
            "VpnError details should not contain stack trace information even for auth errors",
            vpnError.details!!.contains(stackTraceIndicator)
        )
    }
}
