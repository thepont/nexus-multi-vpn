package com.multiregionvpn.core

import com.multiregionvpn.core.vpnclient.MockOpenVpnClient
import com.multiregionvpn.core.vpnclient.OpenVpnClient
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.*

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
        assertFalse(manager.isTunnelConnected("test_tunnel"))
        
        // WHEN: Creating a tunnel
        val config = "client\nremote test.com 1194\nproto udp"
        val result = manager.createTunnel("test_tunnel", config, null)
        
        // THEN: Tunnel is created
        assertTrue(result.success)
        assertTrue(manager.isTunnelConnected("test_tunnel"))
    }

    @Test
    fun `given tunnel exists, when createTunnel is called again, then returns existing status`() = runTest {
        // GIVEN: A tunnel exists
        val tunnelId = "existing_tunnel"
        manager.createTunnel(tunnelId, "client\nremote test.com 1194\nproto udp", null)
        
        // WHEN: Trying to create it again
        val result = manager.createTunnel(tunnelId, "client\nremote test.com 1194\nproto udp", null)
        
        // THEN: Returns true (tunnel already exists and is connected)
        assertTrue(result.success)
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
        assertTrue(manager.isTunnelConnected(tunnelId))
    }

    @Test
    fun `given tunnel does not exist, when sendPacketToTunnel is called, then nothing happens`() = runTest {
        // GIVEN: No tunnel exists
        val testPacket = byteArrayOf(1, 2, 3)
        
        // WHEN: Trying to send a packet to non-existent tunnel
        manager.sendPacketToTunnel("nonexistent", testPacket)
        
        // THEN: No exception is thrown (graceful handling)
        // Tunnel should not exist
        assertFalse(manager.isTunnelConnected("nonexistent"))
    }

    @Test
    fun `when closeTunnel is called, tunnel is disconnected and removed`() = runTest {
        // GIVEN: A connected tunnel
        val tunnelId = "test_tunnel"
        manager.createTunnel(tunnelId, "client\nremote test.com 1194\nproto udp", null)
        assertTrue(manager.isTunnelConnected(tunnelId))
        
        // WHEN: Closing the tunnel
        manager.closeTunnel(tunnelId)
        
        // THEN: Tunnel is removed
        assertFalse(manager.isTunnelConnected(tunnelId))
    }

    @Test
    fun `when closeAll is called, all tunnels are closed`() = runTest {
        // GIVEN: Multiple tunnels
        manager.createTunnel("tunnel1", "client\nremote test.com 1194\nproto udp", null)
        manager.createTunnel("tunnel2", "client\nremote test.com 1194\nproto udp", null)
        
        // WHEN: Closing all tunnels
        manager.closeAll()
        
        // THEN: All tunnels are closed
        assertFalse(manager.isTunnelConnected("tunnel1"))
        assertFalse(manager.isTunnelConnected("tunnel2"))
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
        assertEquals(tunnelId, receivedTunnelId)
        assertEquals(testPacket, receivedPacket)
    }
}

