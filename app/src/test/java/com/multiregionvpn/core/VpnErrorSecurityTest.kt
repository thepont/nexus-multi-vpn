package com.multiregionvpn.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Security unit test for VpnError.
 * Ensures that sensitive information like stack traces is not leaked in error messages.
 */
class VpnErrorSecurityTest {

    @Test
    fun `fromException should not include stack trace in details or message`() {
        // Create an exception with a stack trace
        val exception = RuntimeException("Sensitive error message")

        // Generate VpnError from exception
        val vpnError = VpnError.fromException(exception)

        // Verify message contains the exception message
        assertTrue("Message should contain exception message",
            vpnError.message.contains("Sensitive error message"))

        // Verify details does not contain stack trace markers
        val details = vpnError.details ?: ""
        assertFalse("Details should not contain stack trace markers (at )",
            details.contains("\tat "))
        assertFalse("Details should not contain stack trace markers (Exception in thread)",
            details.contains("Exception in thread"))

        // In our hardened version, details should be equal to message
        assertTrue("Details should be equal to message in hardened version",
            details == exception.message)
    }

    @Test
    fun `getUserMessage should not leak internal details`() {
        val exception = RuntimeException("Internal database error at line 42")
        val vpnError = VpnError.fromException(exception)

        val userMessage = vpnError.getUserMessage()

        // The user message should contain the error message but not be the full internal detail
        assertTrue("User message should be descriptive", userMessage.length > 20)

        // Verify it doesn't just return the stack trace (which we already fixed)
        assertFalse("User message should not contain stack trace markers",
            userMessage.contains("\tat "))
    }
}
