package com.multiregionvpn.core

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import java.net.InetAddress

class ConnectionTrackerTest {

    private lateinit var packageManager: PackageManager
    private lateinit var tracker: ConnectionTracker

    @Before
    fun setUp() {
        packageManager = Mockito.mock(PackageManager::class.java)
        tracker = ConnectionTracker(Mockito.mock(Context::class.java), packageManager)
    }

    @Test
    fun mapsPackageToTunnel() {
        mockApp("com.example.app", uid = 10101)

        val registered = tracker.setPackageToTunnel("com.example.app", "nordvpn_UK")

        assertThat(registered).isTrue()
        assertThat(tracker.getCurrentPackageMappings())
            .containsEntry("com.example.app", "nordvpn_UK")
    }

    @Test
    fun clearPackageRemovesMapping() {
        mockApp("com.example.app", uid = 10101)
        tracker.setPackageToTunnel("com.example.app", "nordvpn_UK")

        tracker.clearPackage("com.example.app")

        assertThat(tracker.getCurrentPackageMappings()).isEmpty()
    }

    @Test
    fun clearAllMappingsResetsState() {
        mockApp("com.example.app", uid = 10101)
        mockApp("com.example.second", uid = 10102)
        tracker.setPackageToTunnel("com.example.app", "nordvpn_UK")
        tracker.setPackageToTunnel("com.example.second", "nordvpn_FR")

        tracker.clearAllMappings()

        assertThat(tracker.getCurrentPackageMappings()).isEmpty()
    }

    @Test
    fun addRouteMapsDestination() {
        val address = InetAddress.getByName("10.1.0.0")
        tracker.addRouteForTunnel("local-test_UK", address, 24)

        val matchedTunnel = tracker.getTunnelForDestination(InetAddress.getByName("10.1.0.123"))

        assertThat(matchedTunnel).isEqualTo("local-test_UK")
    }

    @Test
    fun removeRoutesForTunnelClearsMapping() {
        val address = InetAddress.getByName("10.2.0.0")
        tracker.addRouteForTunnel("local-test_FR", address, 24)

        tracker.removeRoutesForTunnel("local-test_FR")

        val matchedTunnel = tracker.getTunnelForDestination(InetAddress.getByName("10.2.0.42"))

        assertThat(matchedTunnel).isNull()
    }

    @Test
    fun lookupConnectionWithFallbackRegistersConnection() {
        mockApp("com.example.app", uid = 10101)
        tracker.setPackageToTunnel("com.example.app", "nordvpn_UK")

        val srcIp = InetAddress.getByName("192.168.0.10")
        val result = tracker.lookupConnectionWithFallback(srcIp, 443, "com.example.app")

        assertThat(result).isNotNull()
        assertThat(result!!.uid).isEqualTo(10101)
        assertThat(result.tunnelId).isEqualTo("nordvpn_UK")
        assertThat(tracker.lookupConnection(srcIp, 443)?.tunnelId).isEqualTo("nordvpn_UK")
    }

    @Test
    fun ipMappedToMultipleTunnelsReturnsNull() {
        val ip = InetAddress.getByName("10.10.10.10")

        tracker.setTunnelForIp(ip, "nordvpn_UK")
        assertThat(tracker.getTunnelForIp(ip)).isEqualTo("nordvpn_UK")

        tracker.setTunnelForIp(ip, "nordvpn_FR")
        assertThat(tracker.getTunnelForIp(ip)).isNull()

        tracker.removeTunnelForIp(ip)
        assertThat(tracker.getTunnelForIp(ip)).isNull()
    }

    @Test
    fun clearConnectionsForUidRemovesEntries() {
        mockApp("com.example.app", uid = 10101)
        tracker.setPackageToTunnel("com.example.app", "nordvpn_UK")

        val ip = InetAddress.getByName("192.168.1.20")
        tracker.registerConnection(ip, 8080, 10101, "nordvpn_UK")

        assertThat(tracker.lookupConnection(ip, 8080)).isNotNull()

        tracker.clearConnectionsForUid(10101)

        assertThat(tracker.lookupConnection(ip, 8080)).isNull()
        assertThat(tracker.getTunnelForUid(10101)).isNull()
        assertThat(tracker.getUidForPackage("com.example.app")).isEqualTo(10101)
    }

    @Test
    fun cleanupOldEntriesRemovesStaleConnections() {
        val tableField = ConnectionTracker::class.java.getDeclaredField("connectionTable")
        tableField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val table = tableField.get(tracker) as MutableMap<String, ConnectionTracker.ConnectionInfo>

        table["198.51.100.1:80"] = ConnectionTracker.ConnectionInfo(
            uid = 100,
            tunnelId = "nordvpn_UK",
            timestamp = System.currentTimeMillis() - 600_000L
        )
        table["198.51.100.2:80"] = ConnectionTracker.ConnectionInfo(
            uid = 101,
            tunnelId = "nordvpn_FR",
            timestamp = System.currentTimeMillis()
        )

        val cleanupMethod = ConnectionTracker::class.java.getDeclaredMethod("cleanupOldEntries")
        cleanupMethod.isAccessible = true
        cleanupMethod.invoke(tracker)

        assertThat(table.keys).containsExactly("198.51.100.2:80")
    }

    @Test
    fun `lookupConnectionWithFallback registers new mapping using package information`() {
        mockApp("com.example.app", uid = 10101)
        tracker.setPackageToTunnel("com.example.app", "nordvpn_UK")
        val srcIp = InetAddress.getByName("192.0.2.5")

        val info = tracker.lookupConnectionWithFallback(srcIp, 44321, "com.example.app")

        assertThat(info).isNotNull()
        assertThat(info!!.uid).isEqualTo(10101)
        assertThat(info.tunnelId).isEqualTo("nordvpn_UK")
        assertThat(tracker.lookupConnection(srcIp, 44321)).isEqualTo(info)
    }

    @Test
    fun `ip mapping returns single tunnel until collisions occur`() {
        val ip = InetAddress.getByName("203.0.113.11")

        tracker.setTunnelForIp(ip, "local-test_UK")
        assertThat(tracker.getTunnelForIp(ip)).isEqualTo("local-test_UK")

        tracker.setTunnelForIp(ip, "local-test_FR")
        assertThat(tracker.getTunnelForIp(ip)).isNull()

        tracker.removeTunnelForIp(ip)
        assertThat(tracker.getTunnelForIp(ip)).isNull()
    }

    @Test
    fun `stale connection entries are purged on lookup`() {
        val srcIp = InetAddress.getByName("198.51.100.24")
        val key = "${srcIp.hostAddress}:55000"
        val staleTimestamp = System.currentTimeMillis() - getEntryTimeoutMs() - 1
        putConnectionEntry(key, ConnectionTracker.ConnectionInfo(uid = 1234, tunnelId = "local-test_UK", timestamp = staleTimestamp))

        val lookup = tracker.lookupConnection(srcIp, 55000)

        assertThat(lookup).isNull()
        // ensure entry removed
        assertThat(getConnectionTable()).doesNotContainKey(key)
    }

    @Test
    fun `clearConnectionsForUid removes mappings`() {
        tracker.setUidToTunnel(9001, "local-test_UK")
        val srcIp = InetAddress.getByName("10.10.10.10")
        tracker.registerConnection(srcIp, 60000, 9001, "local-test_UK")

        tracker.clearConnectionsForUid(9001)

        assertThat(tracker.getTunnelForUid(9001)).isNull()
        assertThat(tracker.lookupConnection(srcIp, 60000)).isNull()
    }

    @Test
    fun `clearConnectionsForPackage removes uid and rules`() {
        mockApp("com.example.clear", uid = 20202)
        tracker.setPackageToTunnel("com.example.clear", "nordvpn_FR")
        val srcIp = InetAddress.getByName("10.0.0.20")
        tracker.registerConnection(srcIp, 51000, 20202, "nordvpn_FR")

        tracker.clearConnectionsForPackage("com.example.clear")

        assertThat(tracker.getUidForPackage("com.example.clear")).isNull()
        assertThat(tracker.lookupConnection(srcIp, 51000)).isNull()
        assertThat(tracker.getCurrentPackageMappings()).doesNotContainKey("com.example.clear")
    }

    private fun mockApp(packageName: String, uid: Int) {
        val appInfo = ApplicationInfo().apply {
            this.packageName = packageName
            this.uid = uid
        }
        `when`(packageManager.getApplicationInfo(packageName, 0)).thenReturn(appInfo)
    }

    @Suppress("UNCHECKED_CAST")
    private fun getConnectionTable(): MutableMap<String, ConnectionTracker.ConnectionInfo> {
        val field = ConnectionTracker::class.java.getDeclaredField("connectionTable")
        field.isAccessible = true
        return field.get(tracker) as MutableMap<String, ConnectionTracker.ConnectionInfo>
    }

    private fun putConnectionEntry(key: String, info: ConnectionTracker.ConnectionInfo) {
        getConnectionTable()[key] = info
    }

    private fun getEntryTimeoutMs(): Long {
        val field = ConnectionTracker::class.java.getDeclaredField("ENTRY_TIMEOUT_MS")
        field.isAccessible = true
        return field.getLong(null)
    }
}
