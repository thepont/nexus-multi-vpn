package com.multiregionvpn.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class VpnErrorTest {

    @Test
    fun `fromException creates VpnError and getUserMessage does not leak stack trace`() {
        val exception = RuntimeException("Authentication failed due to invalid credentials")
        val vpnError = VpnError.fromException(exception, tunnelId = "tunnel-1")

        assertEquals(VpnError.ErrorType.AUTHENTICATION_FAILED, vpnError.type)
        assertEquals("Authentication failed due to invalid credentials", vpnError.message)
        assertNotNull(vpnError.details)

        val userMessage = vpnError.getUserMessage()

        // Ensure user-facing message contains the high-level description
        assert(userMessage.contains("Authentication failed due to invalid credentials"))

        // Ensure user-facing message does not contain raw stack trace frames
        assertFalse(userMessage.contains("java.lang.RuntimeException"))
        assertFalse(userMessage.contains("at com.multiregionvpn"))
    }

    @Test
    fun `getUserMessage returns safe message for all error types`() {
        for (type in VpnError.ErrorType.values()) {
            val error = VpnError(
                type = type,
                message = "Safe error description",
                details = "java.lang.IllegalStateException: Secret Internal Details\n\tat com.example.Internal.run(Internal.java:42)"
            )
            val userMessage = error.getUserMessage()

            assert(userMessage.contains("Safe error description"))
            assertFalse(userMessage.contains("Secret Internal Details"))
            assertFalse(userMessage.contains("at com.example.Internal"))
        }
    }
}
