package com.multiregionvpn.core

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Before
import org.junit.Test
import java.net.InetAddress

/**
 * BDD-style unit tests for ConnectionTracker
 */
class ConnectionTrackerTest {
    
    private lateinit var context: Context
    private lateinit var packageManager: PackageManager
    private lateinit var tracker: ConnectionTracker
    
    @Before
    fun setup() {
        context = mockk<Context>(relaxed = true)
        packageManager = mockk<PackageManager>(relaxed = true)
        every { context.packageManager } returns packageManager
        tracker = ConnectionTracker(context, packageManager)
    }
    
    @Test
    fun `given package exists, when registerPackage is called, then UID is returned and stored`() {
        // GIVEN: A package exists with UID 10128
        val packageName = "com.example.app"
        val uid = 10128
        val appInfo = ApplicationInfo().apply {
            this.uid = uid
        }
        every { packageManager.getApplicationInfo(packageName, 0) } returns appInfo
        
        // WHEN: Registering the package
        val result = tracker.registerPackage(packageName)
        
        // THEN: UID is returned and stored
        assertThat(result).isEqualTo(uid)
        assertThat(tracker.getRegisteredUids()).contains(uid)
    }
    
    @Test
    fun `given package does not exist, when registerPackage is called, then null is returned`() {
        // GIVEN: Package does not exist
        val packageName = "com.nonexistent.app"
        every { packageManager.getApplicationInfo(packageName, 0) } throws PackageManager.NameNotFoundException()
        
        // WHEN: Registering the package
        val result = tracker.registerPackage(packageName)
        
        // THEN: Null is returned
        assertThat(result).isNull()
        assertThat(tracker.getRegisteredUids()).isEmpty()
    }
    
    @Test
    fun `given UID is mapped to tunnel, when setUidToTunnel is called, then mapping is stored`() {
        // GIVEN: A UID exists
        val uid = 10128
        val tunnelId = "nordvpn_UK"
        
        // WHEN: Mapping UID to tunnel
        tracker.setUidToTunnel(uid, tunnelId)
        
        // THEN: Mapping is stored
        assertThat(tracker.getTunnelForUid(uid)).isEqualTo(tunnelId)
    }
    
    @Test
    fun `given package and tunnel, when setPackageToTunnel is called, then both package and tunnel are mapped`() {
        // GIVEN: A package exists with UID
        val packageName = "com.example.app"
        val uid = 10128
        val tunnelId = "nordvpn_UK"
        val appInfo = ApplicationInfo().apply {
            this.uid = uid
        }
        every { packageManager.getApplicationInfo(packageName, 0) } returns appInfo
        
        // WHEN: Setting package to tunnel
        val result = tracker.setPackageToTunnel(packageName, tunnelId)
        
        // THEN: Both mappings are stored
        assertThat(result).isTrue()
        assertThat(tracker.getRegisteredUids()).contains(uid)
        assertThat(tracker.getTunnelForUid(uid)).isEqualTo(tunnelId)
    }
    
    @Test
    fun `given connection is registered, when lookupConnection is called, then connection info is returned`() {
        // GIVEN: A connection is registered
        val srcIp = InetAddress.getByName("10.0.0.2")
        val srcPort = 12345
        val uid = 10128
        val tunnelId = "nordvpn_UK"
        tracker.setUidToTunnel(uid, tunnelId)
        tracker.registerConnection(srcIp, srcPort, uid, tunnelId)
        
        // WHEN: Looking up the connection
        val result = tracker.lookupConnection(srcIp, srcPort)
        
        // THEN: Connection info is returned
        assertThat(result).isNotNull()
        assertThat(result!!.uid).isEqualTo(uid)
        assertThat(result.tunnelId).isEqualTo(tunnelId)
    }
    
    @Test
    fun `given connection is not registered, when lookupConnection is called, then null is returned`() {
        // GIVEN: No connection is registered
        val srcIp = InetAddress.getByName("10.0.0.2")
        val srcPort = 12345
        
        // WHEN: Looking up the connection
        val result = tracker.lookupConnection(srcIp, srcPort)
        
        // THEN: Null is returned
        assertThat(result).isNull()
    }
    
    @Test
    fun `given package is registered, when lookupConnectionWithFallback is called with package name, then connection info is returned and connection is registered`() {
        // GIVEN: Package is registered but connection is not
        val packageName = "com.example.app"
        val uid = 10128
        val tunnelId = "nordvpn_UK"
        val appInfo = ApplicationInfo().apply {
            this.uid = uid
        }
        every { packageManager.getApplicationInfo(packageName, 0) } returns appInfo
        tracker.setPackageToTunnel(packageName, tunnelId)
        
        val srcIp = InetAddress.getByName("10.0.0.2")
        val srcPort = 12345
        
        // WHEN: Looking up connection with fallback
        val result = tracker.lookupConnectionWithFallback(srcIp, srcPort, packageName)
        
        // THEN: Connection info is returned (from fallback)
        assertThat(result).isNotNull()
        assertThat(result!!.uid).isEqualTo(uid)
        assertThat(result.tunnelId).isEqualTo(tunnelId)
        
        // AND: Connection is now registered for future lookups
        val directLookup = tracker.lookupConnection(srcIp, srcPort)
        assertThat(directLookup).isNotNull()
        assertThat(directLookup!!.uid).isEqualTo(uid)
    }
    
    @Test
    fun `given connection is registered, when removeConnection is called, then connection is removed`() {
        // GIVEN: A connection is registered
        val srcIp = InetAddress.getByName("10.0.0.2")
        val srcPort = 12345
        val uid = 10128
        tracker.registerConnection(srcIp, srcPort, uid)
        
        // WHEN: Removing the connection
        tracker.removeConnection(srcIp, srcPort)
        
        // THEN: Connection is removed
        assertThat(tracker.lookupConnection(srcIp, srcPort)).isNull()
    }
    
    @Test
    fun `given connections exist for UID, when clearConnectionsForUid is called, then all connections for that UID are removed`() {
        // GIVEN: Multiple connections exist for the same UID
        val uid = 10128
        val tunnelId = "nordvpn_UK"
        tracker.setUidToTunnel(uid, tunnelId)
        
        val srcIp1 = InetAddress.getByName("10.0.0.2")
        val srcPort1 = 12345
        val srcIp2 = InetAddress.getByName("10.0.0.3")
        val srcPort2 = 54321
        
        tracker.registerConnection(srcIp1, srcPort1, uid, tunnelId)
        tracker.registerConnection(srcIp2, srcPort2, uid, tunnelId)
        
        // WHEN: Clearing connections for UID
        tracker.clearConnectionsForUid(uid)
        
        // THEN: All connections are removed
        assertThat(tracker.lookupConnection(srcIp1, srcPort1)).isNull()
        assertThat(tracker.lookupConnection(srcIp2, srcPort2)).isNull()
        assertThat(tracker.getTunnelForUid(uid)).isNull()
    }
    
    @Test
    fun `given connections exist for package, when clearConnectionsForPackage is called, then all connections for that package are removed`() {
        // GIVEN: Package and connections exist
        val packageName = "com.example.app"
        val uid = 10128
        val tunnelId = "nordvpn_UK"
        val appInfo = ApplicationInfo().apply {
            this.uid = uid
        }
        every { packageManager.getApplicationInfo(packageName, 0) } returns appInfo
        tracker.setPackageToTunnel(packageName, tunnelId)
        
        val srcIp = InetAddress.getByName("10.0.0.2")
        val srcPort = 12345
        tracker.registerConnection(srcIp, srcPort, uid, tunnelId)
        
        // WHEN: Clearing connections for package
        tracker.clearConnectionsForPackage(packageName)
        
        // THEN: Connections are removed
        assertThat(tracker.lookupConnection(srcIp, srcPort)).isNull()
        assertThat(tracker.getTunnelForUid(uid)).isNull()
    }
    
    @Test
    fun `given tracker has data, when clear is called, then all data is removed`() {
        // GIVEN: Tracker has data
        val packageName = "com.example.app"
        val uid = 10128
        val tunnelId = "nordvpn_UK"
        val appInfo = ApplicationInfo().apply {
            this.uid = uid
        }
        every { packageManager.getApplicationInfo(packageName, 0) } returns appInfo
        tracker.setPackageToTunnel(packageName, tunnelId)
        
        val srcIp = InetAddress.getByName("10.0.0.2")
        val srcPort = 12345
        tracker.registerConnection(srcIp, srcPort, uid, tunnelId)
        
        // WHEN: Clearing all data
        tracker.clear()
        
        // THEN: All data is removed
        assertThat(tracker.getRegisteredUids()).isEmpty()
        assertThat(tracker.lookupConnection(srcIp, srcPort)).isNull()
        val stats = tracker.getStats()
        assertThat(stats.connectionCount).isEqualTo(0)
        assertThat(stats.packageCount).isEqualTo(0)
        assertThat(stats.tunnelCount).isEqualTo(0)
    }
    
    @Test
    fun `given tracker has data, when getStats is called, then correct statistics are returned`() {
        // GIVEN: Tracker has some data
        val packageName = "com.example.app"
        val uid = 10128
        val tunnelId = "nordvpn_UK"
        val appInfo = ApplicationInfo().apply {
            this.uid = uid
        }
        every { packageManager.getApplicationInfo(packageName, 0) } returns appInfo
        tracker.setPackageToTunnel(packageName, tunnelId)
        
        val srcIp = InetAddress.getByName("10.0.0.2")
        val srcPort = 12345
        tracker.registerConnection(srcIp, srcPort, uid, tunnelId)
        
        // WHEN: Getting statistics
        val stats = tracker.getStats()
        
        // THEN: Correct statistics are returned
        assertThat(stats.connectionCount).isEqualTo(1)
        assertThat(stats.packageCount).isEqualTo(1)
        assertThat(stats.tunnelCount).isEqualTo(1)
    }
}


