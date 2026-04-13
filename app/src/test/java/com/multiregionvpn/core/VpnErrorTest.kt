package com.multiregionvpn.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class VpnErrorTest {

    @Test
    fun `fromException sanitizes details by removing stack trace`() {
        val exception = RuntimeException("Sensitive error")
        val vpnError = VpnError.fromException(exception)

        // details should NOT contain the stack trace
        // In our implementation, details = e.cause?.message ?: errorMsg
        // Since cause is null, details should be "Sensitive error"
        assertEquals("Sensitive error", vpnError.details)

        val stackTrace = exception.stackTraceToString()
        assertNotEquals(stackTrace, vpnError.details)
    }

    @Test
    fun `fromException uses cause message for details if available`() {
        val cause = RuntimeException("Root cause")
        val exception = RuntimeException("Wrapper error", cause)
        val vpnError = VpnError.fromException(exception)

        assertEquals("Root cause", vpnError.details)
        assertNotEquals(exception.stackTraceToString(), vpnError.details)
    }
}
