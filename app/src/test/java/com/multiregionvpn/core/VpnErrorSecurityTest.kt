package com.multiregionvpn.core

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VpnErrorSecurityTest {

    @Test
    fun `fromException should not leak stack trace in details`() {
        val exception = RuntimeException("Sensitive error message")
        val vpnError = VpnError.fromException(exception)

        val stackTraceIndicator = "VpnErrorSecurityTest"

        // This test is expected to FAIL before the fix
        assertFalse(
            vpnError.details?.contains(stackTraceIndicator) ?: false,
            "VpnError.details should NOT contain the stack trace"
        )
    }

    @Test
    fun `fromException should not leak stack trace in message`() {
        val exception = RuntimeException("Sensitive error message")
        val vpnError = VpnError.fromException(exception)

        val stackTraceIndicator = "VpnErrorSecurityTest"

        assertFalse(
            vpnError.message.contains(stackTraceIndicator),
            "VpnError.message should NOT contain the stack trace"
        )
    }
}
