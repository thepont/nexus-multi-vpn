package com.multiregionvpn.core

import android.content.Context
import android.net.VpnService
import com.google.common.truth.Truth.assertThat
import com.multiregionvpn.data.database.AppRule
import com.multiregionvpn.data.database.VpnConfig
import com.multiregionvpn.data.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import java.io.FileOutputStream
import java.net.InetAddress

/**
 * Unit tests for PacketRouter packet forwarding logic
 */
class PacketRouterTest {

    private lateinit var mockContext: Context
    private lateinit var mockVpnService: VpnService
    private lateinit var mockSettingsRepo: SettingsRepository
    private lateinit var mockVpnConnectionManager: VpnConnectionManager
    private lateinit var mockVpnOutput: FileOutputStream
    private lateinit var writtenPackets: MutableList<ByteArray>
    private lateinit var packetRouter: PacketRouter
    private lateinit var mockConnectivityManager: android.net.ConnectivityManager
    private lateinit var mockPackageManager: android.content.pm.PackageManager
    
    @Before
    fun setup() {
        mockContext = mockk()
        mockVpnService = mockk()
        mockSettingsRepo = mockk()
        mockVpnConnectionManager = mockk()
        writtenPackets = mutableListOf()
        
        // Create a mock FileOutputStream that captures written data
        mockVpnOutput = mockk(relaxed = true)
        val writeSlot = slot<ByteArray>()
        every { mockVpnOutput.write(capture(writeSlot)) } answers {
            writtenPackets.add(writeSlot.captured)
        }
        every { mockVpnOutput.flush() } returns Unit
        
        // Mock package manager
        mockPackageManager = mockk()
        every { mockContext.packageManager } returns mockPackageManager
        every { mockPackageManager.getPackagesForUid(any()) } returns arrayOf("com.test.app")
        
        // Mock ConnectivityManager (required by PacketRouter)
        mockConnectivityManager = mockk()
        every { mockContext.getSystemService(Context.CONNECTIVITY_SERVICE) } returns mockConnectivityManager
        // Default PacketRouter instance (tests can replace with custom tracker)
        packetRouter = createPacketRouter()
    }

    private fun createPacketRouter(
        connectionTracker: ConnectionTracker? = null
    ): PacketRouter {
        return PacketRouter(
            mockContext,
            mockSettingsRepo,
            mockVpnService,
            mockVpnConnectionManager,
            mockVpnOutput,
            connectionTracker
        )
    }

    @Test
    fun `when no mapping exists then packet is written to direct internet`() {
        writtenPackets.clear()
        val tracker = mockk<ConnectionTracker>(relaxUnitFun = true)
        every { tracker.getTunnelForDestination(any()) } returns null
        every { tracker.getTunnelForIp(any()) } returns null
        every { tracker.lookupConnection(any(), any()) } returns null
        every { tracker.getRegisteredPackages() } returns emptySet()

        val router = createPacketRouter(tracker)
        every { mockVpnConnectionManager.getAllTunnelIds() } returns emptyList()

        val packet = createTestTcpPacket(
            srcIp = "192.168.1.100",
            destIp = "8.8.8.8",
            srcPort = 12345,
            destPort = 80
        )

        router.routePacket(packet)

        assertThat(writtenPackets).containsExactly(packet)
        verify(exactly = 0) { mockVpnConnectionManager.sendPacketToTunnel(any(), any()) }
    }

    @Test
    fun `when vpn config missing after rule lookup then falls back to direct internet`() {
        writtenPackets.clear()
        val tracker = mockk<ConnectionTracker>(relaxUnitFun = true)
        every { tracker.getTunnelForDestination(any()) } returns null
        every { tracker.getTunnelForIp(any()) } returns null
        every { tracker.lookupConnection(any(), any()) } returns ConnectionTracker.ConnectionInfo(
            uid = 20202,
            tunnelId = null
        )

        val router = createPacketRouter(tracker)
        every { mockPackageManager.getPackagesForUid(20202) } returns arrayOf("com.example.missing")

        val appRule = AppRule(packageName = "com.example.missing", vpnConfigId = "vpn-missing-id")
        coEvery { mockSettingsRepo.getAppRuleByPackageName("com.example.missing") } returns appRule
        coEvery { mockSettingsRepo.getVpnConfigById("vpn-missing-id") } returns null

        val packet = createTestTcpPacket(
            srcIp = "192.168.1.50",
            destIp = "8.8.4.4",
            srcPort = 15000,
            destPort = 443
        )

        router.routePacket(packet)

        assertThat(writtenPackets).containsExactly(packet)
        verify(exactly = 0) { mockVpnConnectionManager.sendPacketToTunnel(any(), any()) }
        coVerify(exactly = 1) { mockSettingsRepo.getAppRuleByPackageName("com.example.missing") }
        coVerify(exactly = 1) { mockSettingsRepo.getVpnConfigById("vpn-missing-id") }
    }

    @Test
    fun `given source IP mapped to tunnel when packet routed then uses mapped tunnel`() {
        val tracker = mockk<ConnectionTracker>()
        every { tracker.getTunnelForIp(any()) } answers {
            val ip = firstArg<InetAddress>().hostAddress
            if (ip == "10.31.0.2") "local-test_UK" else null
        }
        every { tracker.getTunnelForDestination(any()) } returns null
        every { tracker.lookupConnection(any(), any()) } returns null

        val router = createPacketRouter(tracker)
        every { mockVpnConnectionManager.sendPacketToTunnel(any(), any()) } returns Unit

        val packet = createTestTcpPacket(
            srcIp = "10.31.0.2",
            destIp = "10.1.0.100",
            srcPort = 44374,
            destPort = 80
        )

        router.routePacket(packet)

        verify(exactly = 1) {
            mockVpnConnectionManager.sendPacketToTunnel("local-test_UK", any())
        }
    }

    @Test
    fun `given connection tracker maps UID to tunnel when packet routed then uses tunnel`() {
        val tracker = mockk<ConnectionTracker>()
        every { tracker.getTunnelForIp(any()) } returns null
        every { tracker.getTunnelForDestination(any()) } returns null
        every { tracker.lookupConnection(any(), any()) } returns ConnectionTracker.ConnectionInfo(
            uid = 10235,
            tunnelId = "local-test_UK"
        )
        every { tracker.getRegisteredPackages() } returns setOf("com.example.diagnostic.uk")
        every { tracker.getUidForPackage("com.example.diagnostic.uk") } returns 10235

        val router = createPacketRouter(tracker)
        every { mockVpnConnectionManager.sendPacketToTunnel(any(), any()) } returns Unit

        val packet = createTestTcpPacket(
            srcIp = "10.0.2.15",
            destIp = "10.1.0.100",
            srcPort = 50000,
            destPort = 80
        )

        router.routePacket(packet)

        verify {
            mockVpnConnectionManager.sendPacketToTunnel("local-test_UK", any())
        }
    }

    @Test
    fun `dns query routes to first connected tunnel`() {
        val tracker = mockk<ConnectionTracker>(relaxUnitFun = true)
        every { tracker.getTunnelForDestination(any()) } returns null
        every { tracker.getTunnelForIp(any()) } returns null
        every { tracker.lookupConnection(any(), any()) } returns null
        every { tracker.getRegisteredPackages() } returns emptySet()

        val router = createPacketRouter(tracker)
        every { mockVpnConnectionManager.getAllTunnelIds() } returns listOf("local-test_UK", "local-test_FR")
        every { mockVpnConnectionManager.isTunnelConnected("local-test_UK") } returns true
        every { mockVpnConnectionManager.isTunnelConnected("local-test_FR") } returns false
        every { mockVpnConnectionManager.sendPacketToTunnel(any(), any()) } returns Unit

        val packet = createTestUdpPacket(
            srcIp = "10.100.0.2",
            destIp = "1.1.1.1",
            srcPort = 53000,
            destPort = 53
        )

        router.routePacket(packet)

        verify {
            mockVpnConnectionManager.sendPacketToTunnel("local-test_UK", packet)
            tracker.registerConnection(any(), 53000, -1, "local-test_UK")
        }
        verify(exactly = 0) { mockVpnOutput.write(any<ByteArray>()) }
    }

    @Test
    fun `dns query falls back to direct internet when no tunnel connected`() {
        writtenPackets.clear()
        val tracker = mockk<ConnectionTracker>(relaxUnitFun = true)
        every { tracker.getTunnelForDestination(any()) } returns null
        every { tracker.getTunnelForIp(any()) } returns null
        every { tracker.lookupConnection(any(), any()) } returns null
        every { tracker.getRegisteredPackages() } returns emptySet()

        val router = createPacketRouter(tracker)
        every { mockVpnConnectionManager.getAllTunnelIds() } returns listOf("local-test_UK")
        every { mockVpnConnectionManager.isTunnelConnected("local-test_UK") } returns false

        val packet = createTestUdpPacket(
            srcIp = "10.100.0.2",
            destIp = "1.0.0.1",
            srcPort = 5353,
            destPort = 53
        )

        router.routePacket(packet)

        assertThat(writtenPackets).hasSize(1)
        assertThat(writtenPackets[0]).isEqualTo(packet)
        verify(exactly = 0) { mockVpnConnectionManager.sendPacketToTunnel(any(), any()) }
        verify(exactly = 0) { tracker.registerConnection(any(), any(), any(), any()) }
    }

    @Test
    fun `registered package fallback routes packet through resolved tunnel`() {
        writtenPackets.clear()
        val tracker = mockk<ConnectionTracker>(relaxUnitFun = true)
        every { tracker.getTunnelForDestination(any()) } returns null
        every { tracker.getTunnelForIp(any()) } returns null
        every { tracker.lookupConnection(any(), any()) } returns null
        every { tracker.getRegisteredPackages() } returns setOf("com.example.diagnostic.uk")
        val registerPackageCalls = mutableListOf<String>()
        every { tracker.registerPackage(any()) } answers {
            val pkg = firstArg<String>()
            registerPackageCalls.add(pkg)
            45678
        }
        every { tracker.getUidForPackage("com.example.diagnostic.uk") } returns 45678
        every { tracker.getTunnelIdForUid(45678) } returns null

        val router = createPacketRouter(tracker)
        every { mockVpnConnectionManager.getAllTunnelIds() } returns emptyList()

        val setTunnelCalls = mutableListOf<Pair<Int, String?>>()
        every { tracker.setUidToTunnel(any(), any()) } answers {
            setTunnelCalls.add(firstArg<Int>() to secondArg())
            Unit
        }

        val registeredConnections = mutableListOf<Triple<InetAddress, Int, String?>>()
        every { tracker.registerConnection(any(), any(), any(), any()) } answers {
            registeredConnections.add(Triple(firstArg(), secondArg(), arg(3)))
            Unit
        }

        val tunnelSends = mutableListOf<Pair<String, ByteArray>>()
        every { mockVpnConnectionManager.sendPacketToTunnel(any(), any()) } answers {
            tunnelSends.add(firstArg<String>() to secondArg())
            Unit
        }

        every {
            mockConnectivityManager.getConnectionOwnerUid(any(), any(), any())
        } returns 45678
        every { mockPackageManager.getPackagesForUid(45678) } returns arrayOf("com.example.diagnostic.uk")

        val vpnConfig = VpnConfig(
            id = "vpn-uk-id",
            name = "UK VPN",
            regionId = "UK",
            templateId = "nordvpn",
            serverHostname = "uk.nordvpn.com"
        )
        val appRule = AppRule("com.example.diagnostic.uk", "vpn-uk-id")
        coEvery { mockSettingsRepo.getAppRuleByPackageName("com.example.diagnostic.uk") } returns appRule
        coEvery { mockSettingsRepo.getVpnConfigById("vpn-uk-id") } returns vpnConfig

        val packet = createTestTcpPacket(
            srcIp = "192.168.1.100",
            destIp = "10.1.0.100",
            srcPort = 40000,
            destPort = 80
        )

        router.routePacket(packet)

        coVerify(exactly = 1) { mockSettingsRepo.getAppRuleByPackageName("com.example.diagnostic.uk") }
        coVerify(exactly = 1) { mockSettingsRepo.getVpnConfigById("vpn-uk-id") }

        assertThat(setTunnelCalls).containsExactly(45678 to "nordvpn_UK")
        assertThat(registeredConnections).hasSize(1)
        assertThat(registeredConnections[0].second).isEqualTo(40000)
        assertThat(registeredConnections[0].third).isEqualTo("nordvpn_UK")
        assertThat(tunnelSends).hasSize(1)
        assertThat(tunnelSends[0].first).isEqualTo("nordvpn_UK")
        assertThat(tunnelSends[0].second).isEqualTo(packet)
        assertThat(writtenPackets).isEmpty()
    }

    @Test
    fun `exceptions fetching registered packages lead to direct fallback`() {
        writtenPackets.clear()
        val tracker = mockk<ConnectionTracker>(relaxUnitFun = true)
        every { tracker.getTunnelForDestination(any()) } returns null
        every { tracker.getTunnelForIp(any()) } returns null
        every { tracker.lookupConnection(any(), any()) } returns null
        every { tracker.getRegisteredPackages() } throws RuntimeException("registry failure")

        val router = createPacketRouter(tracker)

        val packet = createTestTcpPacket(
            srcIp = "192.168.1.101",
            destIp = "8.8.4.4",
            srcPort = 41000,
            destPort = 443
        )

        router.routePacket(packet)

        assertThat(writtenPackets).hasSize(1)
        assertThat(writtenPackets[0]).isEqualTo(packet)
        verify(exactly = 0) { mockVpnConnectionManager.sendPacketToTunnel(any(), any()) }
    }

    @Test
    fun `given destination route mapping when packet routed then uses mapped tunnel`() {
        val tracker = mockk<ConnectionTracker>()
        every { tracker.getTunnelForIp(any()) } returns null
        every { tracker.getTunnelForDestination(any()) } answers {
            val dest = firstArg<InetAddress>().hostAddress
            if (dest == "10.1.0.100") "local-test_UK" else null
        }
        every { tracker.lookupConnection(any(), any()) } returns null

        val router = createPacketRouter(tracker)
        every { mockVpnConnectionManager.sendPacketToTunnel(any(), any()) } returns Unit

        val packet = createTestTcpPacket(
            srcIp = "10.0.2.15",
            destIp = "10.1.0.100",
            srcPort = 40000,
            destPort = 80
        )

        router.routePacket(packet)

        verify(exactly = 1) {
            mockVpnConnectionManager.sendPacketToTunnel("local-test_UK", any())
        }
    }

    /**
     * Creates a minimal valid IPv4 TCP packet for testing
     */
    private fun createTestTcpPacket(
        srcIp: String,
        destIp: String,
        srcPort: Int,
        destPort: Int
    ): ByteArray {
        val buffer = java.nio.ByteBuffer.allocate(60) // Minimum IPv4 + TCP header
            .order(java.nio.ByteOrder.BIG_ENDIAN)
        
        // IPv4 Header (20 bytes)
        buffer.put(0x45.toByte()) // Version (4) + IHL (5)
        buffer.put(0x00.toByte()) // DSCP + ECN
        buffer.putShort(60) // Total Length
        buffer.putShort(0) // Identification
        buffer.putShort(0x4000.toShort()) // Flags + Fragment Offset
        buffer.put(64.toByte()) // TTL
        buffer.put(6.toByte()) // Protocol (TCP)
        buffer.putShort(0) // Header Checksum (0 for testing)
        
        // Source IP
        val srcBytes = InetAddress.getByName(srcIp).address
        buffer.put(srcBytes)
        
        // Destination IP
        val destBytes = InetAddress.getByName(destIp).address
        buffer.put(destBytes)
        
        // TCP Header (20 bytes minimum)
        buffer.putShort(srcPort.toShort()) // Source Port
        buffer.putShort(destPort.toShort()) // Destination Port
        buffer.putInt(0) // Sequence Number
        buffer.putInt(0) // Acknowledgement Number
        buffer.putShort(0x5000.toShort()) // Data Offset (5 * 4 = 20) + Flags
        buffer.putShort(0xFFFF.toShort()) // Window Size
        buffer.putShort(0) // Checksum
        buffer.putShort(0) // Urgent Pointer
        
        // Padding to minimum 60 bytes
        while (buffer.position() < 60) {
            buffer.put(0.toByte())
        }
        
        return buffer.array()
    }

    private fun createTestUdpPacket(
        srcIp: String,
        destIp: String,
        srcPort: Int,
        destPort: Int
    ): ByteArray {
        val buffer = java.nio.ByteBuffer.allocate(42)
            .order(java.nio.ByteOrder.BIG_ENDIAN)

        buffer.put(0x45.toByte())
        buffer.put(0x00.toByte())
        buffer.putShort(42)
        buffer.putShort(0)
        buffer.putShort(0x4000.toShort())
        buffer.put(64.toByte())
        buffer.put(17.toByte())
        buffer.putShort(0)

        buffer.put(InetAddress.getByName(srcIp).address)
        buffer.put(InetAddress.getByName(destIp).address)

        buffer.putShort(srcPort.toShort())
        buffer.putShort(destPort.toShort())
        buffer.putShort(8.toShort())
        buffer.putShort(0)

        while (buffer.position() < 42) {
            buffer.put(0.toByte())
        }

        return buffer.array()
    }
}

