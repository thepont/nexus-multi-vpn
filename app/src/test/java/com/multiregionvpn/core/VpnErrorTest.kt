package com.multiregionvpn.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnErrorTest {

    @Test
    fun `fromException should sanitize details by excluding stack traces`() {
        val exceptionMessage = "Auth failed"
        val exception = Exception(exceptionMessage)

        val vpnError = VpnError.fromException(exception)

        // The details should not contain common stack trace markers
        val details = vpnError.details ?: ""

        assertFalse("Details should not contain stack trace 'at '", details.contains("at "))
        assertFalse("Details should not contain stack trace 'com.multiregionvpn'", details.contains("com.multiregionvpn"))

        // It should still contain the message or be null/exactly the message
        assertTrue("Details should be either null or match message", vpnError.details == null || vpnError.details == exceptionMessage)
    }

    @Test
    fun `fromException should correctly map error types`() {
        val authError = VpnError.fromException(Exception("authentication failed"))
        assertEquals(VpnError.ErrorType.AUTHENTICATION_FAILED, authError.type)

        val connError = VpnError.fromException(Exception("connection timeout"))
        assertEquals(VpnError.ErrorType.CONNECTION_FAILED, connError.type)

        val configError = VpnError.fromException(Exception("parse config failed"))
        assertEquals(VpnError.ErrorType.CONFIG_ERROR, configError.type)

        val interfaceError = VpnError.fromException(Exception("vpn permission denied"))
        assertEquals(VpnError.ErrorType.INTERFACE_ERROR, interfaceError.type)

        val unknownError = VpnError.fromException(Exception("something else happened"))
        assertEquals(VpnError.ErrorType.UNKNOWN, unknownError.type)
    }
}
