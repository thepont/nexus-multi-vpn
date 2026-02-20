package com.multiregionvpn.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VpnErrorTest {
    @Test
    fun testFromException_doesNotLeakStackTrace() {
        val vpnError = VpnError.fromException(RuntimeException("Secret"))
        assertThat(vpnError.details).doesNotContain("VpnErrorTest")
    }

    @Test
    fun testFromException_mapsErrors() {
        assertThat(VpnError.fromException(RuntimeException("auth failed")).type)
            .isEqualTo(VpnError.ErrorType.AUTHENTICATION_FAILED)
        assertThat(VpnError.fromException(RuntimeException("timeout")).type)
            .isEqualTo(VpnError.ErrorType.CONNECTION_FAILED)
    }
}
