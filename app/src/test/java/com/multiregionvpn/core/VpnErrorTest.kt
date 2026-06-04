package com.multiregionvpn.core

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VpnErrorTest {
    @Test
    fun `fromException should not include stack trace in details`() {
        // GIVEN: An exception with a stack trace
        val exception = RuntimeException("Test error message")

        // WHEN: Creating a VpnError from the exception
        val vpnError = VpnError.fromException(exception)

        // THEN: The details should not contain stack trace elements (like "at com.multiregionvpn")
        val details = vpnError.details ?: ""
        assertFalse(details.contains("at com.multiregionvpn"), "Details should not contain stack trace")
        assertTrue(details.contains("Test error message"), "Details should contain the error message")
    }
}
