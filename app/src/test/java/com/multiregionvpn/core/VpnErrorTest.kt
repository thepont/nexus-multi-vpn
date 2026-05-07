package com.multiregionvpn.core

import org.junit.Assert.*
import org.junit.Test

class VpnErrorTest {

    @Test
    fun testFromException_categorizesAuthError() {
        val exception = Exception("Authentication failed for user")
        val vpnError = VpnError.fromException(exception)

        assertEquals(VpnError.ErrorType.AUTHENTICATION_FAILED, vpnError.type)
        assertEquals("java.lang.Exception: Authentication failed for user", vpnError.details)
        assertFalse(vpnError.details!!.contains("at com.multiregionvpn"))
    }

    @Test
    fun testFromException_categorizesConnectionError() {
        val exception = Exception("Connection timed out")
        val vpnError = VpnError.fromException(exception)

        assertEquals(VpnError.ErrorType.CONNECTION_FAILED, vpnError.type)
        assertEquals("java.lang.Exception: Connection timed out", vpnError.details)
    }

    @Test
    fun testFromException_handlesUnknownError() {
        val exception = Exception("Something went wrong")
        val vpnError = VpnError.fromException(exception)

        assertEquals(VpnError.ErrorType.UNKNOWN, vpnError.type)
        assertEquals("java.lang.Exception: Something went wrong", vpnError.details)
    }
}
