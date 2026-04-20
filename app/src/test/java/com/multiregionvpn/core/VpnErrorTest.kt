package com.multiregionvpn.core

import org.junit.Assert.assertTrue
import org.junit.Test

class VpnErrorTest {

    @Test
    fun `fromException should not include stack trace in details`() {
        val exception = RuntimeException("Test exception")
        val vpnError = VpnError.fromException(exception)

        // After fix, it should not include stack trace
        val details = vpnError.details ?: ""
        assertTrue("Details should not contain stack trace info", !details.contains("at com.multiregionvpn.core.VpnErrorTest"))
        assertTrue("Details should contain exception message", details.contains("Test exception"))
    }
}
