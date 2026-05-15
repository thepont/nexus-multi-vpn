package com.multiregionvpn.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnErrorTest {

    @Test
    fun `fromException should not leak stack trace in details`() {
        val exception = RuntimeException("Test exception")
        val error = VpnError.fromException(exception)

        // Before fix: This would contain the stack trace (e.g., "at com.multiregionvpn.core.VpnErrorTest...")
        // After fix: It should only contain the exception's toString() representation

        val details = error.details ?: ""

        // A stack trace typically contains "at " followed by package/class names
        // and multiple lines.
        val isStackTrace = details.contains("at ") && details.contains("\n")

        // This assertion will FAIL before the fix and PASS after the fix.
        // We expect it to FAIL now to confirm the vulnerability.
        assertFalse("Details should not be a stack trace: $details", isStackTrace)
    }

    @Test
    fun `getUserMessage should not contain stack trace info`() {
        val exception = RuntimeException("Sensitive internal error")
        val error = VpnError.fromException(exception)
        val userMessage = error.getUserMessage()

        assertFalse("User message should not leak stack trace", userMessage.contains("at com.multiregionvpn"))
    }
}
