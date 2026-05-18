package com.multiregionvpn.core

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VpnErrorTest {

    @Test
    fun `fromException should not include full stack trace in details`() {
        val exception = RuntimeException("Connection failed")
        val vpnError = VpnError.fromException(exception)

        // details should be e.toString(), which is "java.lang.RuntimeException: Connection failed"
        // it should NOT contain the multi-line stack trace
        assertEquals("java.lang.RuntimeException: Connection failed", vpnError.details)
        assertFalse(vpnError.details!!.contains("\tat "))
    }

    @Test
    fun `fromException should identify authentication errors`() {
        val exception = RuntimeException("authentication failed")
        val vpnError = VpnError.fromException(exception)

        assertEquals(VpnError.ErrorType.AUTHENTICATION_FAILED, vpnError.type)
    }

    @Test
    fun `getUserMessage should not leak stack trace`() {
        val exception = RuntimeException("Secret connection error")
        val vpnError = VpnError.fromException(exception)
        val userMessage = vpnError.getUserMessage()

        assertTrue(userMessage.contains("Could not connect to VPN server"))
        assertTrue(userMessage.contains("java.lang.RuntimeException: Secret connection error"))
        assertFalse(userMessage.contains("\tat com.multiregionvpn"))
    }
}
