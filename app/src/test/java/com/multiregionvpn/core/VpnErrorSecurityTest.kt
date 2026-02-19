package com.multiregionvpn.core

import org.junit.Test
import kotlin.test.*

class VpnErrorSecurityTest {

    @Test
    fun `fromException should not include full stack trace in details`() {
        // GIVEN: An exception with a stack trace
        val exception = RuntimeException("Sensitive error message")

        // WHEN: Creating VpnError from the exception
        val vpnError = VpnError.fromException(exception)

        // THEN: Details should not contain common stack trace markers
        val details = vpnError.details ?: ""

        // Stack traces usually contain "at package.Class.method(File.java:line)"
        assertFalse(details.contains("at "), "Details should not contain stack trace 'at' markers")
        assertFalse(details.contains(".kt:"), "Details should not contain Kotlin file line numbers")
        assertFalse(details.contains(".java:"), "Details should not contain Java file line numbers")

        // AND: Details should match the error message (our new behavior)
        assertEquals("Sensitive error message", details)
    }
}
