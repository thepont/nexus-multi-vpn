package com.multiregionvpn.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VpnErrorTest {

    @Test
    fun test_fromException_doesNotIncludeStackTrace() {
        // Given an exception with a stack trace
        val exception = Exception("Test exception message")

        // When creating a VpnError from it
        val vpnError = VpnError.fromException(exception)

        // Then the details should NOT contain a full stack trace (indicated by package names and line numbers)
        // Instead it should just be the exception.toString() result
        val expectedDetails = exception.toString()
        assertEquals(expectedDetails, vpnError.details)

        // Verify it doesn't contain common stack trace markers
        assertFalse("Details should not contain at marker", vpnError.details!!.contains("\tat "))
    }

    @Test
    fun test_fromException_categorizesAuthError() {
        val exception = Exception("Invalid credentials or authentication failed")
        val vpnError = VpnError.fromException(exception)

        assertEquals(VpnError.ErrorType.AUTHENTICATION_FAILED, vpnError.type)
        assertEquals("Invalid credentials or authentication failed", vpnError.message)
    }
}
