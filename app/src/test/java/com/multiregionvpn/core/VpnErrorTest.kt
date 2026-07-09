package com.multiregionvpn.core

import org.junit.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class VpnErrorTest {

    @Test
    fun `getUserMessage should not contain stack trace details`() {
        // GIVEN: A VpnError created from an exception
        val exception = RuntimeException("Test exception message")
        val vpnError = VpnError.fromException(exception)

        // WHEN: Getting the user-friendly message
        val userMessage = vpnError.getUserMessage()

        // THEN: The message should contain the exception message
        assertTrue(userMessage.contains("Test exception message"), "Message should contain the error message")

        // AND: The message should NOT contain stack trace elements
        // (Current implementation DOES contain them, so this test will fail initially if I assert they are NOT there)
        // I will write the test to FAIL first to confirm the vulnerability

        val containsStackTrace = userMessage.contains("at ") ||
                                userMessage.contains(".kt:") ||
                                userMessage.contains(".java:")

        // This is the assertion that will FAIL before the fix
        assertFalse(containsStackTrace, "User message should NOT contain stack trace details (leakage risk)")
    }
}
