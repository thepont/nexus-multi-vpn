package com.multiregionvpn.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnErrorTest {

    @Test
    fun `fromException does not leak stack trace`() {
        val exception = RuntimeException("Authentication failed")
        val vpnError = VpnError.fromException(exception)

        // Verify secure behavior: details should be e.toString(), not full stack trace
        assertFalse("Details should NOT contain full stack trace",
            vpnError.details?.contains("\tat ") == true)

        val userMessage = vpnError.getUserMessage()
        assertFalse("User message should NOT contain stack trace",
            userMessage.contains("VpnErrorTest"))
        assertTrue("User message should contain the exception message",
            userMessage.contains("Authentication failed"))
    }

    @Test
    fun `fromException categorizes authentication errors correctly`() {
        val authException = RuntimeException("Invalid credentials for nordvpn")
        val vpnError = VpnError.fromException(authException)

        assertTrue(vpnError.type == VpnError.ErrorType.AUTHENTICATION_FAILED)
        assertTrue(vpnError.getUserMessage().contains("NordVPN credentials"))
    }

    @Test
    fun `fromException categorizes connection errors correctly`() {
        val connException = RuntimeException("Connection timeout")
        val vpnError = VpnError.fromException(connException)

        assertTrue(vpnError.type == VpnError.ErrorType.CONNECTION_FAILED)
        assertTrue(vpnError.getUserMessage().contains("internet connection"))
    }
}
