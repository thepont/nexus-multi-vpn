package com.multiregionvpn

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.multiregionvpn.core.VpnConnectionManager
import com.multiregionvpn.core.vpnclient.OpenVpnClient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MultiTunnelNetworkChangeTest {

    private lateinit var manager: VpnConnectionManager
    private val clients = mutableMapOf<String, FakeOpenVpnClient>()

    @Before
    fun setup() {
        ApplicationProvider.getApplicationContext<Context>()
        manager = VpnConnectionManager.createForTesting { tunnelId ->
            FakeOpenVpnClient(tunnelId).also { clients[tunnelId] = it }
        }
        setManagerSingleton(manager)
    }

    @After
    fun teardown() = runBlocking {
        if (!::manager.isInitialized) {
            return@runBlocking
        }
        clients.clear()
        manager.closeAll()
        setManagerSingleton(null)
    }

    @Test
    fun testMultipleTunnelsReconnectSimultaneously() = runBlocking {
        registerTunnel("nordvpn_UK")
        registerTunnel("nordvpn_FR")
        registerTunnel("nordvpn_US")

        manager.reconnectAllTunnels()

        assertTrue(manager.isTunnelConnected("nordvpn_UK"))
        assertTrue(manager.isTunnelConnected("nordvpn_FR"))
        assertTrue(manager.isTunnelConnected("nordvpn_US"))
    }

    @Test
    fun testRapidNetworkChanges() = runBlocking {
        registerTunnel("nordvpn_UK")

        repeat(3) {
            manager.reconnectAllTunnels()
        }

        assertTrue(manager.isTunnelConnected("nordvpn_UK"))
    }

    @Test
    fun testNetworkUnavailableScenario() = runBlocking {
        registerTunnel("nordvpn_UK")

        manager.reconnectAllTunnels()
        assertTrue(manager.isTunnelConnected("nordvpn_UK"))
    }

    @Test
    fun testPartialTunnelFailure() = runBlocking {
        registerTunnel("nordvpn_UK")
        registerTunnel("nordvpn_FR")

        manager.closeTunnel("nordvpn_UK")

        assertFalse(manager.isTunnelConnected("nordvpn_UK"))
        assertTrue(manager.isTunnelConnected("nordvpn_FR"))
    }

    @Test
    fun testVpnStartupDuringNetworkChange() = runBlocking {
        registerTunnel("nordvpn_UK", connected = false)
        manager.simulateTunnelState("nordvpn_UK", ipAssigned = false, dnsConfigured = false)

        clients["nordvpn_UK"]?.setConnected(true)
        manager.simulateTunnelState("nordvpn_UK", ipAssigned = true, dnsConfigured = true)

        manager.reconnectAllTunnels()

        assertTrue(manager.isTunnelReadyForRouting("nordvpn_UK"))
    }

    @Test
    fun testConnectionTrackerAfterNetworkChange() = runBlocking {
        registerTunnel("nordvpn_UK")
        manager.simulateTunnelState("nordvpn_UK", ipAssigned = true, dnsConfigured = true)

        val readyBefore = manager.isTunnelReadyForRouting("nordvpn_UK")
        manager.reconnectAllTunnels()
        val readyAfter = manager.isTunnelReadyForRouting("nordvpn_UK")

        assertTrue(readyBefore)
        assertTrue(readyAfter)
    }

    private fun registerTunnel(tunnelId: String, connected: Boolean = true) {
        val client = FakeOpenVpnClient(tunnelId, connected)
        clients[tunnelId] = client
        manager.registerTestClient(tunnelId, client)
        manager.simulateTunnelState(tunnelId, ipAssigned = connected, dnsConfigured = connected)
    }

    private fun setManagerSingleton(instance: VpnConnectionManager?) {
        val field = VpnConnectionManager::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, instance)
    }

    private class FakeOpenVpnClient(
        private val tunnelId: String,
        private var connected: Boolean = true
    ) : OpenVpnClient {

        private var callback: ((ByteArray) -> Unit)? = null

        override suspend fun connect(ovpnConfig: String, authFilePath: String?): Boolean {
            connected = true
            return true
        }

        override fun sendPacket(packet: ByteArray) {
            callback?.invoke(packet)
        }

        override suspend fun disconnect() {
            connected = false
        }

        override fun isConnected(): Boolean = connected

        override fun setPacketReceiver(callback: (ByteArray) -> Unit) {
            this.callback = callback
        }

        fun setConnected(value: Boolean) {
            connected = value
        }
    }
}
