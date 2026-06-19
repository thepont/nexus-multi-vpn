package com.multiregionvpn.core

import org.junit.Test
import org.junit.Assert.*

/**
 * Verifies that VpnError does not leak stack traces in user-facing messages.
 */
class VpnErrorLeakTest {

    @Test
    fun testUserMessageDoesNotContainStackTrace() {
        val stackTrace = "java.lang.RuntimeException: Secret stack trace\n" +
                "\tat com.multiregionvpn.SecretClass.secretMethod(SecretClass.kt:123)\n" +
                "\tat com.multiregionvpn.InternalService.doStuff(InternalService.kt:456)"

        val error = VpnError(
            type = VpnError.ErrorType.AUTHENTICATION_FAILED,
            message = "Authentication failed",
            details = stackTrace
        )

        val userMessage = error.getUserMessage()

        // The user message should contain the basic message
        assertTrue("User message should contain the basic error message",
            userMessage.contains("Authentication failed"))

        // The user message should NOT contain the stack trace or internal class names
        assertFalse("User message should NOT contain stack trace details",
            userMessage.contains("SecretClass"))
        assertFalse("User message should NOT contain stack trace line numbers",
            userMessage.contains("SecretClass.kt:123"))
        assertFalse("User message should NOT contain 'at ' stack trace prefix",
            userMessage.contains("\tat "))
    }

    @Test
    fun testFromExceptionRedactsStackTraceInUserMessage() {
        val exception = RuntimeException("Something went wrong")
        val error = VpnError.fromException(exception)

        val userMessage = error.getUserMessage()

        // Verify stack trace is in details but NOT in user message
        assertNotNull("Details should contain stack trace", error.details)
        assertTrue("Details should contain class name", error.details!!.contains("RuntimeException"))

        assertFalse("User message should NOT contain class name from stack trace",
            userMessage.contains("RuntimeException"))
    }
}
