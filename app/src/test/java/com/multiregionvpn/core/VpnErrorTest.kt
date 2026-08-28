package com.multiregionvpn.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnErrorTest {

    @Test
    fun testGetUserMessage_doesNotExposeDetailsOrStackTrace() {
        val sensitiveDetails = "java.lang.RuntimeException: Sensitive internal error\n\tat com.multiregionvpn.InternalClass.secretMethod(InternalClass.kt:42)"
        val sensitiveMessage = "Internal database query failed"

        val error = VpnError(
            type = VpnError.ErrorType.AUTHENTICATION_FAILED,
            message = sensitiveMessage,
            details = sensitiveDetails,
            tunnelId = "tunnel_1"
        )

        val userMessage = error.getUserMessage()

        // Verify user-friendly content is present
        assertTrue(userMessage.contains("Authentication failed"))

        // Security check (CWE-209): ensure internal details and stack traces are excluded
        assertFalse(userMessage.contains(sensitiveDetails))
        assertFalse(userMessage.contains(sensitiveMessage))
        assertFalse(userMessage.contains("secretMethod"))
    }

    @Test
    fun testFromException_createsErrorWithDetails() {
        val exception = RuntimeException("Authentication failed due to invalid token")
        val vpnError = VpnError.fromException(exception, tunnelId = "uk_tunnel")

        assertTrue(vpnError.type == VpnError.ErrorType.AUTHENTICATION_FAILED)
        val userMessage = vpnError.getUserMessage()
        assertFalse(userMessage.contains("invalid token"))
        assertFalse(userMessage.contains("RuntimeException"))
    }
}
