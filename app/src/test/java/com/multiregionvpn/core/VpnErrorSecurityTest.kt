package com.multiregionvpn.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnErrorSecurityTest {

    @Test
    fun `fromException should not leak full stack trace in details`() {
        val exception = RuntimeException("Sensitive connection error")
        val vpnError = VpnError.fromException(exception)

        val details = vpnError.details ?: ""

        // Before fix: details contains full stack trace with "at com.multiregionvpn..."
        // After fix: details should only contain the exception class name or a generic message

        assertFalse("VpnError details should not contain full stack trace markers",
            details.contains("at ") && details.contains("VpnErrorSecurityTest"))

        assertTrue("VpnError details should contain exception type for debugging",
            details.contains("RuntimeException"))
    }

    @Test
    fun `getUserMessage should provide helpful but safe information`() {
        val vpnError = VpnError(
            type = VpnError.ErrorType.AUTHENTICATION_FAILED,
            message = "auth failed",
            details = "RuntimeException"
        )

        val userMessage = vpnError.getUserMessage()

        assertTrue("User message should contain the details", userMessage.contains("RuntimeException"))
        assertFalse("User message should not contain internal stack traces", userMessage.contains("at com.multiregionvpn"))
    }
}
