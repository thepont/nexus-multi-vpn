package com.multiregionvpn.core

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VpnErrorTest {

    @Test
    fun `fromException should not leak stack trace in details`() {
        val exception = RuntimeException("Sensitive error message")
        val tunnelId = "test-tunnel"

        val error = VpnError.fromException(exception, tunnelId)

        println("Error message: '${error.message}'")
        println("Error details: '${error.details}'")

        assertEquals("Sensitive error message", error.message)
        assertEquals("Sensitive error message", error.details)
        assertFalse(error.details?.contains("at com.multiregionvpn") ?: true, "Details should not contain stack trace")
    }

    @Test
    fun `fromException should map authentication errors correctly`() {
        val exception = RuntimeException("Authentication failed: invalid credentials")
        val error = VpnError.fromException(exception)

        assertEquals(VpnError.ErrorType.AUTHENTICATION_FAILED, error.type)
    }

    @Test
    fun `fromException should map connection errors correctly`() {
        val exception = RuntimeException("Connection timeout to server")
        val error = VpnError.fromException(exception)

        assertEquals(VpnError.ErrorType.CONNECTION_FAILED, error.type)
    }

    @Test
    fun `fromException should map config errors correctly`() {
        val exception = RuntimeException("Failed to parse OpenVPN config")
        val error = VpnError.fromException(exception)

        assertEquals(VpnError.ErrorType.CONFIG_ERROR, error.type)
    }

    @Test
    fun `fromException should map interface errors correctly`() {
        val exception = RuntimeException("VPN permission denied")
        val error = VpnError.fromException(exception)

        assertEquals(VpnError.ErrorType.INTERFACE_ERROR, error.type)
    }
}
