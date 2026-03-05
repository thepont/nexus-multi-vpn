package com.multiregionvpn.core

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VpnErrorSecurityTest {

    @Test
    fun `fromException should not leak stack trace in details or message`() {
        val exception = try {
            // Force an exception to get a real stack trace
            throw IllegalStateException("Sensitive internal error occurred")
        } catch (e: Exception) {
            e
        }

        val tunnelId = "test-tunnel-123"
        val vpnError = VpnError.fromException(exception, tunnelId)

        // The stack trace should contain internal class names and method names
        val stackTrace = exception.stackTraceToString()
        assertTrue(stackTrace.contains("VpnErrorSecurityTest"), "Test setup failed: exception doesn't contain expected stack trace info")

        // CHECK: The VpnError fields should NOT contain the full stack trace or internal method names
        // Note: The message itself ("Sensitive internal error occurred") might be okay if it's considered safe,
        // but the full stack trace definitely shouldn't be there.

        val details = vpnError.details ?: ""
        val message = vpnError.message

        // Assert that the full stack trace is not in details or message
        assertFalse(details.contains("VpnErrorSecurityTest"), "Stack trace found in VpnError details! Leak detected.")
        assertFalse(details.contains("IllegalStateException"), "Internal exception type found in VpnError details!")
        assertFalse(message.contains("VpnErrorSecurityTest"), "Stack trace found in VpnError message! Leak detected.")
    }

    @Test
    fun `fromException should map authentication errors correctly without leaking details`() {
        val authException = Exception("Authentication failed for user: admin")
        val vpnError = VpnError.fromException(authException)

        assertTrue(vpnError.type == VpnError.ErrorType.AUTHENTICATION_FAILED, "Should be mapped to AUTHENTICATION_FAILED")

        // Even for auth errors, no stack trace should be present in details
        val details = vpnError.details ?: ""
        assertFalse(details.contains("Exception"), "Exception details found in VpnError details!")
    }
}
