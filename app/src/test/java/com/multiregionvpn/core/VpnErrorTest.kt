package com.multiregionvpn.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnErrorTest {

    @Test
    fun getUserMessage_DoesNotIncludeDetails() {
        val stackTrace = "java.lang.RuntimeException: something went wrong\n\tat com.multiregionvpn.core.VpnErrorTest.test(VpnErrorTest.kt:10)"
        val message = "Simple error message"

        val vpnError = VpnError(
            type = VpnError.ErrorType.UNKNOWN,
            message = message,
            details = stackTrace
        )

        val userMessage = vpnError.getUserMessage()

        assertTrue("User message should contain the main error message", userMessage.contains(message))
        assertFalse("User message should NOT contain stack trace details", userMessage.contains("java.lang.RuntimeException"))
        assertFalse("User message should NOT contain stack trace details", userMessage.contains("at com.multiregionvpn"))
    }

    @Test
    fun getUserMessage_AuthenticationFailed_DoesNotIncludeDetails() {
        val stackTrace = "Auth failed details"
        val message = "Invalid credentials"

        val vpnError = VpnError(
            type = VpnError.ErrorType.AUTHENTICATION_FAILED,
            message = message,
            details = stackTrace
        )

        val userMessage = vpnError.getUserMessage()

        assertTrue("User message should contain the main error message", userMessage.contains(message))
        assertFalse("User message should NOT contain stack trace details", userMessage.contains(stackTrace))
    }
}
