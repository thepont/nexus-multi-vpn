package com.multiregionvpn.core

import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse

class VpnErrorSecurityTest {

    @Test
    fun `test getUserMessage does not contain stack trace from details`() {
        val exception = RuntimeException("Sensitive connection error")
        val vpnError = VpnError.fromException(exception)

        val userMessage = vpnError.getUserMessage()

        // Verify the message contains the main error message
        assertTrue("Message should contain error description", userMessage.contains("Sensitive connection error"))

        // This is what we want to PREVENT: details often contain the full stack trace
        // Currently, it includes it via ${details ?: message}
        // If it contains "at com.multiregionvpn", it's leaking stack trace info
        assertFalse("User message should NOT contain stack trace details (CWE-209)",
            userMessage.contains("at com.multiregionvpn.core.VpnErrorSecurityTest"))
    }

    @Test
    fun `test getUserMessage with explicit details redaction`() {
        val vpnError = VpnError(
            type = VpnError.ErrorType.AUTHENTICATION_FAILED,
            message = "Invalid credentials",
            details = "java.lang.RuntimeException: Secret stack trace\n at com.multiregionvpn.Internal(Native Method)"
        )

        val userMessage = vpnError.getUserMessage()

        assertTrue(userMessage.contains("Invalid credentials"))
        assertFalse("Should redact explicit details containing stack trace",
            userMessage.contains("Secret stack trace"))
    }
}
