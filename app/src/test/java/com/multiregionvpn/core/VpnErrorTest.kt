package com.multiregionvpn.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnErrorTest {

    @Test
    fun testFromException_sanitizesDetailsAndDoesNotContainStackTrace() {
        val exception = RuntimeException("Connection timeout occurred")
        val vpnError = VpnError.fromException(exception, tunnelId = "uk_tunnel")

        assertEquals(VpnError.ErrorType.CONNECTION_FAILED, vpnError.type)
        assertEquals("Connection timeout occurred", vpnError.message)
        assertEquals("RuntimeException", vpnError.details)
        assertEquals("uk_tunnel", vpnError.tunnelId)

        // Ensure details do not leak stack traces
        assertFalse(vpnError.details!!.contains("at com.multiregionvpn"))
    }

    @Test
    fun testGetUserMessage_doesNotLeakStackTrace() {
        val vpnError = VpnError(
            type = VpnError.ErrorType.AUTHENTICATION_FAILED,
            message = "Invalid credentials provided",
            details = "java.lang.IllegalArgumentException\n\tat com.multiregionvpn.SecretClass.method(SecretClass.kt:42)"
        )

        val userMessage = vpnError.getUserMessage()

        assertTrue(userMessage.contains("Invalid credentials provided"))
        assertFalse(userMessage.contains("SecretClass"))
        assertFalse(userMessage.contains("at com.multiregionvpn"))
    }
}
