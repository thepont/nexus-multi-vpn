package com.multiregionvpn.core

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VpnErrorSecurityTest {

    @Test
    fun `getUserMessage should not contain stack trace details`() {
        val exceptionMessage = "Connection timeout"
        val exception = RuntimeException(exceptionMessage)
        val vpnError = VpnError.fromException(exception)

        val userMessage = vpnError.getUserMessage()

        // The message should be present
        assertTrue(userMessage.contains(exceptionMessage), "User message should contain the exception message")

        // The stack trace details should NOT be present in the user message
        // Stack traces typically contain class names, method names, and line numbers
        assertFalse(userMessage.contains("RuntimeException"), "User message should not contain exception class name from stack trace")
        assertFalse(userMessage.contains("VpnErrorSecurityTest"), "User message should not contain test class name from stack trace")
        assertFalse(userMessage.contains(".kt:"), "User message should not contain file references from stack trace")

        // Also verify that the 'details' field itself still contains the stack trace for logging
        assertTrue(vpnError.details?.contains("VpnErrorSecurityTest") == true, "Internal details should still contain the stack trace")
    }

    @Test
    fun `getUserMessage should not leak sensitive info for all error types`() {
        VpnError.ErrorType.values().forEach { type ->
            val vpnError = VpnError(
                type = type,
                message = "Brief error",
                details = "Sensitive stack trace with SecretClass.java:123"
            )

            val userMessage = vpnError.getUserMessage()

            assertFalse(userMessage.contains("Sensitive stack trace"), "User message for $type should not contain sensitive details")
            assertFalse(userMessage.contains("SecretClass"), "User message for $type should not contain class names from details")
            assertTrue(userMessage.contains("Brief error"), "User message for $type should contain the brief message")
        }
    }
}
