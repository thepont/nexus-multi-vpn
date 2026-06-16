package com.multiregionvpn.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnErrorSecurityTest {

    @Test
    fun `getUserMessage should not contain stack trace details`() {
        val stackTraceContent = "at com.multiregionvpn.core.NativeOpenVpnClient.connect(NativeOpenVpnClient.kt:123)"
        val errorMessage = "Connection failed"

        val vpnError = VpnError(
            type = VpnError.ErrorType.CONNECTION_FAILED,
            message = errorMessage,
            details = stackTraceContent
        )

        val userMessage = vpnError.getUserMessage()

        assertTrue("User message should contain the main error message", userMessage.contains(errorMessage))
        assertFalse("User message should NOT contain stack trace details", userMessage.contains(stackTraceContent))
    }

    @Test
    fun `fromException should redact details from user message`() {
        val exception = RuntimeException("Auth failed")
        val vpnError = VpnError.fromException(exception)

        val userMessage = vpnError.getUserMessage()

        assertTrue("User message should contain the exception message", userMessage.contains("Auth failed"))
        // Details field itself should still exist for logging/debugging (internal),
        // but it must not be in the user-visible message
        assertTrue("Internal details should still capture stack trace", vpnError.details?.contains("RuntimeException") == true)
        assertFalse("User message should not leak internal stack trace", userMessage.contains("RuntimeException"))
    }
}
