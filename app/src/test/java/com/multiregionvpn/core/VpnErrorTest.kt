package com.multiregionvpn.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnErrorTest {

    @Test
    fun `getUserMessage should not leak stack traces`() {
        val exception = RuntimeException("Connection timed out")
        val vpnError = VpnError.fromException(exception)

        val userMessage = vpnError.getUserMessage()

        // Assert that it DOES NOT contain the stack trace
        assertTrue("Message should contain the error summary", userMessage.contains("Connection timed out"))

        // Check for stack trace leakage
        val stackTraceIndicator = "VpnErrorTest.kt"
        assertFalse("VULNERABILITY FIXED: Message should NOT contain stack trace details", userMessage.contains(stackTraceIndicator))
    }

    @Test
    fun `getUserMessage should contain user-friendly guidance`() {
        val exception = RuntimeException("auth failed")
        val vpnError = VpnError.fromException(exception)

        val userMessage = vpnError.getUserMessage()

        assertTrue("Should contain guidance for auth failure", userMessage.contains("check your NordVPN credentials"))
    }
}
