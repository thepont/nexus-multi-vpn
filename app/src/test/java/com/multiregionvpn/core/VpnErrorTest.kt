package com.multiregionvpn.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class VpnErrorTest {

    @Test
    fun `fromException should not include stack trace in details`() {
        val exception = RuntimeException("Test message")
        val tunnelId = "test_tunnel"

        val error = VpnError.fromException(exception, tunnelId)

        assertEquals("Test message", error.message)
        assertEquals("Test message", error.details)
        // Ensure details is NOT the stack trace
        assertNotEquals(exception.stackTraceToString(), error.details)
    }

    @Test
    fun `fromException with null message should use default details`() {
        val exception = RuntimeException()

        val error = VpnError.fromException(exception)

        assertEquals("Unknown error", error.message)
        assertEquals("No error message", error.details)
    }
}
