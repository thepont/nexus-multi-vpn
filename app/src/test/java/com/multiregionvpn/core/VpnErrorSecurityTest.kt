package com.multiregionvpn.core

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VpnErrorSecurityTest {

    @Test
    fun `fromException should not include stack trace in details`() {
        val exception = RuntimeException("Sensitive error message")
        val vpnError = VpnError.fromException(exception)

        val details = vpnError.details ?: ""

        // Stack traces usually contain class names and line numbers
        assertFalse(details.contains("VpnErrorSecurityTest.kt"), "Details should not contain stack trace info")
        assertFalse(details.contains("at "), "Details should not contain stack trace 'at' markers")
    }

    @Test
    fun `fromException should map authentication errors correctly`() {
        val exception = RuntimeException("auth failed")
        val vpnError = VpnError.fromException(exception)

        assertTrue(vpnError.type == VpnError.ErrorType.AUTHENTICATION_FAILED)
    }
}
