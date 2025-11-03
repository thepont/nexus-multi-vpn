package com.multiregionvpn.core

import android.content.Context
import android.net.VpnService
import android.util.Log
import com.multiregionvpn.core.vpnclient.OpenVpnClient
import com.multiregionvpn.core.vpnclient.NativeOpenVpnClient
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages multiple simultaneous OpenVPN connections.
 * Forwards packets from PacketRouter to the correct OpenVPN client.
 */
class VpnConnectionManager(
    private val context: Context? = null,
    private val vpnService: VpnService? = null,
    private val clientFactory: ((String) -> OpenVpnClient)? = null
) {
    private val connections = ConcurrentHashMap<String, OpenVpnClient>()
    private var packetReceiver: ((String, ByteArray) -> Unit)? = null
    
    private fun createClient(tunnelId: String): OpenVpnClient {
        // Use provided factory if available (for testing)
        if (clientFactory != null) {
            return clientFactory.invoke(tunnelId)
        }
        
        // In production, use NativeOpenVpnClient (OpenVPN 3 C++)
        if (context != null && vpnService != null) {
            return NativeOpenVpnClient(context, vpnService)
        }
        
        // Fallback (should not happen in production)
        Log.w(TAG, "No context/vpnService provided, cannot create real client")
        throw IllegalStateException("Context and VpnService required for real OpenVPN client")
    }
    
    /**
     * Sets a callback to receive packets from VPN tunnels.
     * Packets should be written back to the TUN interface.
     */
    fun setPacketReceiver(callback: (tunnelId: String, packet: ByteArray) -> Unit) {
        packetReceiver = callback
    }
    
    fun sendPacketToTunnel(tunnelId: String, packet: ByteArray) {
        val client = connections[tunnelId]
        if (client != null && client.isConnected()) {
            try {
                client.sendPacket(packet)
                Log.v(TAG, "Sent ${packet.size} bytes to tunnel $tunnelId")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending packet to tunnel $tunnelId", e)
            }
        } else {
            Log.w(TAG, "Tunnel $tunnelId not found or not connected")
        }
    }
    
    suspend fun createTunnel(tunnelId: String, ovpnConfig: String, authFilePath: String?): Boolean {
        Log.d(TAG, "createTunnel() called: tunnelId=$tunnelId, authFile=${authFilePath?.takeLast(20)}")
        
        if (connections.containsKey(tunnelId)) {
            Log.w(TAG, "Tunnel $tunnelId already exists")
            return connections[tunnelId]?.isConnected() == true
        }
        
        Log.d(TAG, "Creating OpenVPN client for tunnel $tunnelId...")
        val client = try {
            createClient(tunnelId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create OpenVPN client for tunnel $tunnelId", e)
            return false
        }
        
        Log.d(TAG, "OpenVPN client created for tunnel $tunnelId")
        
        // Set packet receiver callback
        client.setPacketReceiver { packet ->
            packetReceiver?.invoke(tunnelId, packet)
        }
        Log.d(TAG, "Packet receiver set for tunnel $tunnelId")
        
        // Connect
        Log.d(TAG, "Attempting to connect tunnel $tunnelId to OpenVPN server...")
        Log.d(TAG, "Config length: ${ovpnConfig.length} bytes")
        
        val connected = try {
            client.connect(ovpnConfig, authFilePath)
        } catch (e: Exception) {
            Log.e(TAG, "Exception during tunnel connection for $tunnelId", e)
            e.printStackTrace()
            false
        }
        
        if (connected) {
            connections[tunnelId] = client
            Log.i(TAG, "✅ Successfully created and connected tunnel: $tunnelId")
        } else {
            Log.e(TAG, "❌ Failed to create tunnel: $tunnelId - connect() returned false")
            Log.e(TAG, "This could mean:")
            Log.e(TAG, "  1. OpenVPN 3 native library isn't working")
            Log.e(TAG, "  2. VPN credentials are invalid")
            Log.e(TAG, "  3. VPN server is unreachable")
            Log.e(TAG, "  4. OpenVPN config is invalid")
        }
        
        return connected
    }
    
    suspend fun closeTunnel(tunnelId: String) {
        val client = connections[tunnelId]
        if (client != null) {
            client.disconnect()
            connections.remove(tunnelId)
            Log.d(TAG, "Closed tunnel: $tunnelId")
        }
    }
    
    suspend fun closeAll() {
        connections.values.forEach { 
            runBlocking { it.disconnect() }
        }
        connections.clear()
        Log.d(TAG, "Closed all tunnels")
    }
    
    fun isTunnelConnected(tunnelId: String): Boolean {
        return connections[tunnelId]?.isConnected() == true
    }
    
    companion object {
        private const val TAG = "VpnConnectionManager"
        @Volatile
        private var INSTANCE: VpnConnectionManager? = null
        
        /**
         * Gets or creates the singleton instance.
         * Note: This should be called after initialize() for production use.
         */
        fun getInstance(): VpnConnectionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: throw IllegalStateException(
                    "VpnConnectionManager not initialized. Call initialize() first."
                )
            }
        }
        
        /**
         * Initializes the VpnConnectionManager with Context and VpnService.
         * Should be called from VpnEngineService.
         */
        fun initialize(context: Context, vpnService: VpnService): VpnConnectionManager {
            return synchronized(this) {
                INSTANCE ?: VpnConnectionManager(context, vpnService).also { 
                    INSTANCE = it 
                }
            }
        }
        
        /**
         * Creates a test instance with a custom client factory.
         * For unit testing only.
         */
        fun createForTesting(clientFactory: (String) -> OpenVpnClient): VpnConnectionManager {
            return VpnConnectionManager(null, null, clientFactory)
        }
    }
}
