package com.multiregionvpn.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.VpnService
import android.util.Log
import com.multiregionvpn.data.repository.SettingsRepository
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * High-performance singleton that routes packets based on app rules.
 * Parses packets, gets UID, looks up rules, and routes accordingly.
 */
class PacketRouter(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val vpnService: VpnService,
    private val vpnConnectionManager: VpnConnectionManager,
    private val vpnOutput: java.io.FileOutputStream? = null, // For writing packets back to TUN interface
    private val connectionTracker: ConnectionTracker? = null // Optional connection tracker for UID detection
) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val packageManager = context.packageManager
    private val tracker = connectionTracker ?: ConnectionTracker(context, packageManager)
    
    fun routePacket(packet: ByteArray) {
        try {
            // PERFORMANCE: Removed per-packet logging to prevent binder exhaustion
            // (was logging 100+ times per second, flooding binder buffer)
            // Only log errors and important routing events
            
            // Parse packet to get 5-tuple
            val packetInfo = parsePacket(packet)
            if (packetInfo == null) {
                // Only log parse failures occasionally to avoid spam
                if (Math.random() < 0.01) {  // 1% sample rate
                    Log.w(TAG, "❌ Packet parse failed - size=${packet.size}")
                }
                return
            }
            
            // ARCHITECTURE NOTE: With SOCK_SEQPACKET socketpair architecture:
            // - Outbound packets (app → internet): Read from TUN → route here → write to socket pair → OpenVPN 3
            // - Inbound packets (internet → app): OpenVPN 3 → write to socket pair → packetReceiver → TUN
            //
            // This means ALL packets we receive here from TUN are outbound and need routing.
            // We do NOT receive inbound packets here - they come via packetReceiver callback.
            //
            // The old "isFromTunnelIp" check was incorrectly matching the VPN interface IP (10.100.0.2),
            // causing outbound HTTP traffic to be misclassified as "inbound" and sent to direct internet!
            
            val srcIpString = packetInfo.srcIp.hostAddress ?: ""
            val isDnsQuery = packetInfo.protocol == 17 && packetInfo.destPort == 53
            
            // CRITICAL: Use ConnectionTracker to get UID instead of /proc/net
            // This solves the permission denied issue with /proc/net files
            val connectionInfo = tracker.lookupConnection(packetInfo.srcIp, packetInfo.srcPort)
            
            if (connectionInfo == null) {
                // Connection not in tracking table - try to infer from registered packages
                // Since we're using VpnService.Builder.addAllowedApplication(), only registered
                // apps' packets should reach us. Try to match by checking registered packages.
                // CRITICAL: For DNS queries (UDP port 53) or any new connection from registered apps,
                // we should register it immediately and route it to the appropriate tunnel
                // Since only registered apps' packets reach us (via addAllowedApplication),
                // any packet from a registered app should be routed to its tunnel
                
                // SPECIAL CASE: DNS queries (UDP port 53)
                // DNS queries are special - they come from the system resolver on behalf of apps
                // The source IP is the VPN interface IP (e.g., 10.100.0.2), not the app's IP
                // 
                // INTELLIGENT DNS ROUTING STRATEGY:
                // 1. Try to route to the same tunnel as the app that initiated the query
                //    (using registered package tracking)
                // 2. Fall back to user-configured default DNS tunnel
                // 3. Final fallback to first connected tunnel
                if (packetInfo.protocol == 17 && packetInfo.destPort == 53) {
                    Log.d(TAG, "🔍 DNS query detected: ${packetInfo.srcIp}:${packetInfo.srcPort} → ${packetInfo.destIp}:53")
                    val allTunnels = vpnConnectionManager.getAllTunnelIds()
                    Log.d(TAG, "   Available tunnels: ${allTunnels.joinToString()}")
                    
                    var selectedTunnel: String? = null
                    
                    // Strategy 1: Try to match DNS query to an app's tunnel
                    // Check if any registered package has a tunnel assigned
                    val registeredPackages = try {
                        tracker.getRegisteredPackages()
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ ERROR getting registered packages: ${e.message}", e)
                        emptySet()
                    }
                    
                    if (registeredPackages.isNotEmpty()) {
                        // Try to find a tunnel from registered packages
                        // Prefer the most recently used or first available
                        for (packageName in registeredPackages) {
                            val uid = tracker.getUidForPackage(packageName)
                            if (uid != null) {
                                val tunnelId = tracker.getTunnelIdForUid(uid)
                                if (tunnelId != null && vpnConnectionManager.isTunnelConnected(tunnelId)) {
                                    selectedTunnel = tunnelId
                                    Log.d(TAG, "   Strategy 1: Found tunnel from app $packageName: $tunnelId")
                                    break
                                }
                            }
                        }
                    }
                    
                    // Strategy 2: Use user-configured default DNS tunnel
                    if (selectedTunnel == null) {
                        val defaultDnsTunnel = try {
                            settingsRepository.getDefaultDnsTunnelId()
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ ERROR getting default DNS tunnel: ${e.message}", e)
                            null
                        }
                        
                        if (defaultDnsTunnel != null && vpnConnectionManager.isTunnelConnected(defaultDnsTunnel)) {
                            selectedTunnel = defaultDnsTunnel
                            Log.d(TAG, "   Strategy 2: Using default DNS tunnel: $defaultDnsTunnel")
                        }
                    }
                    
                    // Strategy 3: Fall back to first connected tunnel
                    if (selectedTunnel == null) {
                        selectedTunnel = allTunnels.firstOrNull { tunnelId ->
                            val isConnected = vpnConnectionManager.isTunnelConnected(tunnelId)
                            Log.d(TAG, "   Tunnel $tunnelId: ${if (isConnected) "✅ connected" else "❌ not connected"}")
                            isConnected
                        }
                        if (selectedTunnel != null) {
                            Log.d(TAG, "   Strategy 3: Falling back to first connected tunnel: $selectedTunnel")
                        }
                    }
                    
                    if (selectedTunnel != null) {
                        Log.i(TAG, "🌐 Routing DNS query from ${packetInfo.srcIp}:${packetInfo.srcPort} to tunnel $selectedTunnel (packet size: ${packet.size})")
                        vpnConnectionManager.sendPacketToTunnel(selectedTunnel, packet)
                        Log.d(TAG, "   ✅ DNS query sent to tunnel $selectedTunnel")
                        
                        // Register this DNS connection so future packets are tracked
                        // Use a dummy UID (-1) since we don't know the actual app UID for DNS queries
                        tracker.registerConnection(packetInfo.srcIp, packetInfo.srcPort, -1, selectedTunnel)
                        return
                    } else {
                        Log.e(TAG, "⚠️  DNS query received but no connected tunnels available - sending to direct internet")
                        Log.e(TAG, "   Available tunnels: ${allTunnels.joinToString()}")
                        sendToDirectInternet(packet)
                        return
                    }
                }
                
                Log.e(TAG, "🔍 DEBUG: connectionInfo is null, about to get registered packages")
                
                // Get all registered packages directly from ConnectionTracker
                // This is more reliable than getting UIDs first
                val registeredPackages = try {
                    tracker.getRegisteredPackages()
                } catch (e: Exception) {
                    Log.e(TAG, "❌ ERROR getting registered packages: ${e.message}", e)
                    emptySet()
                }
                
                Log.e(TAG, "🔍 Connection not tracked - checking ${registeredPackages.size} registered package(s) for packet from ${packetInfo.srcIp}:${packetInfo.srcPort} to ${packetInfo.destIp}:${packetInfo.destPort} (protocol ${packetInfo.protocol})")
                
                if (registeredPackages.isNotEmpty()) {
                    Log.e(TAG, "🔍 Found ${registeredPackages.size} registered package(s): ${registeredPackages.joinToString(", ")}, trying to match packet")
                    
                    // Try each registered package to find the one that matches
                    // Since we use addAllowedApplication, packets can only come from registered apps
                    for (packageName in registeredPackages) {
                        val uid = tracker.getUidForPackage(packageName)
                        if (uid != null) {
                            // Get the tunnel ID for this UID
                            var tunnelId = tracker.getTunnelIdForUid(uid)
                            
                            // If no tunnel ID yet, look up the rule
                            if (tunnelId == null) {
                                val appRule = kotlinx.coroutines.runBlocking {
                                    settingsRepository.getAppRuleByPackageName(packageName)
                                }
                                if (appRule != null && appRule.vpnConfigId != null) {
                                    val vpnConfig = kotlinx.coroutines.runBlocking {
                                        settingsRepository.getVpnConfigById(appRule.vpnConfigId!!)
                                    }
                                    if (vpnConfig != null) {
                                        tunnelId = "${vpnConfig.templateId}_${vpnConfig.regionId}"
                                        tracker.setUidToTunnel(uid, tunnelId)
                                        Log.d(TAG, "Found tunnel for $packageName: $tunnelId (via rule lookup)")
                                    }
                                }
                            }
                            
                            // Register this connection immediately so future packets are tracked
                            tracker.registerConnection(packetInfo.srcIp, packetInfo.srcPort, uid, tunnelId)
                            
                            if (tunnelId != null) {
                                // Route to tunnel
                                vpnConnectionManager.sendPacketToTunnel(tunnelId, packet)
                                Log.d(TAG, "✅ Registered and routed new connection from $packageName (UID $uid) to tunnel $tunnelId (dest: ${packetInfo.destIp}:${packetInfo.destPort}, protocol: ${packetInfo.protocol}, src: ${packetInfo.srcIp}:${packetInfo.srcPort})")
                                return
                            } else {
                                // No rule found - send to direct internet
                                Log.v(TAG, "No tunnel found for $packageName (UID $uid), sending to direct internet")
                                sendToDirectInternet(packet)
                                return
                            }
                        }
                    }
                } else {
                    Log.v(TAG, "No registered packages found - cannot route packet from ${packetInfo.srcIp}:${packetInfo.srcPort}")
                }
                
                // If we still can't determine UID, log and send to direct internet
                Log.v(TAG, "Could not determine UID for packet from ${packetInfo.srcIp}:${packetInfo.srcPort} to ${packetInfo.destIp}:${packetInfo.destPort} (protocol ${packetInfo.protocol}) - connection not tracked")
                sendToDirectInternet(packet)
                return
            }
            
            val uid = connectionInfo.uid
            val tunnelId = connectionInfo.tunnelId
            
            // If we have a tunnel ID, route directly to it
            if (tunnelId != null) {
                vpnConnectionManager.sendPacketToTunnel(tunnelId, packet)
                // PERFORMANCE: Removed per-packet verbose logging
                return
            }
            
            // Fallback: Get package name from UID and look up rule
            val packageNames = packageManager.getPackagesForUid(uid) ?: return
            
            if (packageNames.isEmpty()) {
                // System or unknown app, send to direct internet
                sendToDirectInternet(packet)
                return
            }
            
            // Use first package name (in case of shared UID)
            val packageName = packageNames[0]
            
            // Get rule from repository - use runBlocking for synchronous access
            // Note: In production, this should be done asynchronously with proper coroutine handling
            val appRule = kotlinx.coroutines.runBlocking {
                settingsRepository.getAppRuleByPackageName(packageName)
            }
            
            if (appRule != null && appRule.vpnConfigId != null) {
                // Rule found - route to VPN tunnel
                val vpnConfig = kotlinx.coroutines.runBlocking {
                    settingsRepository.getVpnConfigById(appRule.vpnConfigId!!)
                }
                if (vpnConfig != null) {
                    val ruleTunnelId = "${vpnConfig.templateId}_${vpnConfig.regionId}"
                    // Update connection tracker with tunnel ID for future packets
                    tracker.setUidToTunnel(uid, ruleTunnelId)
                    // Re-register connection with tunnel ID
                    tracker.registerConnection(packetInfo.srcIp, packetInfo.srcPort, uid, ruleTunnelId)
                    vpnConnectionManager.sendPacketToTunnel(ruleTunnelId, packet)
                    Log.v(TAG, "Routed packet from $packageName (UID $uid) to tunnel $ruleTunnelId")
                } else {
                    Log.w(TAG, "VPN config ${appRule.vpnConfigId} not found for $packageName")
                    sendToDirectInternet(packet)
                }
            } else {
                // No rule found - send to direct internet
                sendToDirectInternet(packet)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error routing packet", e)
            // On error, default to direct internet
            sendToDirectInternet(packet)
        }
    }
    
    private fun parsePacket(packet: ByteArray): PacketInfo? {
        if (packet.size < 20) {
            Log.v(TAG, "Packet too small: ${packet.size} bytes (min 20 for IPv4 header)")
            return null
        }
        
        try {
            val buffer = ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN)
            
            // Parse IP header
            val versionAndIHL = buffer.get(0).toInt() and 0xFF
            val version = versionAndIHL shr 4
            if (version != 4) {
                Log.v(TAG, "Packet not IPv4: version=$version (expected 4)")
                return null // Only IPv4 for now
            }
            
            val protocol = buffer.get(9).toInt() and 0xFF
            val srcIp = ByteArray(4)
            buffer.position(12)
            buffer.get(srcIp)
            val destIp = ByteArray(4)
            buffer.get(destIp)
            
            var srcPort = 0
            var destPort = 0
            
            // Parse TCP/UDP headers
            val ipHeaderLength = (versionAndIHL and 0x0F) * 4
            if (packet.size >= ipHeaderLength + 4) {
                buffer.position(ipHeaderLength)
                srcPort = buffer.short.toInt() and 0xFFFF
                destPort = buffer.short.toInt() and 0xFFFF
            }
            
            return PacketInfo(
                protocol = protocol,
                srcIp = InetAddress.getByAddress(srcIp),
                srcPort = srcPort,
                destIp = InetAddress.getByAddress(destIp),
                destPort = destPort
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing packet", e)
            return null
        }
    }
    
    private fun getConnectionOwnerUid(
        srcIp: InetAddress,
        srcPort: Int,
        destIp: InetAddress,
        destPort: Int,
        protocol: Int
    ): Int? {
        return try {
            // Use /proc/net to find the UID for this connection
            // Format: /proc/net/tcp for TCP (protocol 6), /proc/net/udp for UDP (protocol 17)
            readUidFromProcNet(srcIp.hostAddress ?: "", srcPort, destIp.hostAddress ?: "", destPort, protocol)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting connection owner UID", e)
            null
        }
    }
    
    private fun readUidFromProcNet(
        srcIp: String,
        srcPort: Int,
        destIp: String,
        destPort: Int,
        protocol: Int
    ): Int? {
        return ProcNetParser.readUidFromProcNet(srcIp, srcPort, destIp, destPort, protocol)
    }
    
    private fun sendToDirectInternet(packet: ByteArray) {
        try {
            // NOTE: When VPN is active, Android routes ALL traffic through TUN.
            // Writing to vpnOutput sends packets OUT of TUN interface to the network.
            // This should forward direct internet traffic correctly.
            //
            // The packet was read FROM TUN (outbound from app), and now we write
            // it BACK to TUN (which sends it to network, bypassing VPN processing).
            //
            // Potential issue: If this creates a loop, we'll need to track packet
            // sources to avoid re-routing packets we've already processed.
            
            vpnOutput?.write(packet)
            vpnOutput?.flush()
            Log.v(TAG, "Forwarded ${packet.size} bytes to direct internet via TUN")
        } catch (e: Exception) {
            Log.e(TAG, "Error forwarding packet to direct internet", e)
        }
    }
    
    data class PacketInfo(
        val protocol: Int,
        val srcIp: InetAddress,
        val srcPort: Int,
        val destIp: InetAddress,
        val destPort: Int
    )
    
    companion object {
        private const val TAG = "PacketRouter"
    }
}
