package com.multiregionvpn.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnErrorTest {

    @Test
    fun `fromException should not include stack trace in details`() {
        val exception = RuntimeException("Sensitive error message")
        val tunnelId = "test-tunnel"

        val error = VpnError.fromException(exception, tunnelId)

        // The message is okay to include
        assertTrue("Message should be present in details", error.details?.contains("Sensitive error message") == true)

        // But the stack trace should NOT be present
        // Stack trace usually contains class names, method names, and line numbers
        // e.g. "com.multiregionvpn.core.VpnErrorTest.fromException should not include stack trace in details(VpnErrorTest.kt:10)"
        assertFalse("Stack trace should not be present in details", error.details?.contains("at com.multiregionvpn") == true)
        assertFalse("Stack trace should not be present in details", error.details?.contains(".kt:") == true)
    }
}
