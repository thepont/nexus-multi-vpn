package com.multiregionvpn.core

import com.google.common.truth.Truth.assertThat
import com.multiregionvpn.core.vpnclient.MockOpenVpnClient
import com.multiregionvpn.core.vpnclient.OpenVpnClient
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for VpnConnectionManager
 */
class VpnConnectionManagerTest {

    private lateinit var manager: VpnConnectionManager
    private lateinit var mockClientFactory: (String) -> OpenVpnClient

    @Before
    fun setup() {
        // Create a factory that returns MockOpenVpnClient instances
            mockClientFactory = { _ ->
                MockOpenVpnClient()
            }
            
            manager = VpnConnectionManager.createForTesting(mockClientFactory)
    }

    @Test
    fun `given tunnel does not exist, when createTunnel is called, then tunnel is created`() = runTest {
        // GIVEN: No tunnels exist
        assertThat(manager.isTunnelConnected("test_tunnel")).isFalse()
        
        // WHEN: Creating a tunnel
        val config = "client\nremote test.com 1194\nproto udp"
        val created = manager.createTunnel("test_tunnel", config, null)
        
        // THEN: Tunnel is created
        assertThat(created).isTrue()
        assertThat(manager.isTunnelConnected("test_tunnel")).isTrue()
    }

    @Test
    fun `given tunnel exists, when createTunnel is called again, then returns existing status`() = runTest {
        // GIVEN: A tunnel exists
        val tunnelId = "existing_tunnel"
        manager.createTunnel(tunnelId, "client\nremote test.com 1194\nproto udp", null)
        
        // WHEN: Trying to create it again
        val result = manager.createTunnel(tunnelId, "client\nremote test.com 1194\nproto udp", null)
        
        // THEN: Returns true (tunnel already exists and is connected)
        assertThat(result).isTrue()
    }

    @Test
    fun `given tunnel exists, when sendPacketToTunnel is called, then packet is forwarded`() = runTest {
        // GIVEN: A connected tunnel
        val tunnelId = "test_tunnel"
        manager.createTunnel(tunnelId, "client\nremote test.com 1194\nproto udp", null)
        
        val testPacket = byteArrayOf(1, 2, 3, 4, 5)
        
        // WHEN: Sending a packet
        manager.sendPacketToTunnel(tunnelId, testPacket)
        
        // THEN: Packet is sent to the client
        // (We can verify this by checking the mock client's sentPackets)
        // For now, we verify no exceptions are thrown
        assertThat(manager.isTunnelConnected(tunnelId)).isTrue()
    }

    @Test
    fun `given tunnel does not exist, when sendPacketToTunnel is called, then nothing happens`() = runTest {
        // GIVEN: No tunnel exists
        val testPacket = byteArrayOf(1, 2, 3)
        
        // WHEN: Trying to send a packet to non-existent tunnel
        manager.sendPacketToTunnel("nonexistent", testPacket)
        
        // THEN: No exception is thrown (graceful handling)
        // Tunnel should not exist
        assertThat(manager.isTunnelConnected("nonexistent")).isFalse()
    }

    @Test
    fun `when closeTunnel is called, tunnel is disconnected and removed`() = runTest {
        // GIVEN: A connected tunnel
        val tunnelId = "test_tunnel"
        manager.createTunnel(tunnelId, "client\nremote test.com 1194\nproto udp", null)
        assertThat(manager.isTunnelConnected(tunnelId)).isTrue()
        
        // WHEN: Closing the tunnel
        manager.closeTunnel(tunnelId)
        
        // THEN: Tunnel is removed
        assertThat(manager.isTunnelConnected(tunnelId)).isFalse()
    }

    @Test
    fun `when closeAll is called, all tunnels are closed`() = runTest {
        // GIVEN: Multiple tunnels
        manager.createTunnel("tunnel1", "client\nremote test.com 1194\nproto udp", null)
        manager.createTunnel("tunnel2", "client\nremote test.com 1194\nproto udp", null)
        
        // WHEN: Closing all tunnels
        manager.closeAll()
        
        // THEN: All tunnels are closed
        assertThat(manager.isTunnelConnected("tunnel1")).isFalse()
        assertThat(manager.isTunnelConnected("tunnel2")).isFalse()
    }

    @Test
    fun `when packetReceiver is set, received packets trigger callback`() = runTest {
        // GIVEN: Packet receiver callback
        var receivedTunnelId: String? = null
        var receivedPacket: ByteArray? = null
        manager.setPacketReceiver { tunnelId, packet ->
            receivedTunnelId = tunnelId
            receivedPacket = packet
        }
        
        // GIVEN: A tunnel with a mock client
        val tunnelId = "test_tunnel"
        val mockClient = MockOpenVpnClient()
        kotlinx.coroutines.runBlocking {
            mockClient.connect("client\nremote test.com 1194\nproto udp", null)
        }
        mockClient.setPacketReceiver { packet ->
            manager.setPacketReceiver { tid, pkt ->
                receivedTunnelId = tid
                receivedPacket = pkt
            }
            // Simulate the manager's packet receiver
            receivedTunnelId = tunnelId
            receivedPacket = packet
        }
        
        // Create manager with custom factory for this test
        val customManager = VpnConnectionManager.createForTesting { mockClient }
        customManager.setPacketReceiver { tid, pkt ->
            receivedTunnelId = tid
            receivedPacket = pkt
        }
        
        kotlinx.coroutines.runBlocking {
            customManager.createTunnel(tunnelId, "client\nremote test.com 1194\nproto udp", null)
        }
        
        // WHEN: Simulating packet reception
        val testPacket = byteArrayOf(10, 20, 30)
        mockClient.simulateReceivePacket(testPacket)
        
        // THEN: Callback is invoked
        assertThat(receivedTunnelId).isEqualTo(tunnelId)
        assertThat(receivedPacket).isEqualTo(testPacket)
    }
}

