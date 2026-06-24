package com.multiregionvpn.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnErrorTest {

    @Test
    fun `getUserMessage should not contain stack trace when created from exception`() {
        // Arrange
        val exceptionMessage = "Test exception message"
        val exception = RuntimeException(exceptionMessage)
        val vpnError = VpnError.fromException(exception)

        // Act
        val userMessage = vpnError.getUserMessage()

        // Assert
        assertTrue("User message should contain the original error message", userMessage.contains(exceptionMessage))
        assertFalse("User message should NOT contain stack trace information", userMessage.contains("RuntimeException"))
        assertFalse("User message should NOT contain stack trace information (at ...)", userMessage.contains("at "))

        // Verify details still contains the stack trace (for logging/debugging)
        assertTrue("Details should still contain the stack trace for internal logging", vpnError.details?.contains("RuntimeException") == true)
    }

    @Test
    fun `getUserMessage should work for all error types without leaking details`() {
        VpnError.ErrorType.values().forEach { type ->
            val vpnError = VpnError(
                type = type,
                message = "Plain message",
                details = "Secret stack trace at com.example.Leak"
            )

            val userMessage = vpnError.getUserMessage()

            assertFalse("User message for $type should not contain details", userMessage.contains("Secret stack trace"))
            assertTrue("User message for $type should contain the message", userMessage.contains("Plain message"))
        }
    }
}
