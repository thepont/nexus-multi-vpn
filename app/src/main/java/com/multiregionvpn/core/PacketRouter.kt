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
    private val vpnOutput: java.io.FileOutputStream? = null // For writing packets back to TUN interface
) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val packageManager = context.packageManager
    
    fun routePacket(packet: ByteArray) {
        try {
            // Parse packet to get 5-tuple
            val packetInfo = parsePacket(packet) ?: return
            
            // Get UID for this connection
            val uid = getConnectionOwnerUid(
                packetInfo.srcIp,
                packetInfo.srcPort,
                packetInfo.destIp,
                packetInfo.destPort,
                packetInfo.protocol
            )
            
            if (uid == null) {
                // If we can't determine UID, log and send to direct internet
                Log.v(TAG, "Could not determine UID for packet from ${packetInfo.srcIp}:${packetInfo.srcPort}")
                sendToDirectInternet(packet)
                return
            }
            
            // Get package name from UID
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
                    val tunnelId = "${vpnConfig.templateId}_${vpnConfig.regionId}"
                    vpnConnectionManager.sendPacketToTunnel(tunnelId, packet)
                    Log.v(TAG, "Routed packet from $packageName to tunnel $tunnelId")
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
        if (packet.size < 20) return null
        
        try {
            val buffer = ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN)
            
            // Parse IP header
            val versionAndIHL = buffer.get(0).toInt() and 0xFF
            val version = versionAndIHL shr 4
            if (version != 4) return null // Only IPv4 for now
            
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
