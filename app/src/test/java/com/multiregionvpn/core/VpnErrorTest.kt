package com.multiregionvpn.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnErrorTest {

    @Test
    fun `getUserMessage should not contain sensitive details from stack trace`() {
        val stackTrace = "java.lang.RuntimeException: sensitive info\n\tat com.example.App.crash(App.kt:10)"
        val error = VpnError(
            type = VpnError.ErrorType.UNKNOWN,
            message = "Something went wrong",
            details = stackTrace
        )

        val userMessage = error.getUserMessage()

        // Assert that the message is present but the sensitive stack trace is not
        assertTrue("User message should contain the main message", userMessage.contains("Something went wrong"))
        assertFalse("User message should NOT contain stack trace details", userMessage.contains("at com.example.App"))
    }

    @Test
    fun `fromException should capture stack trace in details but not in message`() {
        val exception = RuntimeException("Secret error")
        val error = VpnError.fromException(exception)

        assertTrue("Details should contain stack trace", error.details?.contains("RuntimeException") == true)
        val userMessage = error.getUserMessage()
        assertFalse("User message should not leak stack trace from fromException", userMessage.contains("VpnErrorTest.kt"))
    }
}
