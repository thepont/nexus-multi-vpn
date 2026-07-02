package com.multiregionvpn.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnErrorTest {

    @Test
    fun `getUserMessage should not contain details with stack trace`() {
        val message = "Simple error"
        val details = "java.lang.RuntimeException: detailed stack trace\n at com.multiregionvpn.VpnEngineService.start(VpnEngineService.kt:100)"

        val vpnError = VpnError(
            type = VpnError.ErrorType.CONNECTION_FAILED,
            message = message,
            details = details
        )

        val userMessage = vpnError.getUserMessage()

        // Verify user message contains the high level message
        assertTrue("User message should contain the main error message", userMessage.contains(message))

        // Verify user message does NOT contain the sensitive details
        assertFalse("User message should NOT contain sensitive stack trace details", userMessage.contains("RuntimeException"))
        assertFalse("User message should NOT contain sensitive file names or line numbers", userMessage.contains("VpnEngineService.kt"))
    }

    @Test
    fun `fromException should populate details but getUserMessage should hide them`() {
        val exception = RuntimeException("Auth failed")
        val vpnError = VpnError.fromException(exception)

        // Ensure details are actually populated in the data class (for internal logging)
        assertTrue("Details should be populated from exception stack trace", vpnError.details?.contains("RuntimeException") == true)

        val userMessage = vpnError.getUserMessage()

        // Ensure the user message is safe
        assertFalse("User message should hide the stack trace from the exception", userMessage.contains("RuntimeException"))
    }
}
