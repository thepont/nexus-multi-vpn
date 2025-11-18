package com.multiregionvpn.core

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks connections to map (srcIP, srcPort) to UID and tunnelId.
 * 
 * This solves the problem of not being able to read /proc/net files
 * due to Android permission restrictions. Instead, we maintain our own
 * connection tracking table.
 * 
 * Architecture:
 * - When app rules are created: packageName → UID (via PackageManager)
 * - When connections are established: (srcIP, srcPort) → UID
 * - When packets arrive: (srcIP, srcPort) → lookup UID → lookup tunnelId → route
 */
class ConnectionTracker(
    private val context: Context,
    private val packageManager: PackageManager
) {
    private val connectionTable = ConcurrentHashMap<String, ConnectionInfo>()
    private val packageNameToUid = ConcurrentHashMap<String, Int>()
    private val uidToTunnelId = ConcurrentHashMap<Int, String>()
    private val ipToTunnelIds = ConcurrentHashMap<String, MutableSet<String>>()
    private val routeLock = Any()
    private val routeEntries = mutableListOf<RouteEntry>()
    
    companion object {
        private const val TAG = "ConnectionTracker"
        private const val MAX_ENTRIES = 10000 // Prevent memory exhaustion
        private const val ENTRY_TIMEOUT_MS = 300000L // 5 minutes
    }
    
    /**
     * Connection information stored in tracking table
     */
    data class ConnectionInfo(
        val uid: Int,
        val tunnelId: String?,
        val timestamp: Long = System.currentTimeMillis()
    )

    private data class RouteEntry(
        val tunnelId: String,
        val networkAddress: Int,
        val prefixLength: Int
    )
    
    /**
     * Register a package name and get its UID.
     * Called when app rules are created.
     */
    fun registerPackage(packageName: String): Int? {
        return try {
            val uid = packageManager.getApplicationInfo(packageName, 0).uid
            packageNameToUid[packageName] = uid
            Log.d(TAG, "Registered package $packageName -> UID $uid")
            uid
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Package not found: $packageName")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error registering package $packageName", e)
            null
        }
    }
    
    /**
     * Map a UID to a tunnel ID.
     * Called when app rules are created or updated.
     */
    fun setUidToTunnel(uid: Int, tunnelId: String) {
        uidToTunnelId[uid] = tunnelId
        Log.d(TAG, "Mapped UID $uid -> tunnel $tunnelId")
    }
    
    /**
     * Map a UID to a tunnel ID via package name.
     * Convenience method that combines registerPackage + setUidToTunnel.
     */
    fun setPackageToTunnel(packageName: String, tunnelId: String): Boolean {
        val uid = registerPackage(packageName) ?: return false
        setUidToTunnel(uid, tunnelId)
        return true
    }

    /**
     * Clear all mappings for the specified package.
     */
    fun clearPackage(packageName: String) {
        val uid = packageNameToUid.remove(packageName)
        if (uid != null) {
            uidToTunnelId.remove(uid)
            connectionTable.entries.removeIf { it.value.uid == uid }
            Log.d(TAG, "Cleared connection tracking for package $packageName (uid $uid)")
        }
    }

    /**
     * Clear per-connection state while retaining package → tunnel associations.
     * Useful when app rules change but existing registrations remain valid.
     */
    fun clearTransientMappings() {
        connectionTable.clear()
        ipToTunnelIds.clear()
        Log.d(TAG, "Cleared transient connection tracker mappings (connections + IP routes)")
    }

    /**
     * Clear all mappings (used when rules change or VPN stops).
     */
    fun clearAllMappings() {
        clearTransientMappings()
        packageNameToUid.clear()
        uidToTunnelId.clear()
        clearRoutes()
        Log.d(TAG, "Cleared all connection tracker mappings")
    }

    /**
     * Snapshot of current package -> tunnel mappings.
     */
    fun getCurrentPackageMappings(): Map<String, String> {
        return packageNameToUid.mapNotNull { (pkg, uid) ->
            val tunnel = uidToTunnelId[uid]
            if (tunnel != null) pkg to tunnel else null
        }.toMap()
    }
    
    /**
     * Register a connection: (srcIP, srcPort) -> UID.
     * Called when a connection is detected or when we receive a packet
     * and can infer the connection from context.
     */
    fun registerConnection(srcIp: InetAddress, srcPort: Int, uid: Int, tunnelId: String? = null) {
        val key = connectionKey(srcIp, srcPort)
        
        // Prevent memory exhaustion
        if (connectionTable.size >= MAX_ENTRIES) {
            cleanupOldEntries()
        }
        
        val tunnel = tunnelId ?: uidToTunnelId[uid]
        connectionTable[key] = ConnectionInfo(uid, tunnel)
        Log.v(TAG, "Registered connection $key -> UID $uid, tunnel $tunnel")
    }
    
    /**
     * Look up UID for a connection.
     * Returns UID and optional tunnelId if found.
     */
    fun lookupConnection(srcIp: InetAddress, srcPort: Int): ConnectionInfo? {
        val key = connectionKey(srcIp, srcPort)
        val info = connectionTable[key]
        
        // Check if entry is stale (older than timeout)
        if (info != null && System.currentTimeMillis() - info.timestamp > ENTRY_TIMEOUT_MS) {
            connectionTable.remove(key)
            Log.v(TAG, "Removed stale connection entry: $key")
            return null
        }
        
        return info
    }
    
    /**
     * Look up UID for a connection, falling back to package name lookup.
     * If connection not found, tries to infer from package name if we have
     * a mapping from package name to UID.
     */
    fun lookupConnectionWithFallback(
        srcIp: InetAddress,
        srcPort: Int,
        packageName: String?
    ): ConnectionInfo? {
        // First try direct connection lookup
        val direct = lookupConnection(srcIp, srcPort)
        if (direct != null) {
            return direct
        }
        
        // Fallback: if we have package name, try to get UID from it
        if (packageName != null) {
            val uid = packageNameToUid[packageName]
            if (uid != null) {
                val tunnelId = uidToTunnelId[uid]
                // Register this connection for future lookups
                registerConnection(srcIp, srcPort, uid, tunnelId)
                return ConnectionInfo(uid, tunnelId)
            }
        }
        
        return null
    }
    
    /**
     * Remove a connection from tracking table.
     */
    fun removeConnection(srcIp: InetAddress, srcPort: Int) {
        val key = connectionKey(srcIp, srcPort)
        connectionTable.remove(key)
        Log.v(TAG, "Removed connection: $key")
    }
    
    /**
     * Clear all connections for a specific UID.
     * Called when app rules are removed or tunnel is disconnected.
     */
    fun clearConnectionsForUid(uid: Int) {
        val toRemove = connectionTable.entries.filter { it.value.uid == uid }
        toRemove.forEach { connectionTable.remove(it.key) }
        uidToTunnelId.remove(uid)
        Log.d(TAG, "Cleared ${toRemove.size} connections for UID $uid")
    }
    
    /**
     * Clear all connections for a specific package name.
     */
    fun clearConnectionsForPackage(packageName: String) {
        val uid = packageNameToUid[packageName]
        if (uid != null) {
            clearConnectionsForUid(uid)
            packageNameToUid.remove(packageName)
        }
    }
    
    /**
     * Get all registered UIDs.
     */
    fun getRegisteredUids(): Set<Int> {
        return uidToTunnelId.keys.toSet()
    }
    
    /**
     * Get all registered package names.
     * This is more reliable than getting UIDs first, since we track packages directly.
     */
    fun getRegisteredPackages(): Set<String> {
        return packageNameToUid.keys.toSet()
    }
    
    fun addRouteForTunnel(tunnelId: String, address: InetAddress, prefixLength: Int) {
        if (address !is Inet4Address) {
            Log.d(TAG, "Ignoring non-IPv4 route $address/$prefixLength for tunnel $tunnelId")
            return
        }
        if (prefixLength < 0 || prefixLength > 32) {
            Log.w(TAG, "Invalid prefix length $prefixLength for route $address/$prefixLength")
            return
        }

        val networkAddress = ipv4ToInt(address) and prefixMask(prefixLength)
        synchronized(routeLock) {
            routeEntries.removeAll { it.tunnelId == tunnelId && it.networkAddress == networkAddress && it.prefixLength == prefixLength }
            routeEntries.add(RouteEntry(tunnelId, networkAddress, prefixLength))
        }
        Log.d(TAG, "Added route ${address.hostAddress}/$prefixLength -> $tunnelId (network=${intToIpv4(networkAddress)})")
    }

    fun removeRoutesForTunnel(tunnelId: String) {
        synchronized(routeLock) {
            if (routeEntries.removeAll { it.tunnelId == tunnelId }) {
                Log.d(TAG, "Removed routes for tunnel $tunnelId")
            }
        }
    }

    fun clearRoutes() {
        synchronized(routeLock) {
            routeEntries.clear()
        }
        Log.d(TAG, "Cleared all route entries")
    }

    fun getTunnelForDestination(destIp: InetAddress): String? {
        if (destIp !is Inet4Address) {
            return null
        }
        val dest = ipv4ToInt(destIp)
        synchronized(routeLock) {
            var bestMatch: RouteEntry? = null
            for (entry in routeEntries) {
                val mask = prefixMask(entry.prefixLength)
                if ((dest and mask) == entry.networkAddress) {
                    if (bestMatch == null || entry.prefixLength > bestMatch!!.prefixLength) {
                        bestMatch = entry
                    }
                }
            }
            return bestMatch?.tunnelId
        }
    }

    /**
     * Get tunnel ID for a UID.
     */
    fun getTunnelForUid(uid: Int): String? {
        return uidToTunnelId[uid]
    }
    
    /**
     * Get tunnel ID for a UID (alias for getTunnelForUid for consistency).
     */
    fun getTunnelIdForUid(uid: Int): String? {
        return uidToTunnelId[uid]
    }

    fun setTunnelForIp(ip: InetAddress, tunnelId: String) {
        val key = ip.hostAddress ?: run {
            Log.w(TAG, "Unable to map tunnel for IP (hostAddress null): $ip")
            return
        }
        val tunnels = ipToTunnelIds.getOrPut(key) { mutableSetOf() }
        tunnels.add(tunnelId)
        Log.d(TAG, "Mapped IP $key -> tunnels ${tunnels.joinToString()}")
    }

    fun removeTunnelForIp(ip: InetAddress) {
        val key = ip.hostAddress ?: return
        val tunnels = ipToTunnelIds.remove(key)
        if (tunnels != null) {
            Log.d(TAG, "Removed IP mapping for $key (previous tunnels ${tunnels.joinToString()})")
        }
    }

    fun clearIpMappings() {
        ipToTunnelIds.clear()
        Log.d(TAG, "Cleared all IP-to-tunnel mappings")
    }

    fun getTunnelForIp(ip: InetAddress): String? {
        val key = ip.hostAddress ?: return null
        val tunnels = ipToTunnelIds[key] ?: return null
        return when (tunnels.size) {
            0 -> null
            1 -> tunnels.first()
            else -> {
                Log.v(TAG, "IP $key mapped to multiple tunnels $tunnels - deferring to connection lookup")
                null
            }
        }
    }
    
    /**
     * Get UID for a package name.
     */
    fun getUidForPackage(packageName: String): Int? {
        return packageNameToUid[packageName]
    }
    
    /**
     * Cleanup old entries to prevent memory exhaustion.
     */
    private fun cleanupOldEntries() {
        val now = System.currentTimeMillis()
        val toRemove = connectionTable.entries.filter { 
            now - it.value.timestamp > ENTRY_TIMEOUT_MS 
        }
        toRemove.forEach { connectionTable.remove(it.key) }
        Log.d(TAG, "Cleaned up ${toRemove.size} stale connection entries")
    }

    private fun ipv4ToInt(address: Inet4Address): Int {
        val bytes = address.address
        return (bytes[0].toInt() and 0xFF shl 24) or
            (bytes[1].toInt() and 0xFF shl 16) or
            (bytes[2].toInt() and 0xFF shl 8) or
            (bytes[3].toInt() and 0xFF)
    }

    private fun intToIpv4(value: Int): String {
        return "${value ushr 24 and 0xFF}.${value ushr 16 and 0xFF}.${value ushr 8 and 0xFF}.${value and 0xFF}"
    }

    private fun prefixMask(prefixLength: Int): Int {
        return if (prefixLength == 0) {
            0
        } else {
            (-1 shl (32 - prefixLength))
        }
    }
    
    /**
     * Generate connection key from IP and port.
     */
    private fun connectionKey(srcIp: InetAddress, srcPort: Int): String {
        return "${srcIp.hostAddress}:$srcPort"
    }
    
    /**
     * Clear all tracking data.
     */
    fun clear() {
        connectionTable.clear()
        packageNameToUid.clear()
        uidToTunnelId.clear()
        Log.d(TAG, "Cleared all connection tracking data")
    }
    
    /**
     * Get statistics for debugging.
     */
    fun getStats(): Stats {
        return Stats(
            connectionCount = connectionTable.size,
            packageCount = packageNameToUid.size,
            tunnelCount = uidToTunnelId.size
        )
    }
    
    data class Stats(
        val connectionCount: Int,
        val packageCount: Int,
        val tunnelCount: Int
    )
}


