package com.multiregionvpn.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnErrorSecurityTest {

    @Test
    fun `getUserMessage should not contain stack trace details`() {
        // Create an exception with a stack trace
        val exception = RuntimeException("Sensitive internal error")
        val vpnError = VpnError.fromException(exception)

        // The details should contain the stack trace
        assertTrue("Details should contain stack trace", vpnError.details?.contains("RuntimeException") == true)
        assertTrue("Details should contain the error message", vpnError.details?.contains("Sensitive internal error") == true)

        // The user message should NOT contain the stack trace details (CWE-209)
        val userMessage = vpnError.getUserMessage()

        // This is expected to PASS after the fix
        assertFalse(
            "User message should not leak stack trace details (CWE-209). Message: $userMessage",
            userMessage.contains("at com.multiregionvpn") || userMessage.contains("RuntimeException")
        )
    }
}
