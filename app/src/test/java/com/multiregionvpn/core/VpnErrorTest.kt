package com.multiregionvpn.core

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VpnErrorTest {

    @Test
    fun `VpnError fromException should not include stack trace in details`() {
        val exception = RuntimeException("Test error message")
        val vpnError = VpnError.fromException(exception)

        // details should be e.toString(), which is "java.lang.RuntimeException: Test error message"
        // and NOT the full stack trace which starts with "java.lang.RuntimeException: Test error message\n\tat ..."
        val details = vpnError.details ?: ""

        assertTrue(details.contains("java.lang.RuntimeException: Test error message"))
        assertFalse(details.contains("\tat com.multiregionvpn"), "Details should not contain stack trace")
    }

    @Test
    fun `VpnError fromException correctly categorizes authentication errors`() {
        val exception = RuntimeException("Invalid credentials")
        val vpnError = VpnError.fromException(exception)

        assertTrue(vpnError.type == VpnError.ErrorType.AUTHENTICATION_FAILED)
    }
}
