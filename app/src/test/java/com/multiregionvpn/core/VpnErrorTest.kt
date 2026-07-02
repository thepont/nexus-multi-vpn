package com.multiregionvpn.core

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VpnErrorTest {

    @Test
    fun `getUserMessage should not contain stack trace details`() {
        val stackTrace = "java.lang.RuntimeException: Boom\n\tat com.foo.Bar.baz(Bar.kt:123)"
        val error = VpnError(
            type = VpnError.ErrorType.AUTHENTICATION_FAILED,
            message = "Invalid credentials",
            details = stackTrace
        )

        val userMessage = error.getUserMessage()

        // Should contain the message
        assertTrue(userMessage.contains("Invalid credentials"), "User message should contain the error message")

        // Should NOT contain the stack trace details
        assertFalse(userMessage.contains("com.foo.Bar"), "User message should NOT contain stack trace details")
        assertFalse(userMessage.contains("Bar.kt:123"), "User message should NOT contain line numbers from stack trace")
    }
}
