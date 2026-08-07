package com.multiregionvpn.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VpnErrorTest {

    @Test
    fun test_getUserMessage_doesNotLeakStackTraceOrRawDetails() {
        // GIVEN: An exception with sensitive details and stack trace
        val sensitiveException = RuntimeException("Database connection error: credentials (user=admin, pass=secret123)")
        val vpnError = VpnError.fromException(sensitiveException)

        // WHEN: Calling getUserMessage()
        val userMessage = vpnError.getUserMessage()

        // THEN: The user message should be a safe, high-level message and NOT contain raw details or stack trace
        assertThat(userMessage).doesNotContain("Database connection error")
        assertThat(userMessage).doesNotContain("user=admin")
        assertThat(userMessage).doesNotContain("pass=secret123")
        assertThat(userMessage).doesNotContain("RuntimeException")
        assertThat(userMessage).doesNotContain("at ") // indicative of stack trace elements
    }
}
