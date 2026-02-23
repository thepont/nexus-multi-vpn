package com.multiregionvpn.core

import org.junit.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals

class VpnErrorTest {

    @Test
    fun `fromException should not include stack trace in details`() {
        val exception = RuntimeException("Test error message")
        val error = VpnError.fromException(exception, "test-tunnel")

        // Currently this test will FAIL after Step 1 because details WILL contain stack trace
        // I'll write it so it passes with the DESIRED behavior
        assertFalse(error.details?.contains("at com.multiregionvpn") == true, "Details should not contain stack trace")
        assertEquals("Test error message", error.details)
    }

    @Test
    fun `getUserMessage should format correctly for authentication errors`() {
        val error = VpnError(
            type = VpnError.ErrorType.AUTHENTICATION_FAILED,
            message = "Auth failed",
            details = "Invalid credentials"
        )
        val userMessage = error.getUserMessage()
        assertTrue(userMessage.contains("Authentication failed"))
        assertTrue(userMessage.contains("Invalid credentials"))
    }
}
