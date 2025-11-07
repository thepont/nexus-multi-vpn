package com.multiregionvpn.core.vpnclient

import android.content.Context
import android.net.VpnService
import android.util.Log
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import kotlinx.coroutines.*
import java.io.StringReader

/**
 * WireGuard VPN client implementation using GoBackend.
 * 
 * This adapter implements the OpenVpnClient interface to support WireGuard protocol.
 * Uses the official WireGuard Go backend for actual tunnel management.
 * 
 * Key advantages over OpenVPN:
 * - Simpler protocol (~4,000 lines of code vs ~100,000 for OpenVPN)
 * - Better performance (modern cryptography: Noise protocol, ChaCha20, Poly1305)
 * - Native multi-tunnel support
 * - Lower latency and better battery life
 * 
 * Protocol Support:
 * - WireGuard: Mullvad, IVPN, ProtonVPN, Surfshark, AzireVPN, OVPN
 * - NordLynx (NordVPN's WireGuard): Only via NordVPN's official app (no manual configs)
 * 
 * @param context Android context
 * @param vpnService VpnService instance (needed by GoBackend)
 * @param tunnelId Unique identifier for this tunnel
 */
class WireGuardVpnClient(
    private val context: Context,
    private val vpnService: VpnService,
    private val tunnelId: String
) : OpenVpnClient {
    
    companion object {
        private const val TAG = "WireGuardVpnClient"
        
        // Shared GoBackend instance (can handle multiple tunnels)
        @Volatile
        private var backend: GoBackend? = null
        
        @Synchronized
        fun getBackend(context: Context): GoBackend {
            if (backend == null) {
                backend = GoBackend(context)
                Log.d(TAG, "✅ GoBackend initialized")
            }
            return backend!!
        }
    }
    
    private var config: Config? = null
    private var tunnel: WireGuardTunnel? = null
    @Volatile private var isActive = false
    private var packetReceiver: ((ByteArray) -> Unit)? = null
    private val connectionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Callbacks (set by VpnConnectionManager via TunnelIpCallback/TunnelDnsCallback interfaces)
    var onConnectionStateChanged: ((String, Boolean) -> Unit)? = null
    var onTunnelIpReceived: ((String, String, Int) -> Unit)? = null
    var onTunnelDnsReceived: ((String, List<String>) -> Unit)? = null
    
    /**
     * Internal Tunnel implementation for GoBackend
     */
    private inner class WireGuardTunnel(private val name: String) : Tunnel {
        @Volatile
        private var state: Tunnel.State = Tunnel.State.DOWN
        
        override fun getName(): String = name
        
        override fun onStateChange(newState: Tunnel.State) {
            Log.i(TAG, "Tunnel $name state changed: ${state} -> $newState")
            state = newState
            
            when (newState) {
                Tunnel.State.UP -> {
                    isActive = true
                    onConnectionStateChanged?.invoke(tunnelId, true)
                    Log.i(TAG, "✅ Tunnel $name is UP")
                }
                Tunnel.State.DOWN -> {
                    isActive = false
                    onConnectionStateChanged?.invoke(tunnelId, false)
                    Log.i(TAG, "❌ Tunnel $name is DOWN")
                }
                Tunnel.State.TOGGLE -> {
                    // Toggle state not used in our implementation
                    Log.d(TAG, "⚠️  Tunnel $name received TOGGLE state (ignored)")
                }
            }
        }
        
        fun getState(): Tunnel.State = state
    }
    
    /**
     * Connect to WireGuard VPN.
     * 
     * @param ovpnConfig WireGuard configuration in standard format:
     * ```
     * [Interface]
     * PrivateKey = <base64-private-key>
     * Address = 10.2.0.2/32
     * DNS = 10.2.0.1
     * 
     * [Peer]
     * PublicKey = <base64-public-key>
     * Endpoint = vpn-server.example.com:51820
     * AllowedIPs = 0.0.0.0/0
     * PersistentKeepalive = 25
     * ```
     * 
     * @param authFilePath Not used by WireGuard (authentication via cryptographic keys)
     * @return true if connection successful, false otherwise
     */
    override suspend fun connect(ovpnConfig: String, authFilePath: String?): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "═══════════════════════════════════════════════════════")
                Log.i(TAG, "🔌 Connecting WireGuard tunnel: $tunnelId")
                Log.i(TAG, "═══════════════════════════════════════════════════════")
                
                // Parse WireGuard configuration
                val configStream = ovpnConfig.byteInputStream()
                val parsedConfig = Config.parse(configStream)
                this@WireGuardVpnClient.config = parsedConfig
                
                Log.d(TAG, "✅ WireGuard config parsed successfully for $tunnelId")
                
                // Extract IP and DNS information from config
                val interfaceConfig = parsedConfig.`interface`
                
                // Extract IP address and prefix length
                val addresses = interfaceConfig.addresses
                if (addresses.isNotEmpty()) {
                    val address = addresses.first()
                    val ip = address.address.hostAddress ?: "unknown"
                    val prefixLength = address.mask
                    Log.i(TAG, "📍 Tunnel IP: $ip/$prefixLength")
                    onTunnelIpReceived?.invoke(tunnelId, ip, prefixLength)
                }
                
                // Extract DNS servers
                val dnsServers = interfaceConfig.dnsServers.map { it.hostAddress ?: "" }
                if (dnsServers.isNotEmpty()) {
                    Log.i(TAG, "🌐 DNS servers: ${dnsServers.joinToString(", ")}")
                    onTunnelDnsReceived?.invoke(tunnelId, dnsServers)
                }
                
                // Create tunnel instance
                val wgTunnel = WireGuardTunnel(tunnelId)
                this@WireGuardVpnClient.tunnel = wgTunnel
                
                // Get GoBackend instance
                val goBackend = getBackend(context)
                
                Log.d(TAG, "🚀 Bringing up WireGuard tunnel using GoBackend...")
                
                // Bring up the tunnel using GoBackend
                // This call will:
                // 1. Establish the WireGuard connection
                // 2. Configure the VPN interface
                // 3. Start routing traffic through WireGuard
                val state = goBackend.setState(wgTunnel, Tunnel.State.UP, parsedConfig)
                
                if (state == Tunnel.State.UP) {
                    this@WireGuardVpnClient.isActive = true
                    Log.i(TAG, "✅ WireGuard tunnel $tunnelId connected successfully!")
                    Log.i(TAG, "   Using GoBackend for packet handling")
                    Log.i(TAG, "═══════════════════════════════════════════════════════")
                    true
                } else {
                    Log.e(TAG, "❌ Failed to bring up tunnel: state=$state")
                    this@WireGuardVpnClient.isActive = false
                    false
                }
                
            } catch (e: Exception) {
                // Check if it's a Backend.Exception
                if (e::class.simpleName == "Exception" && e.javaClass.declaringClass?.simpleName == "Backend") {
                    Log.e(TAG, "❌ GoBackend error for tunnel $tunnelId", e)
                } else {
                    Log.e(TAG, "❌ Failed to connect WireGuard tunnel $tunnelId", e)
                }
                Log.e(TAG, "   Error: ${e.message}")
                e.printStackTrace()
                this@WireGuardVpnClient.isActive = false
                onConnectionStateChanged?.invoke(tunnelId, false)
                false
            } catch (e: Throwable) {
                Log.e(TAG, "❌ Failed to connect WireGuard tunnel $tunnelId", e)
                e.printStackTrace()
                this@WireGuardVpnClient.isActive = false
                onConnectionStateChanged?.invoke(tunnelId, false)
                false
            }
        }
    }
    
    /**
     * Send a packet through the WireGuard tunnel.
     * 
     * Note: WireGuard handles packet I/O directly via the TUN interface.
     * Unlike OpenVPN which requires manual packet injection, WireGuard's
     * kernel module (or userspace implementation) reads/writes packets
     * automatically from the TUN device.
     * 
     * For multi-tunnel routing, we need to route packets to the correct
     * tunnel's TUN interface. This is handled by VpnEngineService.
     * 
     * @param packet Raw IP packet to send
     */
    override fun sendPacket(packet: ByteArray) {
        if (!isActive) {
            Log.w(TAG, "Cannot send packet: tunnel $tunnelId not active")
            return
        }
        
        // TODO: Implement packet injection for WireGuard
        // WireGuard typically handles this via TUN device directly
        // For multi-tunnel, we may need to use WireGuard's userspace backend
        // or multiple TUN devices (one per tunnel)
        
        Log.v(TAG, "Packet queued for tunnel $tunnelId (${packet.size} bytes)")
    }
    
    /**
     * Check if the WireGuard tunnel is connected.
     */
    override fun isConnected(): Boolean {
        return isActive
    }
    
    /**
     * Disconnect the WireGuard tunnel.
     */
    override suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "Disconnecting WireGuard tunnel: $tunnelId")
                
                val wgTunnel = tunnel
                if (wgTunnel != null) {
                    // Get GoBackend instance
                    val goBackend = getBackend(context)
                    
                    // Bring down the tunnel
                    val state = goBackend.setState(wgTunnel, Tunnel.State.DOWN, null)
                    
                    if (state == Tunnel.State.DOWN) {
                        Log.i(TAG, "✅ WireGuard tunnel $tunnelId disconnected")
                    } else {
                        Log.w(TAG, "⚠️ Tunnel state after disconnect: $state")
                    }
                } else {
                    Log.w(TAG, "No tunnel to disconnect for $tunnelId")
                }
                
                this@WireGuardVpnClient.isActive = false
                onConnectionStateChanged?.invoke(tunnelId, false)
                tunnel = null
                
                // Cancel any active coroutines
                connectionScope.cancel()
                
            } catch (e: Exception) {
                // Check if it's a Backend.Exception
                if (e::class.simpleName == "Exception" && e.javaClass.declaringClass?.simpleName == "Backend") {
                    Log.e(TAG, "❌ GoBackend error disconnecting tunnel $tunnelId", e)
                } else {
                    Log.e(TAG, "❌ Error disconnecting WireGuard tunnel $tunnelId", e)
                }
            }
        }
    }
    
    /**
     * Set a callback for receiving packets from the WireGuard tunnel.
     * The callback receives raw IP packets that should be written back to the TUN interface.
     */
    override fun setPacketReceiver(callback: (ByteArray) -> Unit) {
        packetReceiver = callback
    }
    
    /**
     * Set callbacks for connection state changes, IP assignment, and DNS configuration.
     * These are in addition to the OpenVpnClient interface methods.
     */
    fun setCallbacks(
        connectionStateCallback: (String, Boolean) -> Unit,
        tunnelIpCallback: (String, String, Int) -> Unit,
        tunnelDnsCallback: (String, List<String>) -> Unit
    ) {
        this.onConnectionStateChanged = connectionStateCallback
        this.onTunnelIpReceived = tunnelIpCallback
        this.onTunnelDnsReceived = tunnelDnsCallback
    }
    
    /**
     * Reconnect the WireGuard tunnel after a network change.
     * 
     * THE ZOMBIE TUNNEL BUG FIX (WireGuard Path):
     * Unlike OpenVPN which needs explicit reconnection, WireGuard's protocol is designed
     * to handle network roaming automatically through its handshake mechanism.
     * 
     * However, for consistency and to force an immediate handshake, we:
     * 1. Keep the tunnel up (WireGuard handles the rest automatically)
     * 2. Or optionally do a DOWN->UP cycle to force immediate reconnection
     * 
     * WireGuard advantages:
     * - Stateless protocol - automatically re-establishes connection after network change
     * - No explicit reconnect needed - next packet triggers handshake
     * - Built-in roaming support (designed for mobile networks)
     * 
     * For now, we'll do a soft reconnect (DOWN->UP) to ensure immediate recovery.
     */
    suspend fun reconnect() {
        withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "🔄 Reconnecting WireGuard tunnel: $tunnelId after network change")
                
                val wgTunnel = tunnel
                val currentConfig = config
                
                if (wgTunnel != null && currentConfig != null) {
                    val goBackend = getBackend(context)
                    
                    // Soft reconnect: DOWN -> UP
                    // This forces a new handshake with the server
                    Log.d(TAG, "   Step 1: Bringing tunnel DOWN...")
                    goBackend.setState(wgTunnel, Tunnel.State.DOWN, null)
                    
                    // Small delay to ensure clean shutdown
                    delay(100)
                    
                    Log.d(TAG, "   Step 2: Bringing tunnel UP...")
                    val state = goBackend.setState(wgTunnel, Tunnel.State.UP, currentConfig)
                    
                    if (state == Tunnel.State.UP) {
                        Log.i(TAG, "✅ WireGuard tunnel $tunnelId reconnected successfully")
                    } else {
                        Log.e(TAG, "❌ Failed to reconnect WireGuard tunnel: state=$state")
                    }
                } else {
                    Log.w(TAG, "⚠️  Cannot reconnect: tunnel or config is null")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error reconnecting WireGuard tunnel $tunnelId", e)
            }
        }
    }
}

