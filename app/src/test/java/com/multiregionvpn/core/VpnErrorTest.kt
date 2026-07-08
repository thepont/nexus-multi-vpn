package com.multiregionvpn.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnErrorTest {

    @Test
    fun `getUserMessage should not contain stack trace`() {
        val exception = RuntimeException("Secret connection error")
        val vpnError = VpnError.fromException(exception)

        val userMessage = vpnError.getUserMessage()

        // The message should contain the error message
        assertTrue("User message should contain error message", userMessage.contains("Secret connection error"))

        // The message should NOT contain stack trace elements
        assertFalse("User message should not contain stack trace", userMessage.contains("at com.multiregionvpn.core.VpnErrorTest"))
        assertFalse("User message should not contain stack trace", userMessage.contains("RuntimeException"))
    }
}
