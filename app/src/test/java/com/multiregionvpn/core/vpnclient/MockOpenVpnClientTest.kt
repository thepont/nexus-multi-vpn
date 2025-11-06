package com.multiregionvpn.core.vpnclient

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for MockOpenVpnClient
 */
class MockOpenVpnClientTest {

    private lateinit var client: MockOpenVpnClient

    @Before
    fun setup() {
        client = MockOpenVpnClient()
    }

    @Test
    fun `given valid config, when connect is called, then connection succeeds`() = runTest {
        // GIVEN: A valid OpenVPN configuration
        val validConfig = """
            client
            remote uk1234.nordvpn.com 1194
            proto udp
            dev tun
        """.trimIndent()
        
        // WHEN: Connecting
        val result = client.connect(validConfig, null)
        
        // THEN: Connection succeeds
        assertThat(result).isTrue()
        assertThat(client.isConnected()).isTrue()
        assertThat(client.connectionAttempts).isEqualTo(1)
    }

    @Test
    fun `given invalid config, when connect is called, then connection fails`() = runTest {
        // GIVEN: An invalid OpenVPN configuration (missing required fields)
        val invalidConfig = "client"
        
        // WHEN: Connecting
        val result = client.connect(invalidConfig, null)
        
        // THEN: Connection fails
        assertThat(result).isFalse()
        assertThat(client.isConnected()).isFalse()
    }

    @Test
    fun `when sendPacket is called while connected, packet is tracked`() = runTest {
        // GIVEN: Connected client
        val validConfig = """
            client
            remote uk1234.nordvpn.com 1194
            proto udp
        """.trimIndent()
        client.connect(validConfig, null)
        
        val testPacket = byteArrayOf(1, 2, 3, 4, 5)
        
        // WHEN: Sending a packet
        client.sendPacket(testPacket)
        
        // THEN: Packet is tracked
        assertThat(client.sentPackets).hasSize(1)
        assertThat(client.sentPackets[0]).isEqualTo(testPacket)
    }

    @Test
    fun `when sendPacket is called while not connected, packet is ignored`() {
        // GIVEN: Not connected
        val testPacket = byteArrayOf(1, 2, 3)
        
        // WHEN: Sending a packet
        client.sendPacket(testPacket)
        
        // THEN: Packet is not tracked
        assertThat(client.sentPackets).isEmpty()
    }

    @Test
    fun `when disconnect is called, connection is closed`() = runTest {
        // GIVEN: Connected client
        val validConfig = """
            client
            remote uk1234.nordvpn.com 1194
            proto udp
        """.trimIndent()
        client.connect(validConfig, null)
        assertThat(client.isConnected()).isTrue()
        
        // WHEN: Disconnecting
        client.disconnect()
        
        // THEN: Connection is closed
        assertThat(client.isConnected()).isFalse()
        assertThat(client.sentPackets).isEmpty()
    }

    @Test
    fun `when packetReceiver is set and packet is simulated, callback is invoked`() {
        // GIVEN: Packet receiver callback
        var receivedPacket: ByteArray? = null
        client.setPacketReceiver { packet ->
            receivedPacket = packet
        }
        
        val testPacket = byteArrayOf(10, 20, 30)
        
        // WHEN: Simulating packet reception
        client.simulateReceivePacket(testPacket)
        
        // THEN: Callback is invoked
        assertThat(receivedPacket).isNotNull()
        assertThat(receivedPacket).isEqualTo(testPacket)
        assertThat(client.receivedPackets).hasSize(1)
    }
}


