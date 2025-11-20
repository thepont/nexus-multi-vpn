package com.multiregionvpn.core

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.multiregionvpn.data.database.ConnectionEvent
import com.multiregionvpn.data.repository.ConnectionsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Logs new connection events to the database for display in ConnectionsScreen.
 * 
 * Uses a connection cache to track which connections have already been logged,
 * ensuring we only log NEW connections (not every packet).
 */
@Singleton
class ConnectionLogger @Inject constructor(
    private val connectionsRepository: ConnectionsRepository,
    private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val loggedConnections = ConcurrentHashMap<String, Long>()
    private val packageManager = context.packageManager
    
    companion object {
        private const val TAG = "ConnectionLogger"
        private const val MAX_CACHED_CONNECTIONS = 1000
        private const val CACHE_CLEANUP_THRESHOLD = 1200
    }
    
    /**
     * Log a new connection event.
     * Only logs if this connection hasn't been logged recently.
     */
    fun logConnection(
        packageName: String,
        destinationIp: String,
        destinationPort: Int,
        protocol: String,
        tunnelId: String?,
        tunnelAlias: String?
    ) {
        val connectionKey = "$packageName:$destinationIp:$destinationPort:$protocol"
        
        // Check if we've already logged this connection recently
        val lastLogged = loggedConnections[connectionKey]
        val now = System.currentTimeMillis()
        
        // Only log if this is a new connection or it was logged more than 5 minutes ago
        if (lastLogged == null || (now - lastLogged) > 300000) {
            loggedConnections[connectionKey] = now
            
            // Get app display name
            val appName = try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                packageName // Fallback to package name
            } catch (e: Exception) {
                packageName // Fallback to package name
            }
            
            // Create connection event
            val event = ConnectionEvent(
                timestamp = now,
                packageName = packageName,
                appName = appName,
                destinationIp = destinationIp,
                destinationPort = destinationPort,
                protocol = protocol,
                tunnelId = tunnelId,
                tunnelAlias = tunnelAlias
            )
            
            // Log to database asynchronously
            scope.launch {
                try {
                    connectionsRepository.logConnection(event)
                    Log.d(TAG, "Logged connection: $appName → $destinationIp:$destinationPort via ${tunnelAlias ?: "Direct"}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to log connection", e)
                }
            }
            
            // Cleanup cache if it gets too large
            if (loggedConnections.size > CACHE_CLEANUP_THRESHOLD) {
                cleanupCache()
            }
        }
    }
    
    /**
     * Remove old entries from the cache to prevent memory exhaustion
     */
    private fun cleanupCache() {
        val now = System.currentTimeMillis()
        val toRemove = loggedConnections.entries.filter { (_, timestamp) ->
            (now - timestamp) > 300000 // Remove entries older than 5 minutes
        }
        toRemove.forEach { loggedConnections.remove(it.key) }
        Log.d(TAG, "Cleaned up ${toRemove.size} stale connection cache entries")
    }
    
    /**
     * Clear all cached connections
     */
    fun clearCache() {
        loggedConnections.clear()
        Log.d(TAG, "Cleared connection cache")
    }
}
