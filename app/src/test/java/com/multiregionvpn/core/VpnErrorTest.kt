package com.multiregionvpn.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnErrorTest {

    @Test
    fun `getUserMessage should not contain stack trace details`() {
        val exception = RuntimeException("Sensitive exception")
        val vpnError = VpnError.fromException(exception)

        val userMessage = vpnError.getUserMessage()

        // The message should contain the exception message
        assertTrue("User message should contain exception message", userMessage.contains("Sensitive exception"))

        // The message should NOT contain stack trace elements
        assertFalse("User message should not contain stack trace elements", userMessage.contains("at com.multiregionvpn"))
        assertFalse("User message should not contain stack trace elements", userMessage.contains("RuntimeException"))
    }
}
