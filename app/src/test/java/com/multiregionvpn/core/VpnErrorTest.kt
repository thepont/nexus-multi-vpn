package com.multiregionvpn.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnErrorTest {

    @Test
    fun `getUserMessage should not contain stack trace from details`() {
        val stackTrace = "java.lang.Exception: Something went wrong\n\tat com.multiregionvpn.core.VpnErrorTest.test(VpnErrorTest.kt:10)"
        val message = "Auth failed"
        val error = VpnError(
            type = VpnError.ErrorType.AUTHENTICATION_FAILED,
            message = message,
            details = stackTrace
        )

        val userMessage = error.getUserMessage()

        assertTrue("User message should contain the error message", userMessage.contains(message))
        assertFalse("User message should NOT contain the stack trace details", userMessage.contains("VpnErrorTest.kt"))
        assertFalse("User message should NOT contain 'at com.multiregionvpn'", userMessage.contains("at com.multiregionvpn"))
    }

    @Test
    fun `fromException should capture stack trace in details but keep it out of getUserMessage`() {
        val exception = RuntimeException("Connection timeout")
        val error = VpnError.fromException(exception)

        assertTrue("Details should contain stack trace", error.details?.contains("RuntimeException") == true)

        val userMessage = error.getUserMessage()
        assertFalse("User message should NOT contain the stack trace", userMessage.contains("java.lang.RuntimeException"))
        assertTrue("User message should contain the exception message", userMessage.contains("Connection timeout"))
    }
}
