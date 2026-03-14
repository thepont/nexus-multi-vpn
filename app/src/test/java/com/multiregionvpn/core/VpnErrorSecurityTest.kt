package com.multiregionvpn.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Test

class VpnErrorSecurityTest {

    @Test
    fun `fromException should not leak stack trace in details`() {
        val exception = RuntimeException("Test exception")
        val vpnError = VpnError.fromException(exception)

        // The details should only contain the class name, not the full stack trace
        assertEquals("RuntimeException", vpnError.details)

        // Ensure it definitely doesn't contain common stack trace markers
        assertFalse("Details should not contain stack trace", vpnError.details!!.contains("at com.multiregionvpn"))
        assertFalse("Details should not contain stack trace", vpnError.details!!.contains(".kt:"))
    }

    @Test
    fun `getUserMessage should not leak stack trace`() {
        val exception = RuntimeException("Auth failed")
        val vpnError = VpnError.fromException(exception)
        val userMessage = vpnError.getUserMessage()

        // Ensure user message doesn't contain stack trace markers
        assertFalse("User message should not contain stack trace", userMessage.contains("at com.multiregionvpn"))
        assertFalse("User message should not contain stack trace", userMessage.contains(".kt:"))
    }
}
