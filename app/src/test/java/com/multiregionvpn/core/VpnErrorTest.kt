package com.multiregionvpn.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnErrorTest {

    @Test
    fun testGetUserMessageDoesNotContainDetails() {
        val message = "Connection failed"
        val details = "java.lang.Exception: Stack trace details\n\tat com.multiregionvpn.VpnEngineService.start(VpnEngineService.kt:100)"

        val error = VpnError(
            type = VpnError.ErrorType.CONNECTION_FAILED,
            message = message,
            details = details
        )

        val userMessage = error.getUserMessage()

        assertTrue("User message should contain the primary error message", userMessage.contains(message))
        assertFalse("User message should NOT contain stack trace details", userMessage.contains("Stack trace details"))
        assertFalse("User message should NOT contain line numbers from stack trace", userMessage.contains("VpnEngineService.kt:100"))
    }

    @Test
    fun testFromExceptionStoresDetails() {
        val exceptionMessage = "Auth failed"
        val exception = Exception(exceptionMessage)

        val error = VpnError.fromException(exception)

        assertTrue("Error object should store stack trace in details", error.details?.contains("VpnErrorTest.kt") ?: false)
        assertTrue("Error message should be extracted from exception", error.message == exceptionMessage)

        val userMessage = error.getUserMessage()
        assertFalse("User message should NOT leak details from exception", userMessage.contains("VpnErrorTest.kt"))
    }
}
