package com.multiregionvpn.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VpnErrorTest {

    @Test
    fun `fromException maps authentication errors correctly`() {
        val exception = Exception("Invalid credentials provided")
        val error = VpnError.fromException(exception)

        assertThat(error.type).isEqualTo(VpnError.ErrorType.AUTHENTICATION_FAILED)
        assertThat(error.message).isEqualTo("Invalid credentials provided")
        assertThat(error.details).isEqualTo(exception.toString())
        assertThat(error.details).doesNotContain("at com.multiregionvpn")
    }

    @Test
    fun `fromException maps connection errors correctly`() {
        val exception = Exception("Connection timed out")
        val error = VpnError.fromException(exception)

        assertThat(error.type).isEqualTo(VpnError.ErrorType.CONNECTION_FAILED)
        assertThat(error.message).isEqualTo("Connection timed out")
        assertThat(error.details).isEqualTo(exception.toString())
        assertThat(error.details).doesNotContain("at com.multiregionvpn")
    }

    @Test
    fun `fromException maps config errors correctly`() {
        val exception = Exception("Failed to parse config file")
        val error = VpnError.fromException(exception)

        assertThat(error.type).isEqualTo(VpnError.ErrorType.CONFIG_ERROR)
        assertThat(error.message).isEqualTo("Failed to parse config file")
        assertThat(error.details).isEqualTo(exception.toString())
        assertThat(error.details).doesNotContain("at com.multiregionvpn")
    }

    @Test
    fun `fromException maps interface errors correctly`() {
        val exception = Exception("VPN permission denied")
        val error = VpnError.fromException(exception)

        assertThat(error.type).isEqualTo(VpnError.ErrorType.INTERFACE_ERROR)
        assertThat(error.message).isEqualTo("VPN permission denied")
        assertThat(error.details).isEqualTo(exception.toString())
        assertThat(error.details).doesNotContain("at com.multiregionvpn")
    }

    @Test
    fun `fromException maps unknown errors correctly`() {
        val exception = Exception("Something went wrong")
        val error = VpnError.fromException(exception)

        assertThat(error.type).isEqualTo(VpnError.ErrorType.UNKNOWN)
        assertThat(error.message).isEqualTo("Something went wrong")
        assertThat(error.details).isEqualTo(exception.toString())
        assertThat(error.details).doesNotContain("at com.multiregionvpn")
    }
}
