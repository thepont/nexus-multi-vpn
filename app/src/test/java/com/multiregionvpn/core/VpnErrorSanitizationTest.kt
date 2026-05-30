package com.multiregionvpn.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnErrorSanitizationTest {

    @Test
    fun testSanitizeRedactsPassword() {
        val input = "Connection failed for user=admin password=secret123"
        val expected = "password=[REDACTED]"
        assertTrue(VpnError.sanitize(input)!!.contains(expected))
    }

    @Test
    fun testSanitizeRedactsToken() {
        val input = "Auth failed with token: abc-123-def"
        val expected = "token=[REDACTED]"
        assertTrue(VpnError.sanitize(input)!!.contains(expected))
    }

    @Test
    fun testSanitizeRedactsMultipleSecrets() {
        val input = "username: myuser, password: mypassword123, secret: topsecret"
        val sanitized = VpnError.sanitize(input)!!
        assertTrue(sanitized.contains("username=[REDACTED]"))
        assertTrue(sanitized.contains("password=[REDACTED]"))
        assertTrue(sanitized.contains("secret=[REDACTED]"))
    }

    @Test
    fun testCreateSanitizesAutomatically() {
        val error = VpnError.create(
            type = VpnError.ErrorType.AUTHENTICATION_FAILED,
            message = "Login failed for username=testuser password=mypassword",
            details = "Detailed log: secret=something-private"
        )

        assertTrue(error.message.contains("username=[REDACTED]"))
        assertTrue(error.message.contains("password=[REDACTED]"))
        assertTrue(error.details!!.contains("secret=[REDACTED]"))
        assertFalse(error.message.contains("testuser"))
        assertFalse(error.message.contains("mypassword"))
        assertFalse(error.details!!.contains("something-private"))
    }

    @Test
    fun testFromExceptionSanitizesMessageAndStackTrace() {
        val exception = RuntimeException("Failed with password=12345")
        val error = VpnError.fromException(exception)

        assertTrue(error.message.contains("password=[REDACTED]"))
        assertFalse(error.details!!.contains("12345"))
        assertEquals(VpnError.ErrorType.AUTHENTICATION_FAILED, error.type)
    }

    @Test
    fun testSanitizeHandlesNull() {
        assertEquals(null, VpnError.sanitize(null))
    }

    @Test
    fun testSanitizeCaseInsensitive() {
        val input = "PASSWORD=secret TOKEN=abc"
        val sanitized = VpnError.sanitize(input)!!
        assertTrue(sanitized.contains("PASSWORD=[REDACTED]"))
        assertTrue(sanitized.contains("TOKEN=[REDACTED]"))
    }
}
