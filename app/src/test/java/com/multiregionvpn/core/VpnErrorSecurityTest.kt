package com.multiregionvpn.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Security test to verify that VpnError does not leak internal information
 * (stack traces) in its user-friendly messages (CWE-209).
 */
class VpnErrorSecurityTest {

    @Test
    fun `getUserMessage should not contain details or stack traces`() {
        val message = "Simple error message"
        val stackTrace = "java.lang.RuntimeException: something went wrong\n\tat com.multiregionvpn.core.VpnError.fromException(VpnError.kt:123)"

        val error = VpnError(
            type = VpnError.ErrorType.TUNNEL_ERROR,
            message = message,
            details = stackTrace
        )

        val userMessage = error.getUserMessage()

        // Ensure the basic message is present
        assertTrue("User message should contain the basic error message", userMessage.contains(message))

        // CRITICAL: Ensure the stack trace (details) is NOT present
        assertFalse("User message MUST NOT contain the stack trace (CWE-209)", userMessage.contains("java.lang.RuntimeException"))
        assertFalse("User message MUST NOT contain the stack trace (CWE-209)", userMessage.contains("VpnError.kt"))
    }

    @Test
    fun `fromException should redact stack traces from getUserMessage`() {
        val exceptionMessage = "Failed to connect to NordVPN server"
        val exception = RuntimeException(exceptionMessage)

        val error = VpnError.fromException(exception)
        val userMessage = error.getUserMessage()

        // Ensure the exception message is present
        assertTrue("User message should contain the exception message", userMessage.contains(exceptionMessage))

        // CRITICAL: Ensure the auto-generated stack trace is NOT present in the user-facing message
        assertFalse("User message MUST NOT contain the auto-generated stack trace", userMessage.contains("RuntimeException"))
        assertFalse("User message MUST NOT contain the auto-generated stack trace", userMessage.contains("VpnErrorSecurityTest"))
    }
}
