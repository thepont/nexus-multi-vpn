package com.multiregionvpn.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnErrorTest {

    @Test
    fun `fromException should not include stack trace in details`() {
        val exception = RuntimeException("Test exception message")
        val vpnError = VpnError.fromException(exception)

        // The stack trace typically contains the class name and line numbers
        val stackTraceIndicator = "VpnErrorTest.kt"
        assertFalse("Stack trace should NOT be present in details",
            vpnError.details?.contains(stackTraceIndicator) == true)

        // It should also not contain typical stack trace line starts
        assertFalse("Details should not contain stack trace markers",
            vpnError.details?.contains("\tat ") == true)
    }

    @Test
    fun `getUserMessage should not contain internal class names if sanitized`() {
        val exception = RuntimeException("Secret internal error")
        val vpnError = VpnError.fromException(exception)
        val userMessage = vpnError.getUserMessage()

        assertTrue("User message should contain the exception message",
            userMessage.contains("Secret internal error"))

        assertFalse("User message should NOT contain stack trace elements",
            userMessage.contains("VpnErrorTest.kt"))
    }
}
