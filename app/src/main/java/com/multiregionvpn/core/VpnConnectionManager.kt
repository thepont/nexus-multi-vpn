package com.multiregionvpn.core

import android.content.Context
import android.net.VpnService
import android.util.Log
import com.multiregionvpn.core.vpnclient.OpenVpnClient
import com.multiregionvpn.core.vpnclient.NativeOpenVpnClient
import com.multiregionvpn.core.vpnclient.AuthenticationException
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
    private var tunFileDescriptor: Int = -1  // TUN file descriptor from VpnEngineService
    
    private fun createClient(tunnelId: String): OpenVpnClient {
        // Use provided factory if available (for testing)
        if (clientFactory != null) {
            return clientFactory.invoke(tunnelId)
        }
        
        // In production, use NativeOpenVpnClient (OpenVPN 3 C++)
        if (context != null && vpnService != null) {
            // Create client and pass the TUN file descriptor if available
            val client = NativeOpenVpnClient(context, vpnService, tunFileDescriptor)
            Log.d(TAG, "Created NativeOpenVpnClient with TUN FD: $tunFileDescriptor")
            return client
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
    
    /**
     * Sets the TUN file descriptor from the established VpnService interface.
     * This should be called by VpnEngineService after establishing the VPN interface.
     */
    fun setTunFileDescriptor(fd: Int) {
        tunFileDescriptor = fd
        Log.d(TAG, "TUN file descriptor set: $fd")
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
    
    /**
     * Checks if any OpenVPN tunnel is currently connected (fully established, not just connecting).
     * This is used to determine if VpnEngineService should stop reading from TUN
     * to avoid race conditions with OpenVPN 3's automatic packet I/O.
     * 
     * When OpenVPN 3 connections are established, they manage the TUN FD directly
     * and expect exclusive access. VpnEngineService should stop reading from TUN
     * to avoid packet interception race conditions.
     * 
     * NOTE: We check isConnected() which should only return true when fully connected,
     * not just when connecting. The connecting flag in C++ is used for status checks,
     * but isConnected() should only return true when session->connected is true.
     */
    fun hasConnectedTunnels(): Boolean {
        // Only count as connected if isConnected() returns true AND we have active connections
        // This ensures we only stop TUN reading when connections are actually established
        return connections.isNotEmpty() && connections.values.any { it.isConnected() }
    }
    
    /**
     * Result of tunnel creation attempt
     */
    data class TunnelCreationResult(
        val success: Boolean,
        val error: VpnError? = null
    )
    
    suspend fun createTunnel(tunnelId: String, ovpnConfig: String, authFilePath: String?): TunnelCreationResult {
        Log.d(TAG, "createTunnel() called: tunnelId=$tunnelId, authFile=${authFilePath?.takeLast(20)}")
        
        if (connections.containsKey(tunnelId)) {
            val isConnected = connections[tunnelId]?.isConnected() == true
            if (isConnected) {
                Log.d(TAG, "Tunnel $tunnelId already exists and is connected")
                return TunnelCreationResult(success = true)
            } else {
                Log.w(TAG, "Tunnel $tunnelId exists but is not connected, removing and recreating")
                connections.remove(tunnelId)
            }
        }
        
        Log.d(TAG, "Creating OpenVPN client for tunnel $tunnelId...")
        val client = try {
            createClient(tunnelId)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to create OpenVPN client for tunnel $tunnelId", e)
            val error = VpnError.fromException(e, tunnelId)
            Log.e(TAG, "Error type: ${error.type}, message: ${error.message}")
            return TunnelCreationResult(success = false, error = error)
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
        
        var connected: Boolean
        var connectionError: VpnError? = null
        
        try {
            connected = client.connect(ovpnConfig, authFilePath)
            
            if (!connected) {
                // Get detailed error from client if available
                val errorMessage = if (client is NativeOpenVpnClient) {
                    client.getLastError() ?: "Connection failed (unknown reason)"
                } else {
                    "Connection failed (unknown reason)"
                }
                
                // Determine error type from message
                connectionError = when {
                    errorMessage.contains("auth", ignoreCase = true) ||
                    errorMessage.contains("credential", ignoreCase = true) ||
                    errorMessage.contains("password", ignoreCase = true) ||
                    errorMessage.contains("username", ignoreCase = true) ||
                    errorMessage.contains("invalid", ignoreCase = true) -> {
                        VpnError(
                            type = VpnError.ErrorType.AUTHENTICATION_FAILED,
                            message = errorMessage,
                            details = "OpenVPN connection failed. Check credentials in settings.",
                            tunnelId = tunnelId
                        )
                    }
                    errorMessage.contains("connection", ignoreCase = true) ||
                    errorMessage.contains("timeout", ignoreCase = true) ||
                    errorMessage.contains("unreachable", ignoreCase = true) -> {
                        VpnError(
                            type = VpnError.ErrorType.CONNECTION_FAILED,
                            message = errorMessage,
                            details = "Could not reach VPN server. Check internet connection.",
                            tunnelId = tunnelId
                        )
                    }
                    errorMessage.contains("config", ignoreCase = true) ||
                    errorMessage.contains("parse", ignoreCase = true) -> {
                        VpnError(
                            type = VpnError.ErrorType.CONFIG_ERROR,
                            message = errorMessage,
                            details = "Invalid VPN configuration. Try re-adding the server.",
                            tunnelId = tunnelId
                        )
                    }
                    else -> {
                        VpnError(
                            type = VpnError.ErrorType.TUNNEL_ERROR,
                            message = errorMessage,
                            details = "Tunnel creation failed.",
                            tunnelId = tunnelId
                        )
                    }
                }
                
                Log.e(TAG, "❌ Failed to create tunnel: $tunnelId")
                Log.e(TAG, "   Error type: ${connectionError.type}")
                Log.e(TAG, "   Error message: ${connectionError.message}")
                if (connectionError.details != null) {
                    Log.e(TAG, "   Error details: ${connectionError.details}")
                }
            } else {
                connectionError = null
            }
        } catch (e: com.multiregionvpn.core.vpnclient.AuthenticationException) {
            // Authentication exception - provide clear error
            connected = false
            connectionError = VpnError(
                type = VpnError.ErrorType.AUTHENTICATION_FAILED,
                message = e.message ?: "Authentication failed",
                details = "OpenVPN authentication failed. Please check your NordVPN Service Credentials in settings.",
                tunnelId = tunnelId
            )
            Log.e(TAG, "❌ Authentication failed for tunnel $tunnelId", e)
            Log.e(TAG, "   ${connectionError.getUserMessage()}")
        } catch (e: Exception) {
            connected = false
            connectionError = VpnError.fromException(e, tunnelId)
            Log.e(TAG, "❌ Exception during tunnel connection for $tunnelId", e)
            Log.e(TAG, "   Error type: ${connectionError.type}")
            Log.e(TAG, "   Error message: ${connectionError.message}")
            e.printStackTrace()
        }
        
        if (connected) {
            connections[tunnelId] = client
            Log.i(TAG, "✅ Successfully created and connected tunnel: $tunnelId")
            return TunnelCreationResult(success = true)
        } else {
            Log.e(TAG, "❌ Failed to create tunnel: $tunnelId")
            Log.e(TAG, "   Error type: ${connectionError?.type}")
            Log.e(TAG, "   Error message: ${connectionError?.message}")
            return TunnelCreationResult(success = false, error = connectionError)
        }
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
