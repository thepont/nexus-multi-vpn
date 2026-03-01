package com.multiregionvpn.core

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class VpnErrorSecurityTest {

    @Test
    fun `fromException should not include stack trace in details`() {
        val exception = RuntimeException("Sensitive error message")
        val tunnelId = "test_tunnel"

        val error = VpnError.fromException(exception, tunnelId)

        // Ensure details matches message, not stack trace
        assertEquals(exception.message, error.details)

        // Additional check: ensure it doesn't contain common stack trace markers
        val details = error.details ?: ""
        assertFalse(details.contains("at com.multiregionvpn"), "Details should not contain stack trace info")
        assertFalse(details.contains(".kt:"), "Details should not contain file line references")
    }

    @Test
    fun `fromException should handle null message gracefully`() {
        val exception = RuntimeException()
        val error = VpnError.fromException(exception)

        // When e.message is null:
        // errorMsg becomes "Unknown error"
        // details becomes e.message which is null
        assertNotNull(error.message)
        assertEquals("Unknown error", error.message)
        assertNull(error.details)
    }
}
