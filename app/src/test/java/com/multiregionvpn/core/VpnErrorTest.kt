package com.multiregionvpn.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for VpnError - error mapping and security
 */
class VpnErrorTest {

    @Test
    fun testFromException_doesNotLeakStackTrace() {
        val exception = RuntimeException("Sensitive Error Message")
        val vpnError = VpnError.fromException(exception)

        // This test will FAIL before the fix because it currently leaks the stack trace
        // A stack trace would contain the class and method name: "VpnErrorTest"
        val stackTraceIndicator = "VpnErrorTest"

        assertThat(vpnError.details).isNotNull()
        assertThat(vpnError.details).doesNotContain(stackTraceIndicator)
    }

    @Test
    fun testFromException_mapsAuthenticationError() {
        val exception = RuntimeException("Authentication failed: invalid password")
        val vpnError = VpnError.fromException(exception)

        assertThat(vpnError.type).isEqualTo(VpnError.ErrorType.AUTHENTICATION_FAILED)
    }

    @Test
    fun testFromException_mapsConnectionError() {
        val exception = RuntimeException("Connection timeout")
        val vpnError = VpnError.fromException(exception)

        assertThat(vpnError.type).isEqualTo(VpnError.ErrorType.CONNECTION_FAILED)
    }

    @Test
    fun testFromException_mapsConfigError() {
        val exception = RuntimeException("Failed to parse config file")
        val vpnError = VpnError.fromException(exception)

        assertThat(vpnError.type).isEqualTo(VpnError.ErrorType.CONFIG_ERROR)
    }

    @Test
    fun testFromException_mapsInterfaceError() {
        val exception = RuntimeException("VPN permission denied")
        val vpnError = VpnError.fromException(exception)

        assertThat(vpnError.type).isEqualTo(VpnError.ErrorType.INTERFACE_ERROR)
    }

    @Test
    fun testGetUserMessage_includesDetails() {
        val vpnError = VpnError(
            type = VpnError.ErrorType.UNKNOWN,
            message = "Base message",
            details = "Detailed info"
        )

        val userMessage = vpnError.getUserMessage()
        // If details is provided, it's used instead of message in UNKNOWN type
        assertThat(userMessage).contains("Detailed info")
    }
}
