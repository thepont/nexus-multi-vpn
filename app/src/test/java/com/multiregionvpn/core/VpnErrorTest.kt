package com.multiregionvpn.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnErrorTest {

    @Test
    fun testGetUserMessageDoesNotLeakStackTraceOrDetails() {
        val sensitiveDetails = "java.lang.RuntimeException: Secret internal state or stacktrace line\n\tat com.multiregionvpn.InternalClass.method(InternalClass.kt:42)"
        val rawMessage = "Internal database connection failed with secret token xyz123"

        for (errorType in VpnError.ErrorType.values()) {
            val vpnError = VpnError(
                type = errorType,
                message = rawMessage,
                details = sensitiveDetails
            )

            val userMessage = vpnError.getUserMessage()

            // Verify internal details and stack trace are omitted from user-facing message
            assertFalse(
                "User message for $errorType leaked stacktrace/sensitive details",
                userMessage.contains("Secret internal state") || userMessage.contains("InternalClass.kt")
            )
            assertFalse(
                "User message for $errorType leaked raw internal message",
                userMessage.contains("secret token xyz123")
            )
        }
    }

    @Test
    fun testFromExceptionPreservesDetailsForDiagnostics() {
        val exception = RuntimeException("Connection timed out")
        val vpnError = VpnError.fromException(exception, tunnelId = "UK_tunnel")

        // Verify VpnError preserves internal diagnostics
        assertTrue(vpnError.message.contains("Connection timed out"))
        assertTrue(vpnError.details?.contains("RuntimeException") == true)

        // Verify user message remains clean and sanitized
        val userMessage = vpnError.getUserMessage()
        assertFalse(userMessage.contains("RuntimeException"))
        assertTrue(userMessage.contains("Could not connect to VPN server"))
    }
}
