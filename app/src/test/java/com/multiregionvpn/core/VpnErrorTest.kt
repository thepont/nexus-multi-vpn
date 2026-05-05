package com.multiregionvpn.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnErrorTest {

    @Test
    fun `fromException categorizes authentication errors`() {
        val exception = Exception("Auth failed")
        val vpnError = VpnError.fromException(exception)

        assertEquals(VpnError.ErrorType.AUTHENTICATION_FAILED, vpnError.type)
        assertEquals("Auth failed", vpnError.message)
    }

    @Test
    fun `fromException categorizes connection errors`() {
        val exception = Exception("Connection timeout")
        val vpnError = VpnError.fromException(exception)

        assertEquals(VpnError.ErrorType.CONNECTION_FAILED, vpnError.type)
        assertEquals("Connection timeout", vpnError.message)
    }

    @Test
    fun `fromException categorizes configuration errors`() {
        val exception = Exception("Tunnel config error")
        val vpnError = VpnError.fromException(exception)

        assertEquals(VpnError.ErrorType.CONFIG_ERROR, vpnError.type)
        assertEquals("Tunnel config error", vpnError.message)
    }

    @Test
    fun `fromException categorizes interface errors`() {
        val exception = Exception("VPN permission")
        val vpnError = VpnError.fromException(exception)

        assertEquals(VpnError.ErrorType.INTERFACE_ERROR, vpnError.type)
        assertEquals("VPN permission", vpnError.message)
    }

    @Test
    fun `fromException does not leak stack trace in details`() {
        val exception = RuntimeException("Sensitive error")
        val vpnError = VpnError.fromException(exception)

        // details should be just e.toString(), not the full stack trace
        assertEquals("java.lang.RuntimeException: Sensitive error", vpnError.details)

        // Ensure it doesn't contain common stack trace elements
        assertFalse(vpnError.details!!.contains("at com.multiregionvpn"))
    }

    @Test
    fun `getUserMessage includes details but not stack trace`() {
        val exception = Exception("Auth error")
        val vpnError = VpnError.fromException(exception)
        val userMessage = vpnError.getUserMessage()

        assertTrue(userMessage.contains("Auth error"))
        assertFalse(userMessage.contains("at com.multiregionvpn"))
    }
}
