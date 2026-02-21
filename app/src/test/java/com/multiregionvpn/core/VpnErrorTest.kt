package com.multiregionvpn.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for VpnError exception mapping and security sanitization.
 */
class VpnErrorTest {

    @Test
    fun `when fromException is called, then details should not contain stack trace`() {
        // GIVEN: An exception with a message and a stack trace
        val message = "Authentication failed on server"
        val exception = Exception(message)

        // WHEN: VpnError is created from the exception
        val vpnError = VpnError.fromException(exception)

        // THEN: Details should match the message and not contain stack trace elements
        // This verifies the security fix against information leakage
        assertThat(vpnError.details).isEqualTo(message)
        assertThat(vpnError.details).doesNotContain("at java.lang.Exception")
        assertThat(vpnError.details).doesNotContain("VpnErrorTest")
    }

    @Test
    fun `when exception message contains auth keywords, then error type is AUTHENTICATION_FAILED`() {
        val exceptions = listOf(
            Exception("auth failed"),
            Exception("invalid credentials"),
            Exception("wrong password"),
            Exception("username not found")
        )

        exceptions.forEach { e ->
            val vpnError = VpnError.fromException(e)
            assertThat(vpnError.type).isEqualTo(VpnError.ErrorType.AUTHENTICATION_FAILED)
        }
    }

    @Test
    fun `when exception message contains connection keywords, then error type is CONNECTION_FAILED`() {
        val exceptions = listOf(
            Exception("connection timeout"),
            Exception("server unreachable"),
            Exception("failed to connect")
        )

        exceptions.forEach { e ->
            val vpnError = VpnError.fromException(e)
            assertThat(vpnError.type).isEqualTo(VpnError.ErrorType.CONNECTION_FAILED)
        }
    }

    @Test
    fun `when exception has no message, then default message is used and details are sanitized`() {
        // GIVEN: An exception without a message
        val exception = Exception()

        // WHEN: VpnError is created
        val vpnError = VpnError.fromException(exception)

        // THEN: Default message is used and details do not contain stack trace
        assertThat(vpnError.message).isEqualTo("Unknown error")
        assertThat(vpnError.details).isEqualTo("Unknown error")
        assertThat(vpnError.details).doesNotContain("at ")
    }
}
