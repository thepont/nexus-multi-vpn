package com.multiregionvpn.core

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VpnErrorSecurityTest {

    @Test
    fun `fromException should not include full stack trace`() {
        val exception = RuntimeException("Test exception")
        val vpnError = VpnError.fromException(exception)

        val details = vpnError.details ?: ""

        // Currently this fails because it DOES include the full stack trace
        // We want it to only include e.toString() which is "java.lang.RuntimeException: Test exception"
        // and NOT the stack trace lines starting with "at "
        assertFalse(details.contains("at com.multiregionvpn"), "Details should not contain stack trace")
    }

    @Test
    fun `VpnError should redact sensitive information from message and details`() {
        val sensitiveMessage = "Failed to login with password: mysecret123"
        val exception = RuntimeException(sensitiveMessage)
        val vpnError = VpnError.fromException(exception)

        // Currently this fails because it does NOT redact "mysecret123"
        assertFalse(vpnError.message.contains("mysecret123"), "Message should redact password")
        assertFalse(vpnError.details?.contains("mysecret123") == true, "Details should redact password")

        assertTrue(vpnError.message.contains("password: [REDACTED]"), "Message should contain [REDACTED]")
    }
}
