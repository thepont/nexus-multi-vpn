package com.multiregionvpn.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnErrorLeakTest {

    @Test
    fun `getUserMessage should not contain stack trace details`() {
        val exception = Exception("sensitive error message")
        val vpnError = VpnError.fromException(exception)

        val userMessage = vpnError.getUserMessage()

        // The user message should contain the high-level message
        assertTrue("User message should contain the error message",
            userMessage.contains("sensitive error message"))

        // The user message should NOT contain stack trace details (e.g., class names, line numbers)
        assertFalse("User message should not leak stack trace details",
            userMessage.contains("at com.multiregionvpn.core.VpnErrorLeakTest"))
    }
}
