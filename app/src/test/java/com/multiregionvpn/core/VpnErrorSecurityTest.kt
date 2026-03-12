package com.multiregionvpn.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnErrorSecurityTest {

    @Test
    fun `fromException should not leak stack trace in details`() {
        val exception = RuntimeException("Sensitive error message")
        val vpnError = VpnError.fromException(exception)

        val details = vpnError.details
        if (details != null) {
            // Check that details doesn't contain common stack trace markers
            assertFalse("Details should not contain stack trace: $details",
                details.contains("at com.multiregionvpn") ||
                details.contains("RuntimeException") ||
                details.split("\n").size > 3)
        }
    }

    @Test
    fun `fromException should not leak stack trace in message`() {
        val exception = RuntimeException("Sensitive error message")
        val vpnError = VpnError.fromException(exception)

        val message = vpnError.message
        assertFalse("Message should not contain stack trace: $message",
            message.contains("at com.multiregionvpn"))
    }
}
