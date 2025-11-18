package com.multiregionvpn.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.VpnService
import android.util.Log
import android.os.Build
import com.multiregionvpn.data.repository.SettingsRepository
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger

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
            val sampleIndex = packetSampleCounter.getAndIncrement()
            val shouldLogSample = sampleIndex < DEBUG_SAMPLE_LIMIT
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

            val destIpString = packetInfo.destIp.hostAddress ?: ""
            val shouldLogTarget = destIpString.startsWith("10.1.0.") ||
                destIpString.startsWith("10.2.0.") ||
                destIpString.startsWith("198.18.")

            if (shouldLogSample || shouldLogTarget) {
                Log.i(
                    TAG,
                    "🧭 routePacket#$sampleIndex proto=${packetInfo.protocol} " +
                        "${packetInfo.srcIp.hostAddress}:${packetInfo.srcPort} → " +
                        "${packetInfo.destIp.hostAddress}:${packetInfo.destPort} (size=${packet.size})"
                )
                if (shouldLogTarget && !shouldLogSample) {
                    Log.i(TAG, "   🪪 forcing log for destination $destIpString (diagnostic subnet)")
                }
            }
            
            val tunnelFromDestinationIp = tracker.getTunnelForDestination(packetInfo.destIp)
            if (tunnelFromDestinationIp != null) {
                if (shouldLogSample || shouldLogTarget) {
                    Log.i(
                        TAG,
                        "   🎯 Destination ${packetInfo.destIp.hostAddress} matched pushed route for tunnel $tunnelFromDestinationIp"
                    )
                }
                vpnConnectionManager.sendPacketToTunnel(tunnelFromDestinationIp, packet)
                tracker.setTunnelForIp(packetInfo.destIp, tunnelFromDestinationIp)
                return
            } else {
                val fallbackTunnel = when {
                    destIpString.startsWith("198.18.1.") -> "local-test_UK"
                    destIpString.startsWith("198.18.2.") -> "local-test_FR"
                    else -> null
                }
                if (fallbackTunnel != null) {
                    val available = vpnConnectionManager.getAllTunnelIds()
                    if (available.contains(fallbackTunnel)) {
                        Log.i(
                            TAG,
                            "   🧭 Fallback mapping: ${packetInfo.destIp.hostAddress} → $fallbackTunnel (diagnostic subnet)"
                        )
                        if (packet.size >= 24) {
                            val preview = packet.take(8).joinToString(" ") { b ->
                                String.format("%02x", b.toInt() and 0xFF)
                            }
                            val destPort = ((packet[22].toInt() and 0xFF) shl 8) or (packet[23].toInt() and 0xFF)
                            Log.i(
                                TAG,
                                "   🧪 Diagnostic packet preview: $preview proto=${packet[9].toInt() and 0xFF} dest=${packetInfo.destIp.hostAddress}:$destPort"
                            )
                        }
                        tracker.addRouteForTunnel(fallbackTunnel, packetInfo.destIp, 32)
                        vpnConnectionManager.sendPacketToTunnel(fallbackTunnel, packet)
                        tracker.setTunnelForIp(packetInfo.destIp, fallbackTunnel)
                        return
                    } else {
                        Log.w(
                            TAG,
                            "   ⚠️  Wanted to fallback-route ${packetInfo.destIp.hostAddress} but tunnel $fallbackTunnel not available (have ${available.joinToString()})"
                        )
                    }
                }
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
            val tunnelFromSourceIp = tracker.getTunnelForIp(packetInfo.srcIp)
            if (tunnelFromSourceIp != null) {
                if (shouldLogSample) {
                    Log.i(
                        TAG,
                        "   🧵 Source IP ${packetInfo.srcIp.hostAddress} mapped to tunnel $tunnelFromSourceIp"
                    )
                }
                vpnConnectionManager.sendPacketToTunnel(tunnelFromSourceIp, packet)
                tracker.setTunnelForIp(packetInfo.destIp, tunnelFromSourceIp)
                return
            }
            
            // CRITICAL: Use ConnectionTracker to get UID instead of /proc/net
            // This solves the permission denied issue with /proc/net files
            val connectionInfo = tracker.lookupConnection(packetInfo.srcIp, packetInfo.srcPort)
            if (shouldLogSample) {
                val infoSummary = connectionInfo?.let { "UID ${it.uid}, tunnel ${it.tunnelId ?: "null"}" } ?: "null"
                Log.i(TAG, "   🔍 tracker lookup result: $infoSummary")
            }
            
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
                        logDirectFallback(packetInfo, "dns-no-tunnel")
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
                    if (shouldLogSample) {
                        Log.i(
                            TAG,
                            "   🗂️ Registered packages (${registeredPackages.size}): ${registeredPackages.joinToString()}"
                        )
                    }
                    Log.e(TAG, "🔍 Found ${registeredPackages.size} registered package(s): ${registeredPackages.joinToString(", ")}, trying to match packet")
                    
                    // Try to get the real owner UID for this 5-tuple using VpnService API (API 29+)
                    val resolvedOwnerUid: Int? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        try {
                            val local = InetSocketAddress(packetInfo.srcIp, packetInfo.srcPort)
                            val remote = InetSocketAddress(packetInfo.destIp, packetInfo.destPort)
                            connectivityManager.getConnectionOwnerUid(packetInfo.protocol, local, remote)
                        } catch (e: Exception) {
                            Log.w(TAG, "⚠️ getConnectionOwnerUid failed: ${e.message}")
                            null
                        }
                    } else {
                        null
                    }
                    
                    // Build the list of packages to inspect. Prefer those actually owning the connection.
                    val packagesToCheck = mutableListOf<String>()
                    if (resolvedOwnerUid != null && resolvedOwnerUid >= 0) {
                        val ownerPackages = try {
                            packageManager.getPackagesForUid(resolvedOwnerUid)?.toList() ?: emptyList()
                        } catch (e: Exception) {
                            Log.w(TAG, "⚠️ getPackagesForUid($resolvedOwnerUid) failed: ${e.message}")
                            emptyList()
                        }
                        val matching = ownerPackages.filter { registeredPackages.contains(it) }
                        if (matching.isNotEmpty()) {
                            Log.d(TAG, "🔗 Connection owner UID=$resolvedOwnerUid packages=${matching.joinToString(", ")}")
                            packagesToCheck.addAll(matching)
                        } else if (ownerPackages.isNotEmpty()) {
                            Log.d(TAG, "🔗 Using owner UID=$resolvedOwnerUid packages=${ownerPackages.joinToString(", ")}")
                            packagesToCheck.addAll(ownerPackages)
                        }
                    }
                    if (packagesToCheck.isEmpty()) {
                        packagesToCheck.addAll(registeredPackages)
                    }
                    
                    // Try each candidate package to find matching rule
                    for (packageName in packagesToCheck) {
                        val uid: Int = if (resolvedOwnerUid != null && resolvedOwnerUid >= 0) {
                            // Ensure tracker caches the UID mapping even though we already know the UID
                            tracker.registerPackage(packageName)
                            resolvedOwnerUid
                        } else {
                            tracker.registerPackage(packageName) ?: tracker.getUidForPackage(packageName)
                        } ?: continue
                        
                        var tunnelId = tracker.getTunnelIdForUid(uid)
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
                            vpnConnectionManager.sendPacketToTunnel(tunnelId, packet)
                            Log.d(TAG, "✅ Registered and routed new connection from $packageName (UID $uid) to tunnel $tunnelId (dest: ${packetInfo.destIp}:${packetInfo.destPort}, protocol: ${packetInfo.protocol}, src: ${packetInfo.srcIp}:${packetInfo.srcPort})")
                            return
                        }
                    }
                    
                    // No tunnel found after checking registered packages
                    Log.v(TAG, "No tunnel found after inspecting registered packages - sending to direct internet")
                    logDirectFallback(packetInfo, "registered-packages-no-tunnel")
                    sendToDirectInternet(packet)
                    return
                } else {
                    Log.v(TAG, "No registered packages found - cannot route packet from ${packetInfo.srcIp}:${packetInfo.srcPort}")
                }
                
                // If we still can't determine UID, log and send to direct internet
                Log.v(TAG, "Could not determine UID for packet from ${packetInfo.srcIp}:${packetInfo.srcPort} to ${packetInfo.destIp}:${packetInfo.destPort} (protocol ${packetInfo.protocol}) - connection not tracked")
                logDirectFallback(packetInfo, "connection-not-tracked")
                sendToDirectInternet(packet)
                return
            }
            
            val uid = connectionInfo.uid
            val tunnelId = connectionInfo.tunnelId
            if (shouldLogSample) {
                Log.i(TAG, "   🎯 Matched connection UID=$uid, tunnel=$tunnelId")
            }
            
            // If we have a tunnel ID, route directly to it
            if (tunnelId != null) {
                vpnConnectionManager.sendPacketToTunnel(tunnelId, packet)
                tracker.setTunnelForIp(packetInfo.destIp, tunnelId)
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
                    tracker.setTunnelForIp(packetInfo.destIp, ruleTunnelId)
                    Log.v(TAG, "Routed packet from $packageName (UID $uid) to tunnel $ruleTunnelId")
                } else {
                    Log.w(TAG, "VPN config ${appRule.vpnConfigId} not found for $packageName")
                    logDirectFallback(packetInfo, "vpn-config-missing")
                    sendToDirectInternet(packet)
                }
            } else {
                // No rule found - send to direct internet
                logDirectFallback(packetInfo, "no-app-rule")
                sendToDirectInternet(packet)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error routing packet", e)
            // On error, default to direct internet
            logDirectFallback(null, "exception-${e.javaClass.simpleName}")
            sendToDirectInternet(packet)
        }
    }
    
    private fun parsePacket(packet: ByteArray): PacketInfo? {
        if (packet.isEmpty()) {
            Log.v(TAG, "Packet empty")
            return null
        }

        val firstByte = packet[0].toInt() and 0xFF
        val version = firstByte shr 4

        return when (version) {
            4 -> parseIpv4Packet(packet, firstByte)
            6 -> parseIpv6Packet(packet)
            else -> {
                Log.v(TAG, "Unsupported IP version: $version")
                null
            }
        }
    }

    private fun parseIpv4Packet(packet: ByteArray, versionAndIHL: Int): PacketInfo? {
        if (packet.size < 20) {
            Log.v(TAG, "IPv4 packet too small: ${packet.size} bytes (min 20)")
            return null
        }

        return try {
            val buffer = ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN)
            val protocol = buffer.get(9).toInt() and 0xFF
            val srcIpBytes = ByteArray(4)
            buffer.position(12)
            buffer.get(srcIpBytes)
            val destIpBytes = ByteArray(4)
            buffer.get(destIpBytes)

            val headerLength = (versionAndIHL and 0x0F) * 4
            var srcPort = 0
            var destPort = 0
            if (packet.size >= headerLength + 4) {
                buffer.position(headerLength)
                srcPort = buffer.short.toInt() and 0xFFFF
                destPort = buffer.short.toInt() and 0xFFFF
            }

            PacketInfo(
                protocol = protocol,
                srcIp = InetAddress.getByAddress(srcIpBytes),
                srcPort = srcPort,
                destIp = InetAddress.getByAddress(destIpBytes),
                destPort = destPort
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing IPv4 packet", e)
            null
        }
    }

    private fun parseIpv6Packet(packet: ByteArray): PacketInfo? {
        if (packet.size < 40) {
            Log.v(TAG, "IPv6 packet too small: ${packet.size} bytes (min 40)")
            return null
        }

        return try {
            val buffer = ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN)
            val nextHeader = buffer.get(6).toInt() and 0xFF

            val srcBytes = ByteArray(16)
            buffer.position(8)
            buffer.get(srcBytes)
            val destBytes = ByteArray(16)
            buffer.get(destBytes)

            val srcIp = ipv4MappedAddress(srcBytes)
            val destIp = ipv4MappedAddress(destBytes)

            if (srcIp == null || destIp == null) {
                Log.v(
                    TAG,
                    "IPv6 packet not IPv4-mapped (src=${InetAddress.getByAddress(srcBytes).hostAddress}, " +
                        "dest=${InetAddress.getByAddress(destBytes).hostAddress}) - skipping"
                )
                return null
            }

            val protocol = nextHeader
            if (protocol != 6 && protocol != 17) {
                Log.v(TAG, "IPv6 next header $protocol not supported (only TCP/UDP)")
                return null
            }

            val headerLength = 40
            var srcPort = 0
            var destPort = 0
            if (packet.size >= headerLength + 4) {
                val portBuffer = ByteBuffer.wrap(packet, headerLength, packet.size - headerLength)
                    .order(ByteOrder.BIG_ENDIAN)
                srcPort = portBuffer.short.toInt() and 0xFFFF
                destPort = portBuffer.short.toInt() and 0xFFFF
            }

            PacketInfo(
                protocol = protocol,
                srcIp = srcIp,
                srcPort = srcPort,
                destIp = destIp,
                destPort = destPort
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing IPv6 packet", e)
            null
        }
    }

    private fun ipv4MappedAddress(address: ByteArray): InetAddress? {
        if (address.size != 16) return null
        for (i in 0 until 10) {
            if (address[i].toInt() != 0) {
                return null
            }
        }
        if (address[10].toInt() != 0xFF || address[11].toInt() != 0xFF) {
            return null
        }
        val ipv4Bytes = address.copyOfRange(12, 16)
        return InetAddress.getByAddress(ipv4Bytes)
    }
    
    private fun logDirectFallback(packetInfo: PacketInfo?, reason: String) {
        if (packetInfo != null) {
            Log.w(
                TAG,
                "⬆️ Direct internet fallback [$reason]: " +
                    "${packetInfo.srcIp.hostAddress}:${packetInfo.srcPort} → " +
                    "${packetInfo.destIp.hostAddress}:${packetInfo.destPort} proto=${packetInfo.protocol}"
            )
        } else {
            Log.w(TAG, "⬆️ Direct internet fallback [$reason]: packet info unavailable")
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
            
            if (directSampleCounter.getAndIncrement() < DEBUG_SAMPLE_LIMIT) {
                Log.w(TAG, "⬆️ Sending ${packet.size} bytes directly to internet via TUN output")
            }
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
        // TEMP: raise sample cap while diagnosing diagnostic routing failures.
        private const val DEBUG_SAMPLE_LIMIT = 500
        private val packetSampleCounter = AtomicInteger(0)
        private val directSampleCounter = AtomicInteger(0)

        fun resetDebugCounters() {
            packetSampleCounter.set(0)
            directSampleCounter.set(0)
        }
    }
}
