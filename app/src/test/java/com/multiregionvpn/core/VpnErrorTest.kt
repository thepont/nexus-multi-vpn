package com.multiregionvpn.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VpnErrorTest {

    @Test
    fun fromException_createsCorrectErrorTypeAndCapturesDetails() {
        val authException = IllegalArgumentException("Authentication error occurred: invalid username or password")
        val error = VpnError.fromException(authException, "tunnel-123")

        assertThat(error.type).isEqualTo(VpnError.ErrorType.AUTHENTICATION_FAILED)
        assertThat(error.message).contains("Authentication error occurred")
        assertThat(error.tunnelId).isEqualTo("tunnel-123")

        // Raw details should contain stack trace strings (e.g. package paths, class name)
        assertThat(error.details).contains("java.lang.IllegalArgumentException")
        assertThat(error.details).contains("VpnErrorTest")
    }

    @Test
    fun getUserMessage_doesNotLeakRawStackTraceDetails_forAuthenticationError() {
        val authException = IllegalArgumentException("Authentication error occurred: invalid username or password")
        val error = VpnError.fromException(authException, "tunnel-123")

        val userMessage = error.getUserMessage()

        // It must contain the clean high-level description
        assertThat(userMessage).contains("Authentication failed. Please check your NordVPN credentials")
        assertThat(userMessage).contains("Authentication error occurred")

        // It must NOT leak the stack trace or internal implementation classes
        assertThat(userMessage).doesNotContain("java.lang.IllegalArgumentException")
        assertThat(userMessage).doesNotContain("VpnErrorTest")
        assertThat(userMessage).doesNotContain("at ")
    }

    @Test
    fun getUserMessage_doesNotLeakRawStackTraceDetails_forConnectionError() {
        val connException = RuntimeException("Connection timed out to server")
        val error = VpnError.fromException(connException, "tunnel-456")

        val userMessage = error.getUserMessage()

        assertThat(userMessage).contains("Could not connect to VPN server")
        assertThat(userMessage).contains("Connection timed out to server")

        assertThat(userMessage).doesNotContain("java.lang.RuntimeException")
        assertThat(userMessage).doesNotContain("VpnErrorTest")
        assertThat(userMessage).doesNotContain("at ")
    }

    @Test
    fun getUserMessage_doesNotLeakRawStackTraceDetails_forConfigError() {
        val configException = IllegalStateException("Failed to parse config file")
        val error = VpnError.fromException(configException, "tunnel-789")

        val userMessage = error.getUserMessage()

        assertThat(userMessage).contains("Invalid VPN configuration")
        assertThat(userMessage).contains("Failed to parse config file")

        assertThat(userMessage).doesNotContain("java.lang.IllegalStateException")
        assertThat(userMessage).doesNotContain("VpnErrorTest")
        assertThat(userMessage).doesNotContain("at ")
    }

    @Test
    fun getUserMessage_doesNotLeakRawStackTraceDetails_forInterfaceError() {
        val interfaceException = RuntimeException("VPN interface permission denied")
        val error = VpnError.fromException(interfaceException, "tunnel-abc")

        val userMessage = error.getUserMessage()

        assertThat(userMessage).contains("VPN interface error")
        assertThat(userMessage).contains("VPN interface permission denied")

        assertThat(userMessage).doesNotContain("java.lang.RuntimeException")
        assertThat(userMessage).doesNotContain("VpnErrorTest")
        assertThat(userMessage).doesNotContain("at ")
    }

    @Test
    fun getUserMessage_doesNotLeakRawStackTraceDetails_forUnknownError() {
        val genericException = NullPointerException("Some unexpected error")
        val error = VpnError.fromException(genericException, "tunnel-xyz")

        val userMessage = error.getUserMessage()

        assertThat(userMessage).contains("An unexpected error occurred")
        assertThat(userMessage).contains("Some unexpected error")

        assertThat(userMessage).doesNotContain("java.lang.NullPointerException")
        assertThat(userMessage).doesNotContain("VpnErrorTest")
        assertThat(userMessage).doesNotContain("at ")
    }
}
