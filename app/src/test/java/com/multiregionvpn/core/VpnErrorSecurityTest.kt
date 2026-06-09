package com.multiregionvpn.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Security unit test for VpnError information leakage.
 * Verifies that stack traces (CWE-209) are NOT exposed to the user.
 */
class VpnErrorSecurityTest {

    @Test
    fun `getUserMessage should not contain stack trace details when created from exception`() {
        // GIVEN: A VpnError created from an exception which contains a stack trace
        val sensitiveInfo = "SecretInternalClassName.kt:42"
        val exception = Exception("Generic connection error")
        // Manually set stack trace to contain something recognizable
        val stackTraceElement = StackTraceElement("SecretInternalClassName", "secretMethod", "SecretInternalClassName.kt", 42)
        exception.stackTrace = arrayOf(stackTraceElement)

        val vpnError = VpnError.fromException(exception)

        // Ensure details actually contains the stack trace (to verify the test itself is valid)
        assertThat(vpnError.details).contains(sensitiveInfo)

        // WHEN: Getting the user-friendly message
        val userMessage = vpnError.getUserMessage()

        // THEN: The user message should NOT contain the sensitive stack trace info
        // Note: Currently it DOES, so this test should FAIL before the fix
        assertThat(userMessage).doesNotContain(sensitiveInfo)
    }

    @Test
    fun `getUserMessage should not contain details field even for all error types`() {
        val details = "Internal system details that should be hidden"

        VpnError.ErrorType.values().forEach { type ->
            val vpnError = VpnError(
                type = type,
                message = "Public message",
                details = details
            )

            val userMessage = vpnError.getUserMessage()
            assertThat(userMessage).doesNotContain(details)
        }
    }
}
