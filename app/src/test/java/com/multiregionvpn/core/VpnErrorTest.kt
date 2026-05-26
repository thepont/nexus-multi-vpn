package com.multiregionvpn.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class VpnErrorTest {

    @Test
    fun `fromException should redact full stack trace in details`() {
        // GIVEN: An exception with a full stack trace
        val exception = try {
            throw Exception("Auth failed with credentials")
        } catch (e: Exception) {
            e
        }

        // WHEN: Creating VpnError from this exception
        val vpnError = VpnError.fromException(exception)

        // THEN: details should only contain e.toString(), not the full stack trace
        assertNotNull(vpnError.details)
        assertEquals(exception.toString(), vpnError.details)

        // Ensure it doesn't contain common stack trace markers like "at " or multiple lines
        assertFalse("Details should not contain full stack trace",
            vpnError.details!!.contains("\n\tat "))
    }
}
