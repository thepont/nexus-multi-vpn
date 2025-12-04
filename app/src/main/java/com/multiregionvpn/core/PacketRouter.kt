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
                // We need to route DNS queries to any connected tunnel since DNS resolution is needed
                // for all apps using the VPN, and the DNS servers are only reachable through the tunnel
                if (packetInfo.protocol == 17 && packetInfo.destPort == 53) {
                    // This is a DNS query - route it to the first connected tunnel
                    // DNS queries should go through the VPN to use VPN DNS servers
                    Log.d(TAG, "🔍 DNS query detected: ${packetInfo.srcIp}:${packetInfo.srcPort} → ${packetInfo.destIp}:53")
                    val allTunnels = vpnConnectionManager.getAllTunnelIds()
                    Log.d(TAG, "   Available tunnels: ${allTunnels.joinToString()}")
                    val connectedTunnel = allTunnels.firstOrNull { tunnelId ->
                        val isConnected = vpnConnectionManager.isTunnelConnected(tunnelId)
                        Log.d(TAG, "   Tunnel $tunnelId: ${if (isConnected) "✅ connected" else "❌ not connected"}")
                        isConnected
                    }
                    
                    if (connectedTunnel != null) {
                        Log.i(TAG, "🌐 Routing DNS query from ${packetInfo.srcIp}:${packetInfo.srcPort} to tunnel $connectedTunnel (packet size: ${packet.size})")
                        vpnConnectionManager.sendPacketToTunnel(connectedTunnel, packet)
                        Log.d(TAG, "   ✅ DNS query sent to tunnel $connectedTunnel")
                        
                        // Register this DNS connection so future packets are tracked
                        // Use a dummy UID (-1) since we don't know the actual app UID for DNS queries
                        tracker.registerConnection(packetInfo.srcIp, packetInfo.srcPort, -1, connectedTunnel)
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
                        // Skip test packages that may not be accessible
                        if (packageName.startsWith("androidx.test.") || 
                            packageName.startsWith("android.test.") ||
                            packageName == "androidx.test.services") {
                            continue
                        }
                        
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
            val packageNames = try {
                packageManager.getPackagesForUid(uid)
            } catch (e: SecurityException) {
                Log.w(TAG, "Security exception getting packages for UID $uid: ${e.message} - sending to direct internet")
                sendToDirectInternet(packet)
                return
            } catch (e: Exception) {
                Log.w(TAG, "Error getting packages for UID $uid: ${e.message} - sending to direct internet", e)
                sendToDirectInternet(packet)
                return
            }
            
            if (packageNames == null || packageNames.isEmpty()) {
                // System or unknown app, send to direct internet
                sendToDirectInternet(packet)
                return
            }
            
            // Filter out test packages and use first valid package name (in case of shared UID)
            val validPackageNames = packageNames.filter { 
                !it.startsWith("androidx.test.") && 
                !it.startsWith("android.test.") &&
                it != "androidx.test.services"
            }
            
            if (validPackageNames.isEmpty()) {
                // Only test packages found, send to direct internet
                Log.v(TAG, "Only test packages found for UID $uid: ${packageNames.joinToString(", ")} - sending to direct internet")
                sendToDirectInternet(packet)
                return
            }
            
            // Use first valid package name
            val packageName = validPackageNames[0]
            
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
        // CRITICAL: In Global VPN mode, writing packets back to vpnOutput creates a routing loop:
        // App -> TUN -> readPacketsFromTun -> PacketRouter -> sendToDirectInternet -> vpnOutput (TUN) -> loop!
        //
        // THE FIX: For direct internet traffic in global VPN mode, we MUST drop the packet.
        // This allows Android to re-route the packet through the underlying network (not VPN).
        // 
        // HOW IT WORKS:
        // 1. App sends packet → TUN interface (because VPN is active globally)
        // 2. Our readPacketsFromTun() reads packet from TUN
        // 3. PacketRouter determines "no rule" → direct internet
        // 4. We DROP the packet here (don't write back to TUN)
        // 5. Android's network stack detects packet loss and retransmits via underlying network
        //
        // This is the standard approach for VPN apps that need to bypass VPN for certain traffic
        // when using global VPN mode. The packet gets dropped to break the VPN routing loop,
        // and the OS retransmits it via the normal network path.
        //
        // NOTE: In true split tunneling mode (with addAllowedApplication), this wouldn't happen
        // because only allowed apps' packets would enter the TUN interface in the first place.
        // But global VPN mode + PacketRouter requires this drop behavior.
        
        Log.v(TAG, "Dropped ${packet.size} bytes destined for direct internet (bypassing VPN in global mode)")
        // Intentionally do nothing - drop the packet
        // Android will retransmit via underlying network
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
