package com.multiregionvpn.core

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Unit tests for [VpnError] to verify correct mapping from exceptions
 * and secure handling of user-facing error messages (CWE-209 mitigation).
 */
class VpnErrorTest {

    @Test
    fun `fromException maps authentication exceptions correctly`() {
        // GIVEN: An authentication exception
        val exception = RuntimeException("invalid credentials or bad password")

        // WHEN: Creating VpnError from the exception
        val vpnError = VpnError.fromException(exception)

        // THEN: Error type should be AUTHENTICATION_FAILED
        assertEquals(VpnError.ErrorType.AUTHENTICATION_FAILED, vpnError.type)
        assertEquals("invalid credentials or bad password", vpnError.message)
        assertNotNull(vpnError.details)
        assertTrue(vpnError.details!!.contains("java.lang.RuntimeException"))
    }

    @Test
    fun `fromException maps connection exceptions correctly`() {
        // GIVEN: A connection timeout exception
        val exception = RuntimeException("connection timed out")

        // WHEN: Creating VpnError from the exception
        val vpnError = VpnError.fromException(exception)

        // THEN: Error type should be CONNECTION_FAILED
        assertEquals(VpnError.ErrorType.CONNECTION_FAILED, vpnError.type)
        assertEquals("connection timed out", vpnError.message)
        assertNotNull(vpnError.details)
        assertTrue(vpnError.details!!.contains("java.lang.RuntimeException"))
    }

    @Test
    fun `fromException maps config exceptions correctly`() {
        // GIVEN: A configuration exception
        val exception = RuntimeException("failed to parse config file")

        // WHEN: Creating VpnError from the exception
        val vpnError = VpnError.fromException(exception)

        // THEN: Error type should be CONFIG_ERROR
        assertEquals(VpnError.ErrorType.CONFIG_ERROR, vpnError.type)
        assertEquals("failed to parse config file", vpnError.message)
    }

    @Test
    fun `fromException maps interface exceptions correctly`() {
        // GIVEN: A VPN interface permission exception
        val exception = RuntimeException("vpn permission denied")

        // WHEN: Creating VpnError from the exception
        val vpnError = VpnError.fromException(exception)

        // THEN: Error type should be INTERFACE_ERROR
        assertEquals(VpnError.ErrorType.INTERFACE_ERROR, vpnError.type)
        assertEquals("vpn permission denied", vpnError.message)
    }

    @Test
    fun `fromException maps unknown exceptions correctly`() {
        // GIVEN: A generic exception
        val exception = IllegalArgumentException("Something went wrong internally")

        // WHEN: Creating VpnError from the exception
        val vpnError = VpnError.fromException(exception)

        // THEN: Error type should be UNKNOWN
        assertEquals(VpnError.ErrorType.UNKNOWN, vpnError.type)
        assertEquals("Something went wrong internally", vpnError.message)
    }

    @Test
    fun `getUserMessage should not expose stack traces or details`() {
        // GIVEN: A VpnError containing detailed stack trace
        val message = "Auth failure"
        val stackTrace = "at com.multiregionvpn.core.VpnErrorTest.test(VpnErrorTest.kt:42)\nat org.junit.runner.JUnitCore.run(JUnitCore.java:137)"
        val error = VpnError(
            type = VpnError.ErrorType.AUTHENTICATION_FAILED,
            message = message,
            details = stackTrace
        )

        // WHEN: Retrieving the user-facing message
        val userMessage = error.getUserMessage()

        // THEN: The message must contain the high-level 'message' but NOT the 'details' (stack trace)
        assertTrue(userMessage.contains(message), "User message should contain the main error description")
        assertFalse(userMessage.contains("VpnErrorTest.kt"), "User message MUST NOT expose internal class names or line numbers")
        assertFalse(userMessage.contains("JUnitCore.run"), "User message MUST NOT expose internal framework stack trace details")
    }
}
