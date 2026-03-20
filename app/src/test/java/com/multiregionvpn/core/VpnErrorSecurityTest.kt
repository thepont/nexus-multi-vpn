package com.multiregionvpn.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VpnErrorSecurityTest {

    @Test
    fun `fromException should NOT leak full stack trace in details`() {
        val exception = RuntimeException("Sensitive error message")
        val vpnError = VpnError.fromException(exception, "test_tunnel")

        val details = vpnError.details ?: ""

        // We verify that the details field ONLY contains the exception class name
        assertEquals("RuntimeException", details)

        // And ensure it DOES NOT contain any part of the stack trace
        assertFalse("Details should not contain full stack trace", details.contains("at com.multiregionvpn.core.VpnErrorSecurityTest"))
    }
}
