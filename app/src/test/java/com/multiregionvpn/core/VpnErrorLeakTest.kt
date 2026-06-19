package com.multiregionvpn.core

import org.junit.Assert.assertTrue
import org.junit.Test

class VpnErrorLeakTest {

    @Test
    fun testGetUserMessageLeaksStackTrace() {
        val exception = RuntimeException("Sensitive operation failed")
        val vpnError = VpnError.fromException(exception)

        val userMessage = vpnError.getUserMessage()

        // Check if the stack trace is NOT present in the user message
        // A stack trace usually contains "at " and the package name
        assertTrue("User message should NOT contain stack trace info",
            !userMessage.contains("at com.multiregionvpn.core.VpnErrorLeakTest"))
        assertTrue("User message should still contain the exception message",
            userMessage.contains("Sensitive operation failed"))
    }
}
