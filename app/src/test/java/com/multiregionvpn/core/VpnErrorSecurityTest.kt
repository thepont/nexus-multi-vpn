package com.multiregionvpn.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnErrorSecurityTest {

    @Test
    fun `fromException should not leak stack trace in details`() {
        // Create an exception with a stack trace
        val exception = try {
            throw RuntimeException("Secret error message")
        } catch (e: Exception) {
            e
        }

        val vpnError = VpnError.fromException(exception)

        // Details should be the simple name of the exception class, not the stack trace
        assertTrue("Details should be the exception class name",
            vpnError.details == "RuntimeException")

        // Ensure no stack trace markers are present
        assertFalse("Details should not contain stack trace markers",
            vpnError.details?.contains("at ") ?: false)
        assertFalse("Details should not contain the error message if it's not the class name",
            vpnError.details?.contains("Secret error message") ?: false)
    }

    @Test
    fun `fromException should not leak stack trace in message`() {
        val exception = RuntimeException("Some error")
        val vpnError = VpnError.fromException(exception)

        // The message can contain the exception message, but not the stack trace
        assertFalse("Message should not contain stack trace markers",
            vpnError.message.contains("at "))
    }
}
