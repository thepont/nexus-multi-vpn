package com.multiregionvpn.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VpnErrorTest {

    @Test
    fun getUserMessage_WhenCreatedFromException_DoesNotIncludeStackTrace() {
        val exceptionMessage = "Auth failed"
        val exception = RuntimeException(exceptionMessage)
        val vpnError = VpnError.fromException(exception)

        val userMessage = vpnError.getUserMessage()

        // SECURITY FIX (CWE-209): getUserMessage() should NOT include the 'details' field (stack trace)
        assertThat(userMessage).contains("Auth failed")
        assertThat(userMessage).doesNotContain("java.lang.RuntimeException")
        assertThat(userMessage).doesNotContain("at com.multiregionvpn.core.VpnErrorTest")
    }

    @Test
    fun fromException_CorrectlyMapsErrorTypes() {
        val authException = RuntimeException("Authentication failed")
        val authError = VpnError.fromException(authException)
        assertThat(authError.type).isEqualTo(VpnError.ErrorType.AUTHENTICATION_FAILED)

        val connectionException = RuntimeException("Connection timeout")
        val connectionError = VpnError.fromException(connectionException)
        assertThat(connectionError.type).isEqualTo(VpnError.ErrorType.CONNECTION_FAILED)

        val configException = RuntimeException("Config parse error")
        val configError = VpnError.fromException(configException)
        assertThat(configError.type).isEqualTo(VpnError.ErrorType.CONFIG_ERROR)
    }
}
