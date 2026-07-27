package com.multiregionvpn.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VpnErrorTest {

    @Test
    fun `getUserMessage should return user friendly message without raw stack traces`() {
        val originalMessage = "Authentication failed - bad password"
        val exception = RuntimeException(originalMessage)

        val error = VpnError.fromException(exception, tunnelId = "nordvpn_UK")

        // Ensure the error type was correctly classified
        assertThat(error.type).isEqualTo(VpnError.ErrorType.AUTHENTICATION_FAILED)

        // Ensure details contains the stack trace
        assertThat(error.details).contains("java.lang.RuntimeException: Authentication failed - bad password")
        assertThat(error.details).contains("at com.multiregionvpn.core.VpnErrorTest")

        // Call getUserMessage and verify the results
        val userMessage = error.getUserMessage()

        // The user friendly message should contain the high-level original message
        assertThat(userMessage).contains(originalMessage)

        // SECURITY CHECK: The user friendly message must NEVER contain any raw stack trace elements
        assertThat(userMessage).doesNotContain("java.lang.RuntimeException")
        assertThat(userMessage).doesNotContain("at com.multiregionvpn.core.VpnErrorTest")
        assertThat(userMessage).doesNotContain("VpnErrorTest.kt")
    }

    @Test
    fun `getUserMessage formats other error categories securely without leaking details`() {
        val errors = listOf(
            VpnError(VpnError.ErrorType.CONNECTION_FAILED, "Server timed out", "Detailed stack trace here"),
            VpnError(VpnError.ErrorType.CONFIG_ERROR, "Config syntax error", "Detailed stack trace here"),
            VpnError(VpnError.ErrorType.INTERFACE_ERROR, "Permission denied", "Detailed stack trace here"),
            VpnError(VpnError.ErrorType.TUNNEL_ERROR, "Tunnel init failed", "Detailed stack trace here"),
            VpnError(VpnError.ErrorType.UNKNOWN, "Something went wrong", "Detailed stack trace here")
        )

        for (error in errors) {
            val userMessage = error.getUserMessage()
            assertThat(userMessage).contains(error.message)
            assertThat(userMessage).doesNotContain("Detailed stack trace here")
        }
    }
}
