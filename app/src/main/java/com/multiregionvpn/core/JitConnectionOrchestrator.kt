package com.multiregionvpn.core

import android.util.Log
import com.multiregionvpn.core.net.LatencyTester
import com.multiregionvpn.core.provider.*
import com.multiregionvpn.data.database.ProviderAccountEntity
import com.multiregionvpn.data.database.ProviderCredentials
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates Just-In-Time VPN connections.
 * Handles server selection, latency testing, config generation, and connection establishment.
 */
@Singleton
class JitConnectionOrchestrator @Inject constructor(
    private val providerRegistry: ProviderRegistry,
    private val latencyTester: LatencyTester,
    private val vpnConnectionManager: VpnConnectionManager,
    private val packetBufferManager: PacketBufferManager
) {
    private val activeConnections = ConcurrentHashMap<PacketBufferManager.RouteKey, ConnectionEntry>()
    private val connectionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // Configuration
    private val idleTimeoutMs = 5 * 60 * 1000L // 5 minutes
    private val inactivityCheckIntervalMs = 30 * 1000L // Check every 30 seconds

    init {
        // Start inactivity monitoring
        connectionScope.launch {
            monitorInactivity()
        }
    }

    /**
     * Ensures a connection is established for the given route.
     * If not connected, triggers the JIT connection flow.
     */
    suspend fun ensureConnected(
        routeKey: PacketBufferManager.RouteKey,
        providerAccount: ProviderAccountEntity,
        regionRequest: RegionRequest
    ): String? = withContext(Dispatchers.Default) {
        val entry = activeConnections.getOrPut(routeKey) {
            ConnectionEntry(
                routeKey = routeKey,
                providerId = providerAccount.providerId,
                regionRequest = regionRequest
            )
        }

        // If already connected, return tunnel ID
        if (entry.state == ConnectionState.CONNECTED && entry.tunnelId != null) {
            entry.updateActivity()
            return@withContext entry.tunnelId
        }

        // If connection is in progress, wait for it
        if (entry.state == ConnectionState.SELECTING_SERVER || entry.state == ConnectionState.CONNECTING) {
            entry.connectionJob?.join()
            return@withContext entry.tunnelId
        }

        // Start new connection
        entry.connectionJob = connectionScope.launch {
            try {
                connectJit(entry, providerAccount, regionRequest)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to establish JIT connection for $routeKey", e)
                entry.state = ConnectionState.DISCONNECTED
            }
        }

        entry.connectionJob?.join()
        entry.tunnelId
    }

    /**
     * Performs the JIT connection flow:
     * 1. Select best server (with latency testing)
     * 2. Generate config
     * 3. Establish connection
     * 4. Flush buffered packets
     */
    private suspend fun connectJit(
        entry: ConnectionEntry,
        providerAccount: ProviderAccountEntity,
        regionRequest: RegionRequest
    ) {
        val provider = providerRegistry.requireProvider(entry.providerId)
        
        try {
            // Step 1: Select best server
            entry.state = ConnectionState.SELECTING_SERVER
            Log.d(TAG, "Selecting best server for ${entry.routeKey}...")
            
            val servers = provider.fetchServers(forceRefresh = false)
                .filter { it.regionCode == regionRequest.regionCode }
            
            if (servers.isEmpty()) {
                throw Exception("No servers available for region ${regionRequest.regionCode}")
            }

            // Measure latency to candidate servers
            val candidates = servers.take(10) // Test top 10 servers
            val latencyResults = latencyTester.measure(candidates)
            val bestServer = latencyResults.firstOrNull { it.success }?.server
                ?: throw Exception("No reachable servers found")

            Log.d(TAG, "Selected server: ${bestServer.hostname} (latency: ${latencyResults.first().latencyMs}ms)")

            // Step 2: Generate config
            entry.state = ConnectionState.CONNECTING
            Log.d(TAG, "Generating config for ${bestServer.hostname}...")
            
            // TODO: Decrypt credentials from providerAccount.encryptedCredentials
            // For now, use a placeholder
            val credentials = ProviderCredentials(
                templateId = entry.providerId,
                username = "", // TODO: Decrypt from encryptedCredentials
                password = ""  // TODO: Decrypt from encryptedCredentials
            )
            
            val protocol = regionRequest.preferredProtocol ?: SupportedProtocol.OPENVPN_UDP
            val target = ProviderTarget(
                server = bestServer,
                protocol = protocol,
                credentials = credentials
            )
            
            val vpnConfig = provider.generateConfig(target, credentials)
            val tunnelId = "${vpnConfig.templateId}_${vpnConfig.regionId}"

            // Step 3: Establish connection
            Log.d(TAG, "Establishing connection to $tunnelId...")
            
            // TODO: Use VpnTemplateService to prepare config, then VpnConnectionManager to connect
            // For now, this is a placeholder
            // val preparedConfig = vpnTemplateService.prepareConfig(vpnConfig)
            // val result = vpnConnectionManager.createTunnel(tunnelId, preparedConfig.ovpnFileContent, preparedConfig.authFile?.absolutePath)
            
            entry.tunnelId = tunnelId
            entry.state = ConnectionState.CONNECTED
            entry.updateActivity()
            
            Log.d(TAG, "JIT connection established: $tunnelId")

            // Step 4: Flush buffered packets
            val bufferedPackets = packetBufferManager.drainPackets(entry.routeKey)
            Log.d(TAG, "Flushing ${bufferedPackets.size} buffered packets for ${entry.routeKey}")
            bufferedPackets.forEach { packet ->
                vpnConnectionManager.sendPacketToTunnel(tunnelId, packet)
            }
        } catch (e: Exception) {
            Log.e(TAG, "JIT connection failed for ${entry.routeKey}", e)
            entry.state = ConnectionState.DISCONNECTED
            throw e
        }
    }

    /**
     * Monitors connections for inactivity and disconnects idle tunnels.
     */
    private suspend fun monitorInactivity() {
        while (connectionScope.isActive) {
            delay(inactivityCheckIntervalMs)
            
            val idleEntries = activeConnections.values.filter { it.isIdle(idleTimeoutMs) }
            idleEntries.forEach { entry ->
                Log.d(TAG, "Disconnecting idle tunnel: ${entry.tunnelId}")
                entry.state = ConnectionState.DISCONNECTING
                
                entry.tunnelId?.let { tunnelId ->
                    try {
                        vpnConnectionManager.closeTunnel(tunnelId)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error disconnecting idle tunnel $tunnelId", e)
                    }
                }
                
                activeConnections.remove(entry.routeKey)
                packetBufferManager.clearRoute(entry.routeKey)
            }
        }
    }

    /**
     * Gets the current state of a connection.
     */
    fun getConnectionState(routeKey: PacketBufferManager.RouteKey): ConnectionState? {
        return activeConnections[routeKey]?.state
    }

    /**
     * Manually disconnects a connection.
     */
    suspend fun disconnect(routeKey: PacketBufferManager.RouteKey) {
        val entry = activeConnections.remove(routeKey) ?: return
        entry.state = ConnectionState.DISCONNECTING
        entry.connectionJob?.cancel()
        
        entry.tunnelId?.let { tunnelId ->
            vpnConnectionManager.closeTunnel(tunnelId)
        }
        
        packetBufferManager.clearRoute(routeKey)
    }

    companion object {
        private const val TAG = "JitConnectionOrchestrator"
    }
}

