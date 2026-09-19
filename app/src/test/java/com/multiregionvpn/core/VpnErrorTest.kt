package com.multiregionvpn.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class VpnErrorTest {

    @Test
    fun `fromException creates VpnError without stacktrace in details`() {
        val exception = RuntimeException("Connection timeout occurred")
        val error = VpnError.fromException(exception, tunnelId = "tunnel-1")

        assertEquals(VpnError.ErrorType.CONNECTION_FAILED, error.type)
        assertEquals("Connection timeout occurred", error.message)
        assertEquals("RuntimeException", error.details)
        assertEquals("tunnel-1", error.tunnelId)

        // Ensure stack trace string is not in details or message
        assertFalse(error.details?.contains("at com.multiregionvpn") ?: false)
    }

    @Test
    fun `getUserMessage returns safe error message without leaking details`() {
        val exception = IllegalStateException("Internal state failure with secret tokens")
        val error = VpnError.fromException(exception)

        val userMessage = error.getUserMessage()
        assertNotNull(userMessage)
        assertFalse(userMessage.contains("secret tokens"))
        assertFalse(userMessage.contains("IllegalStateException"))
        assertEquals("An unexpected error occurred. Please try again later.", userMessage)
    }
}
