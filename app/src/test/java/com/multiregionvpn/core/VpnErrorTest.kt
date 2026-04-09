package com.multiregionvpn.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class VpnErrorTest {

    @Test
    fun fromException_doesNotIncludeStackTraceInDetails() {
        val exceptionMessage = "Auth failed"
        val exception = RuntimeException(exceptionMessage)

        val vpnError = VpnError.fromException(exception)

        // The details should now just be the exception message, not the full stack trace
        assertEquals(exceptionMessage, vpnError.details)

        // Verify it doesn't contain a common stack trace marker like "at com.multiregionvpn"
        val stackTrace = exception.stackTraceToString()
        assertNotEquals(stackTrace, vpnError.details)
    }

    @Test
    fun fromException_usesExceptionMessage() {
        val exceptionMessage = "Connection timed out"
        val exception = RuntimeException(exceptionMessage)

        val vpnError = VpnError.fromException(exception)

        assertEquals(VpnError.ErrorType.CONNECTION_FAILED, vpnError.type)
        assertEquals(exceptionMessage, vpnError.message)
        assertEquals(exceptionMessage, vpnError.details)
    }
}
