package com.multiregionvpn.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnErrorTest {

    @Test
    fun `getUserMessage should not contain details with stack trace`() {
        val message = "Auth failed"
        val stackTrace = "java.lang.Exception: Auth failed\n\tat com.multiregionvpn.core.VpnErrorTest.test(VpnErrorTest.kt:10)"

        val error = VpnError(
            type = VpnError.ErrorType.AUTHENTICATION_FAILED,
            message = message,
            details = stackTrace
        )

        val userMessage = error.getUserMessage()

        // Should contain the message
        assertTrue("User message should contain error message", userMessage.contains(message))

        // Should NOT contain the technical details (stack trace)
        assertFalse("User message should NOT contain stack trace details", userMessage.contains("java.lang.Exception"))
        assertFalse("User message should NOT contain line numbers from stack trace", userMessage.contains("VpnErrorTest.kt"))
    }

    @Test
    fun `getUserMessage for unknown error should not contain details`() {
        val message = "Something went wrong"
        val stackTrace = "Detailed stack trace here"

        val error = VpnError(
            type = VpnError.ErrorType.UNKNOWN,
            message = message,
            details = stackTrace
        )

        val userMessage = error.getUserMessage()

        assertTrue(userMessage.contains(message))
        assertFalse("User message for UNKNOWN should NOT contain details", userMessage.contains(stackTrace))
    }
}
