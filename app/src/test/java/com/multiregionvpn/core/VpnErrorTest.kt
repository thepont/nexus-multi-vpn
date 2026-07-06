package com.multiregionvpn.core

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VpnErrorTest {

    @Test
    fun testGetUserMessage_ExcludesDetails() {
        val message = "Authentication failed"
        val details = "java.lang.RuntimeException: Stack trace that might contain sensitive info"
        val error = VpnError(
            type = VpnError.ErrorType.AUTHENTICATION_FAILED,
            message = message,
            details = details
        )

        val userMessage = error.getUserMessage()

        // Ensure message is present
        assertTrue(userMessage.contains(message), "User message should contain the high-level message")

        // 🛡️ Sentinel: Ensure details (stack trace) are NOT leaked
        assertFalse(userMessage.contains("RuntimeException"), "User message should NOT leak technical details")
        assertFalse(userMessage.contains(details), "User message should NOT leak stack traces")
    }

    @Test
    fun testFromException_PopulatesDetails() {
        val message = "Network timeout"
        val exception = RuntimeException(message)

        val error = VpnError.fromException(exception)

        assertEquals(message, error.message)
        assertTrue(error.details?.contains("RuntimeException") == true, "Internal details should contain stack trace")

        // Ensure user message still doesn't leak it
        assertFalse(error.getUserMessage().contains("RuntimeException"))
    }
}
