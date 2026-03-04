package com.multiregionvpn.core

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VpnErrorSecurityTest {

    @Test
    fun `fromException should not include stack trace in details`() {
        val exception = RuntimeException("Sensitive error occurred")
        val vpnError = VpnError.fromException(exception)

        val details = vpnError.details ?: ""

        // Stack traces usually contain class names and line numbers like "at com.multiregionvpn..."
        assertFalse(details.contains("at "), "Error details should not contain stack trace markers ('at ')")
        assertFalse(details.contains("VpnErrorSecurityTest.kt"), "Error details should not contain file names from stack trace")
    }

    @Test
    fun `fromException should include message but not full trace`() {
        val message = "Authentication failed"
        val exception = RuntimeException(message)
        val vpnError = VpnError.fromException(exception)

        assertTrue(vpnError.message.contains(message), "Error message should be preserved")

        val details = vpnError.details ?: ""
        assertFalse(details.length > 500, "Error details are too long, likely contains a stack trace")
    }
}
