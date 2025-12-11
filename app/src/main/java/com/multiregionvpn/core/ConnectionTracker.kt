package com.multiregionvpn.core

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
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
    
    /**
     * Register a package name and get its UID.
     * Called when app rules are created.
     */
    fun registerPackage(packageName: String): Int? {
        // Skip test-related packages that may not be accessible
        if (packageName.startsWith("androidx.test.") || 
            packageName.startsWith("android.test.") ||
            packageName == "androidx.test.services") {
            Log.w(TAG, "Skipping registration of test package: $packageName (not accessible in test environment)")
            return null
        }
        
        val maxRetries = 3
        val retryDelayMs = 500L // 0.5 seconds

        for (i in 0 until maxRetries) {
            try {
                val uid = packageManager.getApplicationInfo(packageName, 0).uid
                packageNameToUid[packageName] = uid
                Log.d(TAG, "Registered package $packageName -> UID $uid")
                return uid
            } catch (e: PackageManager.NameNotFoundException) {
                if (i < maxRetries - 1) {
                    Log.w(TAG, "Package not found: $packageName (attempt ${i + 1}/$maxRetries). Retrying in ${retryDelayMs}ms...")
                    Thread.sleep(retryDelayMs) // Use Thread.sleep in a non-coroutine context
                } else {
                    Log.w(TAG, "Package not found: $packageName (after $maxRetries attempts) - skipping registration")
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "Security exception when registering package $packageName: ${e.message} - skipping")
                return null
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Invalid package name: $packageName - ${e.message} - skipping")
                return null
            } catch (e: Exception) {
                Log.w(TAG, "Error registering package $packageName: ${e.message} - skipping", e)
                return null
            }
        }
        return null // All retries failed
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
        // Skip test-related packages
        if (packageName.startsWith("androidx.test.") || 
            packageName.startsWith("android.test.") ||
            packageName == "androidx.test.services") {
            Log.w(TAG, "Skipping tunnel mapping for test package: $packageName")
            return false
        }
        
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
     * Clear all mappings (used when rules change or VPN stops).
     */
    fun clearAllMappings() {
        connectionTable.clear()
        packageNameToUid.clear()
        uidToTunnelId.clear()
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


