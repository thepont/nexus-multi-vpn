package com.multiregionvpn.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnErrorSanitizationTest {

    @Test
    fun testSanitizeRedactsPassword() {
        val input = "Connection failed for user=admin password=secret123"
        val expected = "Connection failed for user=admin password=[REDACTED]"
        assertEquals(expected, VpnError.sanitize(input))
    }

    @Test
    fun testSanitizeRedactsToken() {
        val input = "Auth failed with token: abc-123-def"
        val expected = "Auth failed with token=[REDACTED]"
        assertEquals(expected, VpnError.sanitize(input))
    }

    @Test
    fun testSanitizeRedactsMultipleSecrets() {
        val input = "username: myuser, password: mypassword123, secret: topsecret"
        val expected = "username=[REDACTED], password=[REDACTED], secret=[REDACTED]"
        assertEquals(expected, VpnError.sanitize(input))
    }

    @Test
    fun testCreateSanitizesAutomatically() {
        val error = VpnError.create(
            type = VpnError.ErrorType.AUTHENTICATION_FAILED,
            message = "Login failed for username=testuser password=mypassword",
            details = "Detailed log: secret=something-private"
        )

        assertEquals("Login failed for username=[REDACTED] password=[REDACTED]", error.message)
        assertEquals("Detailed log: secret=[REDACTED]", error.details)
        assertFalse(error.message.contains("testuser"))
        assertFalse(error.message.contains("mypassword"))
        assertFalse(error.details!!.contains("something-private"))
    }

    @Test
    fun testFromExceptionSanitizesMessageAndStackTrace() {
        val exception = RuntimeException("Failed with password=12345")
        val error = VpnError.fromException(exception)

        // Use contains instead of exact match for message because e.toString() output can vary
        assertTrue("Message should contain redacted password", error.message.contains("password=[REDACTED]"))
        assertFalse("Details should not contain actual password", error.details!!.contains("12345"))
        assertEquals(VpnError.ErrorType.AUTHENTICATION_FAILED, error.type)
    }

    @Test
    fun testSanitizeHandlesNull() {
        assertEquals(null, VpnError.sanitize(null))
    }

    @Test
    fun testSanitizeCaseInsensitive() {
        val input = "PASSWORD=secret TOKEN=abc"
        val expected = "PASSWORD=[REDACTED] TOKEN=[REDACTED]"
        assertEquals(expected, VpnError.sanitize(input))
    }
}
