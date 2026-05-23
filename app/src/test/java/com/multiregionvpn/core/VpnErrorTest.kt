package com.multiregionvpn.core

import org.junit.Test
import kotlin.test.assertTrue

class VpnErrorTest {
    @Test
    fun `fromException should redact stack trace`() {
        val exception = RuntimeException("Test exception")
        val error = VpnError.fromException(exception)

        // Verify that it no longer contains stack trace information
        assertTrue(!error.details!!.contains("at com.multiregionvpn.core.VpnErrorTest"),
            "Details should NOT contain stack trace information (fixed behavior)")

        // Verify it still contains the exception message
        assertTrue(error.details!!.contains("Test exception"),
            "Details should still contain the exception message")
    }
}
