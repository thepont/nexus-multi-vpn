package com.multiregionvpn.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VpnErrorTest {

    @Test
    fun `fromException sanitizes stack trace from details`() {
        val exception = RuntimeException("Sensitive error message")
        val error = VpnError.fromException(exception)

        // Ensure details do not contain common stack trace markers
        val details = error.details ?: ""
        assertFalse("Details should not contain stack trace", details.contains("at com.multiregionvpn"))
        assertFalse("Details should not contain stack trace line numbers", details.contains(".kt:"))

        // In our sanitized version, details should be just the message
        assertEquals("Sensitive error message", details)
    }

    @Test
    fun `fromException identifies authentication errors`() {
        val exception = RuntimeException("Authentication failed")
        val error = VpnError.fromException(exception)

        assertEquals(VpnError.ErrorType.AUTHENTICATION_FAILED, error.type)
    }
}
