package com.multiregionvpn.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnErrorTest {

    @Test
    fun `fromException should not include full stack trace in details`() {
        val exception = Exception("Test exception")
        val vpnError = VpnError.fromException(exception)

        // The current implementation uses e.stackTraceToString()
        // We want to ensure it DOES NOT contain common stack trace indicators like class names and line numbers
        val details = vpnError.details ?: ""

        // This is expected to FAIL with current implementation
        assertFalse("Details should not contain stack trace", details.contains("at com.multiregionvpn.core.VpnErrorTest"))
    }

    @Test
    fun `fromException should categorize authentication errors correctly`() {
        val exception = Exception("Authentication failed")
        val vpnError = VpnError.fromException(exception)

        assertTrue(vpnError.type == VpnError.ErrorType.AUTHENTICATION_FAILED)
    }
}
