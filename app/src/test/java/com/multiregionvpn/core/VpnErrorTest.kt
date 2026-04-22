package com.multiregionvpn.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VpnErrorTest {

    @Test
    fun `fromException should not include stack trace in details`() {
        val exception = Exception("sensitive information in stack trace")
        val vpnError = VpnError.fromException(exception)

        assertEquals("sensitive information in stack trace", vpnError.message)
        assertEquals("sensitive information in stack trace", vpnError.details)

        // Verify details is not a full stack trace
        assertFalse(vpnError.details?.contains("at com.multiregionvpn") ?: false)
    }

    @Test
    fun `fromException should handle null message`() {
        val exception = Exception()
        val vpnError = VpnError.fromException(exception)

        assertEquals("Unknown error", vpnError.message)
        assertEquals(null, vpnError.details)
    }
}
