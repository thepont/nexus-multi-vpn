package com.multiregionvpn.core

import org.junit.Test
import kotlin.test.*

/**
 * Security tests for VpnError to ensure no sensitive information leakage.
 */
class VpnErrorSecurityTest {

    @Test
    fun `VpnError fromException should not leak stack traces in details`() {
        // GIVEN: An exception with a full stack trace
        val exception = try {
            throw RuntimeException("Secret internal error")
        } catch (e: Exception) {
            e
        }

        val stackTrace = exception.stackTraceToString()

        // WHEN: Creating a VpnError from the exception
        val vpnError = VpnError.fromException(exception)

        // THEN: The details field should NOT contain the stack trace
        // (Currently this is expected to FAIL until we fix it)
        val details = vpnError.details ?: ""

        assertFalse(details.contains("RuntimeException"), "Details should not contain exception class name from stack trace")
        assertFalse(details.contains("VpnErrorSecurityTest"), "Details should not contain internal class names from stack trace")
        assertNotEquals(stackTrace, details, "Details should not be the full stack trace")
    }

    @Test
    fun `VpnError fromException should use message instead of stack trace`() {
        val message = "Connection timed out"
        val exception = RuntimeException(message)

        val vpnError = VpnError.fromException(exception)

        // After fix, details should probably be the message or a sanitized version
        // For now, let's just ensure it's not the stack trace
        assertNotEquals(exception.stackTraceToString(), vpnError.details)
    }
}
