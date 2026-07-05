package com.multiregionvpn.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for VpnError - Ensuring no technical details are leaked in user messages.
 */
class VpnErrorTest {

    @Test
    fun `getUserMessage should NOT include technical details from details field (CWE-209)`() {
        val technicalDetails = "java.lang.RuntimeException: Secret stack trace\n at com.example.Leak.doWork(Leak.kt:123)"
        val error = VpnError(
            type = VpnError.ErrorType.AUTHENTICATION_FAILED,
            message = "Login failed",
            details = technicalDetails
        )

        val userMessage = error.getUserMessage()

        // Verify technical details are NOT present in the user-facing message
        assertThat(userMessage).doesNotContain("RuntimeException")
        assertThat(userMessage).doesNotContain("Leak.kt")
        assertThat(userMessage).contains("Login failed")
    }

    @Test
    fun `getUserMessage should include message when details are null`() {
        val errorMessage = "Something went wrong"
        val error = VpnError(
            type = VpnError.ErrorType.UNKNOWN,
            message = errorMessage,
            details = null
        )

        val userMessage = error.getUserMessage()

        assertThat(userMessage).contains(errorMessage)
    }

    @Test
    fun `fromException should populate details with stack trace`() {
        val exception = RuntimeException("Test exception")
        val error = VpnError.fromException(exception)

        assertThat(error.details).contains("RuntimeException")
        assertThat(error.details).contains("test_fromException_should_populate_details_with_stack_trace")
    }
}
