package com.multiregionvpn.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Security test for VpnError to ensure it doesn't leak sensitive information (CWE-209).
 */
class VpnErrorSecurityTest {

    @Test
    fun `getUserMessage should not contain stack trace details`() {
        val exception = RuntimeException("Test exception message")
        val vpnError = VpnError.fromException(exception)

        val userMessage = vpnError.getUserMessage()

        // The user message should contain the exception message
        assertTrue("User message should contain the error message",
            userMessage.contains("Test exception message"))

        // The user message should NOT contain stack trace elements
        assertFalse("User message should NOT contain stack trace (CWE-209)",
            userMessage.contains("at com.multiregionvpn.core.VpnErrorSecurityTest"))
        assertFalse("User message should NOT contain stack trace indicators",
            userMessage.contains("StackTrace"))
    }

    @Test
    fun `getUserMessage should handle all error types without leaking details`() {
        VpnError.ErrorType.values().forEach { type ->
            val vpnError = VpnError(
                type = type,
                message = "Brief error message",
                details = "Sensitive stack trace information: at some.internal.Class.method(Class.kt:123)"
            )

            val userMessage = vpnError.getUserMessage()

            assertFalse("User message for $type should NOT contain sensitive details",
                userMessage.contains("Sensitive stack trace information"))
            assertTrue("User message for $type should contain the brief message",
                userMessage.contains("Brief error message"))
        }
    }
}
