package com.multiregionvpn.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VpnErrorTest {

    @Test
    fun `fromException should not include stack trace in details`() {
        val exception = RuntimeException("test error")
        val vpnError = VpnError.fromException(exception)

        assertThat(vpnError.details).isEqualTo(exception.toString())
        assertThat(vpnError.details).doesNotContain("at com.multiregionvpn")
    }

    @Test
    fun `fromException should correctly identify error types`() {
        val authException = RuntimeException("authentication failed")
        val authError = VpnError.fromException(authException)
        assertThat(authError.type).isEqualTo(VpnError.ErrorType.AUTHENTICATION_FAILED)

        val connException = RuntimeException("connection timeout")
        val connError = VpnError.fromException(connException)
        assertThat(connError.type).isEqualTo(VpnError.ErrorType.CONNECTION_FAILED)
    }
}
