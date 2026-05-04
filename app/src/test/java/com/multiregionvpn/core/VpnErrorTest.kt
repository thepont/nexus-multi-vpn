package com.multiregionvpn.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for VpnError - sensitive data leakage prevention
 */
class VpnErrorTest {

    @Test
    fun test_fromException_doesNotIncludeStackTraceInDetails() {
        val exception = RuntimeException("Sensitive error message")
        val vpnError = VpnError.fromException(exception)

        // The details should be the string representation of the exception, not the full stack trace
        assertThat(vpnError.details).isEqualTo(exception.toString())
        assertThat(vpnError.details).doesNotContain("at com.multiregionvpn.core.VpnErrorTest")
    }

    @Test
    fun test_fromException_categorizesAuthError() {
        val exception = RuntimeException("Invalid credentials for NordVPN")
        val vpnError = VpnError.fromException(exception)

        assertThat(vpnError.type).isEqualTo(VpnError.ErrorType.AUTHENTICATION_FAILED)
    }

    @Test
    fun test_fromException_categorizesConnectionError() {
        val exception = RuntimeException("Connection timed out")
        val vpnError = VpnError.fromException(exception)

        assertThat(vpnError.type).isEqualTo(VpnError.ErrorType.CONNECTION_FAILED)
    }
}
