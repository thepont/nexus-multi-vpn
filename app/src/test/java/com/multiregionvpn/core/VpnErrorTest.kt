package com.multiregionvpn.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VpnErrorTest {

    @Test
    fun fromException_doesNotExposeStackTraceInDetails() {
        val exception = RuntimeException("Authentication failed due to invalid password")
        val error = VpnError.fromException(exception, tunnelId = "test_tunnel")

        assertEquals(VpnError.ErrorType.AUTHENTICATION_FAILED, error.type)
        assertEquals("Authentication failed due to invalid password", error.message)
        assertEquals("RuntimeException", error.details)
        assertEquals("test_tunnel", error.tunnelId)
    }

    @Test
    fun getUserMessage_doesNotLeakStackTraceDetails() {
        val exception = IllegalStateException("Internal database secret key state mismatch at com.example.SecretClass.line42")
        val error = VpnError.fromException(exception)

        val userMessage = error.getUserMessage()
        assertFalse(userMessage.contains("at com.example"))
        assertFalse(userMessage.contains("stackTrace"))
        assertEquals("An unexpected error occurred:\n\nInternal database secret key state mismatch at com.example.SecretClass.line42", userMessage)
    }
}
