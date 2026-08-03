package com.multiregionvpn.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [VpnError] verification, specifically checking that raw exceptions
 * and stack traces (CWE-209) are not leaked in user-facing messages.
 */
class VpnErrorTest {

    @Test
    fun test_vpnErrorFromException_doesNotLeakStackTraceInUserMessage() {
        val rootCauseException = RuntimeException("Severe system authentication failure")
        val vpnError = VpnError.fromException(rootCauseException, "test-tunnel")

        // Ensure the error was categorized correctly
        assertEquals(VpnError.ErrorType.AUTHENTICATION_FAILED, vpnError.type)
        assertEquals("Severe system authentication failure", vpnError.message)
        assertNotNull(vpnError.details)
        assertTrue(vpnError.details!!.contains("java.lang.RuntimeException"))

        // Ensure user message is formatted nicely without leaking the raw stack trace/details
        val userMessage = vpnError.getUserMessage()
        assertTrue(userMessage.contains("Authentication failed"))
        assertTrue(userMessage.contains("Severe system authentication failure"))
        assertFalse(userMessage.contains("java.lang.RuntimeException"))
        assertFalse(userMessage.contains("at com.multiregionvpn"))
    }

    @Test
    fun test_vpnErrorTypesAndUserMessages() {
        for (type in VpnError.ErrorType.values()) {
            val error = VpnError(type, "Test Message", "Raw internal stack trace/details")
            val userMsg = error.getUserMessage()

            // Check that the safe high-level message is included, but raw details are not
            assertTrue(userMsg.contains("Test Message"))
            assertFalse(userMsg.contains("Raw internal stack trace/details"))
        }
    }
}
