package com.multiregionvpn.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnErrorTest {

    @Test
    fun `fromException categorizes authentication errors`() {
        val exception = Exception("Authentication failed")
        val vpnError = VpnError.fromException(exception)

        assertEquals(VpnError.ErrorType.AUTHENTICATION_FAILED, vpnError.type)
        assertEquals("Authentication failed", vpnError.message)
    }

    @Test
    fun `fromException categorizes connection errors`() {
        val exception = Exception("Connection timed out")
        val vpnError = VpnError.fromException(exception)

        assertEquals(VpnError.ErrorType.CONNECTION_FAILED, vpnError.type)
        assertEquals("Connection timed out", vpnError.message)
    }

    @Test
    fun `fromException categorizes config errors`() {
        val exception = Exception("Failed to parse config")
        val vpnError = VpnError.fromException(exception)

        assertEquals(VpnError.ErrorType.CONFIG_ERROR, vpnError.type)
        assertEquals("Failed to parse config", vpnError.message)
    }

    @Test
    fun `fromException categorizes interface errors`() {
        val exception = Exception("VPN permission denied")
        val vpnError = VpnError.fromException(exception)

        assertEquals(VpnError.ErrorType.INTERFACE_ERROR, vpnError.type)
        assertEquals("VPN permission denied", vpnError.message)
    }

    @Test
    fun `fromException handles unknown errors`() {
        val exception = Exception("Some random error")
        val vpnError = VpnError.fromException(exception)

        assertEquals(VpnError.ErrorType.UNKNOWN, vpnError.type)
        assertEquals("Some random error", vpnError.message)
    }

    @Test
    fun `fromException details does not contain full stack trace`() {
        val exception = Exception("Test exception")
        val vpnError = VpnError.fromException(exception)

        // details should be e.toString(), not the full stack trace
        assertEquals("java.lang.Exception: Test exception", vpnError.details)

        // It definitely shouldn't contain multiple lines or "at com.multiregionvpn"
        assertFalse(vpnError.details!!.contains("at com.multiregionvpn"))
        assertFalse(vpnError.details!!.contains("\n"))
    }
}
