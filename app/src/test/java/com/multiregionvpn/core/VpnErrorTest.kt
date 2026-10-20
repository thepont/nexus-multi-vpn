package com.multiregionvpn.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnErrorTest {

    @Test
    fun `fromException creates VpnError without exposing stack trace details`() {
        val cause = RuntimeException("Connection timeout occurred at com.multiregionvpn.InternalClass.connect(InternalClass.kt:42)")
        val error = VpnError.fromException(cause, tunnelId = "tunnel-1")

        assertEquals(VpnError.ErrorType.CONNECTION_FAILED, error.type)
        assertEquals("Connection timeout occurred at com.multiregionvpn.InternalClass.connect(InternalClass.kt:42)", error.message)
        assertEquals("RuntimeException", error.details)
        assertEquals("tunnel-1", error.tunnelId)

        val userMsg = error.getUserMessage()
        assertFalse("User message should not leak full stack traces", userMsg.contains("at com.multiregionvpn"))
        assertTrue("User message should contain helpful guidance", userMsg.contains("Could not connect to VPN server"))
    }

    @Test
    fun `fromException maps authentication exceptions correctly`() {
        val cause = IllegalArgumentException("Invalid password credentials")
        val error = VpnError.fromException(cause)

        assertEquals(VpnError.ErrorType.AUTHENTICATION_FAILED, error.type)
        assertEquals("IllegalArgumentException", error.details)
        assertTrue(error.getUserMessage().contains("Authentication failed"))
    }
}
