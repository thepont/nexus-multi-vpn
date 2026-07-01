package com.multiregionvpn.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnErrorTest {

    @Test
    fun `getUserMessage should not contain stack trace details`() {
        val stackTrace = "java.lang.RuntimeException: Something went wrong\n" +
                "\tat com.multiregionvpn.VpnEngineService.start(VpnEngineService.kt:123)"

        val error = VpnError(
            type = VpnError.ErrorType.UNKNOWN,
            message = "Test error message",
            details = stackTrace
        )

        val userMessage = error.getUserMessage()

        // Ensure the message is present but the stack trace is NOT
        assertTrue("User message should contain the primary message", userMessage.contains("Test error message"))
        assertFalse("User message should NOT contain stack trace details", userMessage.contains("at com.multiregionvpn"))
        assertFalse("User message should NOT contain stack trace details", userMessage.contains("RuntimeException"))
    }

    @Test
    fun `fromException should capture stack trace in details but not in user message`() {
        val exception = RuntimeException("Sensitive internal error")
        val error = VpnError.fromException(exception)

        val userMessage = error.getUserMessage()

        assertTrue("User message should contain the exception message", userMessage.contains("Sensitive internal error"))
        assertFalse("User message should NOT contain stack trace details", userMessage.contains("at com.multiregionvpn"))
    }
}
