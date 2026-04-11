package com.multiregionvpn.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnErrorTest {

    @Test
    fun fromException_shouldNotLeakStackTrace() {
        val exception = RuntimeException("Auth failed")
        val vpnError = VpnError.fromException(exception)

        val details = vpnError.details ?: ""

        assertTrue("Details should contain the message", details.contains("Auth failed"))

        // After the fix, it should NOT contain stack trace elements
        assertFalse("Details should not contain stack trace 'at ' markers", details.contains("at "))
        // The sanitized details should just be the message
        assertTrue("Details should equal the error message", details == "Auth failed")
    }
}
