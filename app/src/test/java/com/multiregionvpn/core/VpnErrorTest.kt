package com.multiregionvpn.core

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class VpnErrorTest {

    @Test
    fun `fromException should not include stack trace in details`() {
        val exceptionMessage = "Test exception message"
        val exception = RuntimeException(exceptionMessage)

        val vpnError = VpnError.fromException(exception)

        // Ensure details is either null or the message, but NOT the full stack trace
        if (vpnError.details != null) {
            assertEquals(exceptionMessage, vpnError.details)
            assertFalse(vpnError.details!!.contains("java.lang.RuntimeException"))
            assertFalse(vpnError.details!!.contains("at com.multiregionvpn.core.VpnErrorTest"))
        }
    }

    @Test
    fun `fromException should map authentication errors correctly`() {
        val exception = RuntimeException("Authentication failed: invalid credentials")
        val vpnError = VpnError.fromException(exception)

        assertEquals(VpnError.ErrorType.AUTHENTICATION_FAILED, vpnError.type)
        assertEquals("Authentication failed: invalid credentials", vpnError.message)
    }

    @Test
    fun `fromException should map connection errors correctly`() {
        val exception = RuntimeException("Connection timed out")
        val vpnError = VpnError.fromException(exception)

        assertEquals(VpnError.ErrorType.CONNECTION_FAILED, vpnError.type)
    }

    @Test
    fun `fromException should map configuration errors correctly`() {
        val exception = RuntimeException("Failed to parse config")
        val vpnError = VpnError.fromException(exception)

        assertEquals(VpnError.ErrorType.CONFIG_ERROR, vpnError.type)
    }

    @Test
    fun `fromException should map interface errors correctly`() {
        val exception = RuntimeException("VPN permission denied")
        val vpnError = VpnError.fromException(exception)

        assertEquals(VpnError.ErrorType.INTERFACE_ERROR, vpnError.type)
    }

    @Test
    fun `fromException should map unknown errors to UNKNOWN type`() {
        val exception = RuntimeException("Something went wrong")
        val vpnError = VpnError.fromException(exception)

        assertEquals(VpnError.ErrorType.UNKNOWN, vpnError.type)
    }

    @Test
    fun `getUserMessage should return formatted message with details`() {
        val vpnError = VpnError(
            type = VpnError.ErrorType.AUTHENTICATION_FAILED,
            message = "Login failed",
            details = "Invalid username"
        )

        val userMessage = vpnError.getUserMessage()

        assertTrue(userMessage.contains("Authentication failed"))
        assertTrue(userMessage.contains("Invalid username"))
    }
}
