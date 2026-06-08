package com.multiregionvpn.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnErrorSecurityTest {

    @Test
    fun `getUserMessage should not contain stack trace details`() {
        val exception = RuntimeException("Secret connection error")
        val vpnError = VpnError.fromException(exception)

        val userMessage = vpnError.getUserMessage()

        // The message itself might be in the user message
        assertTrue("User message should contain the error message", userMessage.contains("Secret connection error"))

        // But it should NOT contain the stack trace
        // Stack traces usually contain "at com.multiregionvpn..." or "at java.lang..."
        assertFalse("User message should not contain stack trace details", userMessage.contains("at com.multiregionvpn"))
        assertFalse("User message should not contain stack trace details", userMessage.contains("at java.lang"))

        // Verify specifically for each error type if needed, but fromException covers most
    }

    @Test
    fun `AUTHENTICATION_FAILED user message should not contain details`() {
        val vpnError = VpnError(
            type = VpnError.ErrorType.AUTHENTICATION_FAILED,
            message = "Invalid credentials",
            details = "Stack trace: at com.multiregionvpn.InternalClass.doSomething(InternalClass.kt:123)"
        )

        val userMessage = vpnError.getUserMessage()

        assertTrue(userMessage.contains("Invalid credentials"))
        assertFalse(userMessage.contains("InternalClass.kt"))
        assertFalse(userMessage.contains("Stack trace"))
    }
}
