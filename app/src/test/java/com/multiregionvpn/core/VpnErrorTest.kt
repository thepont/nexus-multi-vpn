package com.multiregionvpn.core

import kotlin.test.*
import org.junit.Test

class VpnErrorTest {

    @Test
    fun `getUserMessage for AUTHENTICATION_FAILED should contain NordVPN instructions`() {
        val error = VpnError(
            type = VpnError.ErrorType.AUTHENTICATION_FAILED,
            message = "Auth failed",
            details = "Detailed auth error"
        )
        val userMessage = error.getUserMessage()

        assertTrue(userMessage.contains("Authentication failed"))
        assertTrue(userMessage.contains("NordVPN credentials"))
        assertTrue(userMessage.contains("https://my.nordaccount.com/dashboard/nordvpn/manual-setup/"))
        assertTrue(userMessage.contains("Detailed auth error"))
    }

    @Test
    fun `getUserMessage for CONNECTION_FAILED should contain connection instructions`() {
        val error = VpnError(
            type = VpnError.ErrorType.CONNECTION_FAILED,
            message = "Connection timeout",
            details = "Server unreachable"
        )
        val userMessage = error.getUserMessage()

        assertTrue(userMessage.contains("Could not connect to VPN server"))
        assertTrue(userMessage.contains("internet connection"))
        assertTrue(userMessage.contains("Server unreachable"))
    }

    @Test
    fun `getUserMessage for CONFIG_ERROR should contain config instructions`() {
        val error = VpnError(
            type = VpnError.ErrorType.CONFIG_ERROR,
            message = "Invalid config",
            details = "Parse error"
        )
        val userMessage = error.getUserMessage()

        assertTrue(userMessage.contains("Invalid VPN configuration"))
        assertTrue(userMessage.contains("outdated"))
        assertTrue(userMessage.contains("Parse error"))
    }

    @Test
    fun `getUserMessage for INTERFACE_ERROR should contain interface instructions`() {
        val error = VpnError(
            type = VpnError.ErrorType.INTERFACE_ERROR,
            message = "Interface error",
            details = "Permission denied"
        )
        val userMessage = error.getUserMessage()

        assertTrue(userMessage.contains("VPN interface error"))
        assertTrue(userMessage.contains("permission"))
        assertTrue(userMessage.contains("Permission denied"))
    }

    @Test
    fun `getUserMessage for TUNNEL_ERROR should contain tunnel instructions`() {
        val error = VpnError(
            type = VpnError.ErrorType.TUNNEL_ERROR,
            message = "Tunnel failed",
            details = "Handshake failed"
        )
        val userMessage = error.getUserMessage()

        assertTrue(userMessage.contains("Tunnel creation failed"))
        assertTrue(userMessage.contains("reachable"))
        assertTrue(userMessage.contains("Handshake failed"))
    }

    @Test
    fun `getUserMessage for UNKNOWN should contain unexpected error message`() {
        val error = VpnError(
            type = VpnError.ErrorType.UNKNOWN,
            message = "Something went wrong",
            details = "Strange error"
        )
        val userMessage = error.getUserMessage()

        assertTrue(userMessage.contains("An unexpected error occurred"))
        assertTrue(userMessage.contains("Strange error"))
    }

    @Test
    fun `getUserMessage should prefer details over message`() {
        val errorWithDetails = VpnError(
            type = VpnError.ErrorType.UNKNOWN,
            message = "Short message",
            details = "Very long and detailed explanation"
        )
        assertTrue(errorWithDetails.getUserMessage().contains("Very long and detailed explanation"))
        assertFalse(errorWithDetails.getUserMessage().endsWith("Short message"))

        val errorWithoutDetails = VpnError(
            type = VpnError.ErrorType.UNKNOWN,
            message = "Short message",
            details = null
        )
        assertTrue(errorWithoutDetails.getUserMessage().contains("Short message"))
    }

    @Test
    fun `fromException should map authentication keywords to AUTHENTICATION_FAILED`() {
        val keywords = listOf("auth", "credential", "password", "username", "invalid")
        keywords.forEach { keyword ->
            val exception = Exception("Error with $keyword")
            val vpnError = VpnError.fromException(exception)
            assertEquals(VpnError.ErrorType.AUTHENTICATION_FAILED, vpnError.type, "Failed for keyword: $keyword")
        }
    }

    @Test
    fun `fromException should map connection keywords to CONNECTION_FAILED`() {
        val keywords = listOf("connection", "timeout", "unreachable")
        keywords.forEach { keyword ->
            val exception = Exception("Error with $keyword")
            val vpnError = VpnError.fromException(exception)
            assertEquals(VpnError.ErrorType.CONNECTION_FAILED, vpnError.type, "Failed for keyword: $keyword")
        }
    }

    @Test
    fun `fromException should map config keywords to CONFIG_ERROR`() {
        val keywords = listOf("config", "parse")
        keywords.forEach { keyword ->
            val exception = Exception("Error with $keyword")
            val vpnError = VpnError.fromException(exception)
            assertEquals(VpnError.ErrorType.CONFIG_ERROR, vpnError.type, "Failed for keyword: $keyword")
        }
    }

    @Test
    fun `fromException should map interface keywords to INTERFACE_ERROR`() {
        val keywords = listOf("interface", "permission", "vpn")
        keywords.forEach { keyword ->
            val exception = Exception("Error with $keyword")
            val vpnError = VpnError.fromException(exception)
            assertEquals(VpnError.ErrorType.INTERFACE_ERROR, vpnError.type, "Failed for keyword: $keyword")
        }
    }

    @Test
    fun `fromException should map unknown errors to UNKNOWN`() {
        val exception = Exception("Something else entirely")
        val vpnError = VpnError.fromException(exception)
        assertEquals(VpnError.ErrorType.UNKNOWN, vpnError.type)
    }

    @Test
    fun `fromException should preserve tunnelId`() {
        val tunnelId = "test-tunnel-123"
        val exception = Exception("any error")
        val vpnError = VpnError.fromException(exception, tunnelId)
        assertEquals(tunnelId, vpnError.tunnelId)
    }

    @Test
    fun `fromException should capture message and stack trace`() {
        val message = "test message"
        val exception = Exception(message)
        val vpnError = VpnError.fromException(exception)

        assertEquals(message, vpnError.message)
        assertNotNull(vpnError.details)
        assertTrue(vpnError.details!!.contains("java.lang.Exception: test message"))
    }
}
