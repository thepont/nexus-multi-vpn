package com.multiregionvpn.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnErrorTest {

    @Test
    fun `fromException should not include stack trace in details`() {
        val exception = RuntimeException("Test exception")
        val vpnError = VpnError.fromException(exception)

        val details = vpnError.details ?: ""

        // Ensure it doesn't contain common stack trace markers
        assertFalse("Details should not contain stack trace", details.contains("at com.multiregionvpn"))
        assertFalse("Details should not contain stack trace", details.contains("VpnErrorTest.kt"))

        // Ensure it contains the exception name and message
        assertTrue("Details should contain exception class name", details.contains("java.lang.RuntimeException"))
        assertTrue("Details should contain exception message", details.contains("Test exception"))
    }
}
