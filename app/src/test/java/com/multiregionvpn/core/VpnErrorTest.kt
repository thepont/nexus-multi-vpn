package com.multiregionvpn.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnErrorTest {

    @Test
    fun `getUserMessage should not contain stack trace details`() {
        val stackTrace = "java.lang.RuntimeException: Secret info\n\tat com.example.Leak.do(Leak.kt:10)"
        val message = "Something went wrong"

        val error = VpnError(
            type = VpnError.ErrorType.UNKNOWN,
            message = message,
            details = stackTrace
        )

        val userMessage = error.getUserMessage()

        assertTrue("User message should contain the main message", userMessage.contains(message))
        assertFalse("User message should NOT contain the stack trace details", userMessage.contains("at com.example.Leak"))
    }

    @Test
    fun `fromException should populate details with stack trace`() {
        val exception = RuntimeException("Test exception")
        val error = VpnError.fromException(exception)

        assertTrue("Error details should contain the stack trace", error.details?.contains("RuntimeException") == true)
        assertTrue("Error details should contain the class name", error.details?.contains("VpnErrorTest") == true)
    }
}
