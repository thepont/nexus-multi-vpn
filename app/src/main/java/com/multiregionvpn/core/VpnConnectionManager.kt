package com.multiregionvpn.core

import android.content.Context
import android.net.VpnService
import android.util.Log
import com.multiregionvpn.core.vpnclient.OpenVpnClient
import com.multiregionvpn.core.vpnclient.NativeOpenVpnClient
import com.multiregionvpn.core.vpnclient.WireGuardVpnClient
import com.multiregionvpn.core.vpnclient.AuthenticationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    // Load native library for socketpair creation
    init {
        try {
            System.loadLibrary("openvpn-jni")
            Log.d(TAG, "Native library loaded for socketpair support")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library", e)
        }
    }
    private val connections = ConcurrentHashMap<String, OpenVpnClient>()
    private var packetReceiver: ((String, ByteArray) -> Unit)? = null
    private var baseTunFileDescriptor: Int = -1  // Base TUN file descriptor from VpnEngineService (will be duplicated per connection)
    private var vpnInterface: android.os.ParcelFileDescriptor? = null  // Original PFD for duplicating
    private var connectionStateListener: ((Boolean) -> Unit)? = null  // true = has connecting connections, false = all connected or none connecting
    // Socketpair file descriptors (SOCK_SEQPACKET for packet-oriented TUN emulation)
    private val pipeWriteFds = ConcurrentHashMap<String, Int>()  // tunnelId -> socketpair Kotlin-side FD for writing packets
    private val pipeWritePfds = ConcurrentHashMap<String, android.os.ParcelFileDescriptor>()  // tunnelId -> PFD for Kotlin FD (keep open, used for both read and write)
    // REMOVED pipeReadPfds - was creating duplicate PFD from same FD, causing double-close and FD leaks
    private val pipeWriters = ConcurrentHashMap<String, java.io.FileOutputStream>()  // tunnelId -> socket writer (for sending to OpenVPN)
    private val pipeReaders = ConcurrentHashMap<String, kotlinx.coroutines.Job>()  // tunnelId -> socket reader coroutine job (for receiving from OpenVPN)
    private val pipeReaderScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
    
    private var tunnelIpCallback: ((String, String, Int) -> Unit)? = null  // Callback for tunnel IP addresses
    private var tunnelDnsCallback: ((String, List<String>) -> Unit)? = null  // Callback for DNS servers
    
    // Packet queueing for tunnels that are connecting but not yet ready
    private data class QueuedPacket(val packet: ByteArray, val timestamp: Long)
    private val packetQueues = ConcurrentHashMap<String, java.util.concurrent.ConcurrentLinkedQueue<QueuedPacket>>()
    private val QUEUE_TIMEOUT_MS = 10000L  // Drop packets after 10 seconds (increased for slower connections)
    private val MAX_QUEUE_SIZE = 10000  // Max packets to queue per tunnel (~15MB for avg 1500-byte packets)
    
    // Tunnel readiness tracking (for comprehensive routing readiness checks)
    private data class TunnelReadinessState(
        var ipAssigned: Boolean = false,
        var dnsConfigured: Boolean = false
    )
    private val tunnelReadinessStates = ConcurrentHashMap<String, TunnelReadinessState>()
    
    /**
     * Sets a callback to receive tunnel IP addresses when OpenVPN assigns them via DHCP.
     */
    fun setTunnelIpCallback(callback: (tunnelId: String, ip: String, prefixLength: Int) -> Unit) {
        tunnelIpCallback = callback
    }
    
    /**
     * Sets a callback to receive DNS servers when OpenVPN pushes them via DHCP options.
     */
    fun setTunnelDnsCallback(callback: (tunnelId: String, dnsServers: List<String>) -> Unit) {
        tunnelDnsCallback = callback
    }
    
    /**
     * Detect VPN protocol from configuration content.
     * 
     * @param config The VPN configuration string
     * @return "wireguard" or "openvpn"
     */
    private fun detectProtocol(config: String): String {
        val trimmedConfig = config.trimStart()
        
        // WireGuard configs start with [Interface] or [Peer]
        if (trimmedConfig.startsWith("[Interface]") || trimmedConfig.startsWith("[Peer]")) {
            Log.i(TAG, "Detected protocol: WireGuard")
            return "wireguard"
        }
        
        // OpenVPN configs contain keywords like 'client', 'remote', 'proto', etc.
        val openVpnKeywords = listOf("client", "remote ", "proto ", "<ca>", "auth-user-pass")
        if (openVpnKeywords.any { trimmedConfig.contains(it, ignoreCase = true) }) {
            Log.i(TAG, "Detected protocol: OpenVPN")
            return "openvpn"
        }
        
        // Default to OpenVPN if detection fails
        Log.w(TAG, "Could not detect protocol, defaulting to OpenVPN")
        return "openvpn"
    }
    
    private fun createClient(tunnelId: String): OpenVpnClient {
        // Use provided factory if available (for testing)
        if (clientFactory != null) {
            return clientFactory.invoke(tunnelId)
        }
        
        // In production, use NativeOpenVpnClient (OpenVPN 3 C++)
            if (context != null && vpnService != null) {
                // Create callback objects that implement TunnelIpCallback and TunnelDnsCallback interfaces
                val ipCallback = object : com.multiregionvpn.core.vpnclient.TunnelIpCallback {
                    override fun onTunnelIpReceived(tunnelId: String, ip: String, prefixLength: Int) {
                        Log.d(TAG, "Tunnel IP received: tunnelId=$tunnelId, ip=$ip/$prefixLength")
                        
                        // Mark IP as assigned for readiness tracking
                        val readinessState = tunnelReadinessStates.getOrPut(tunnelId) { TunnelReadinessState() }
                        readinessState.ipAssigned = true
                        Log.i(TAG, "✅ Tunnel $tunnelId: IP assigned (readiness: ${getTunnelReadinessStatus(tunnelId)})")
                        
                        // Forward to VpnEngineService callback
                        tunnelIpCallback?.invoke(tunnelId, ip, prefixLength)
                    }
                }
                
                val dnsCallback = object : com.multiregionvpn.core.vpnclient.TunnelDnsCallback {
                    override fun onTunnelDnsReceived(tunnelId: String, dnsServers: List<String>) {
                        Log.d(TAG, "Tunnel DNS servers received: tunnelId=$tunnelId, dnsServers=$dnsServers")
                        
                        // Mark DNS as configured for readiness tracking
                        val readinessState = tunnelReadinessStates.getOrPut(tunnelId) { TunnelReadinessState() }
                        readinessState.dnsConfigured = true
                        Log.i(TAG, "✅ Tunnel $tunnelId: DNS configured (readiness: ${getTunnelReadinessStatus(tunnelId)})")
                        
                        // Forward to VpnEngineService callback
                        tunnelDnsCallback?.invoke(tunnelId, dnsServers)
                    }
                }
                
                // CRITICAL: Create socketpairs (SOCK_SEQPACKET) for each tunnel to enable packet filtering
                // Since we can't filter packets from the same TUN device, we create separate socketpairs
                // for each tunnel. We read from TUN ourselves, route packets, and write to the
                // appropriate socketpair. OpenVPN 3 reads from its socketpair (packet-oriented, emulates TUN).
                val connectionFd = try {
                    // Create socketpair using JNI (native code)
                    // Returns the OpenVPN 3 side FD (packet-oriented, SOCK_SEQPACKET)
                    val pipeReadFd = createPipe(tunnelId)
                    if (pipeReadFd >= 0) {
                            // Get the Kotlin FD (socket pair is bidirectional - same FD for read/write)
                            val kotlinFd = getPipeWriteFd(tunnelId)
                            
                            if (kotlinFd >= 0) {
                                Log.d(TAG, "✅ Created socket pair for tunnel $tunnelId")
                                Log.d(TAG, "   OpenVPN 3 FD: $pipeReadFd (bidirectional)")
                                Log.d(TAG, "   Kotlin FD: $kotlinFd (bidirectional)")
                                
                                // Store pipe FDs and PFDs (keep PFDs open)
                                // CRITICAL FIX: Only create ONE PFD per FD to avoid double-close issues
                                pipeWriteFds[tunnelId] = kotlinFd
                                val pfd = android.os.ParcelFileDescriptor.fromFd(kotlinFd)
                                pipeWritePfds[tunnelId] = pfd
                                // Don't create a second PFD from same FD - reuse the same PFD for read/write
                                // pipeReadPfds[tunnelId] = pfd  // REMOVED - was causing double-close
                                
                                // Start pipe reader coroutine to read response packets from OpenVPN 3
                                // OpenVPN 3 writes responses to socket pair, we read them and forward to TUN
                                startPipeReader(tunnelId, kotlinFd)
                                
                                pipeReadFd
                            } else {
                            Log.w(TAG, "Failed to get socketpair FDs for tunnel $tunnelId, falling back to TUN FD duplication")
                            // Fallback to TUN FD duplication if socketpair creation fails
                            if (baseTunFileDescriptor >= 0 && vpnInterface != null) {
                                try {
                                    val duplicatedPfd = vpnInterface!!.dup()
                                    val duplicatedFd = duplicatedPfd.detachFd()
                                    if (duplicatedFd >= 0) {
                                        duplicatedFd
                                    } else {
                                        baseTunFileDescriptor
                                    }
                                } catch (e: Exception) {
                                    baseTunFileDescriptor
                                }
                            } else {
                                -1
                            }
                        }
                    } else {
                        // Fallback if socketpair creation failed
                        Log.w(TAG, "Failed to create socketpair for tunnel $tunnelId, falling back to TUN FD duplication")
                        if (baseTunFileDescriptor >= 0 && vpnInterface != null) {
                            try {
                                val duplicatedPfd = vpnInterface!!.dup()
                                val duplicatedFd = duplicatedPfd.detachFd()
                                if (duplicatedFd >= 0) {
                                    duplicatedFd
                                } else {
                                    baseTunFileDescriptor
                                }
                            } catch (e: Exception) {
                                baseTunFileDescriptor
                            }
                        } else {
                            -1
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error creating socketpair for tunnel $tunnelId: ${e.message}", e)
                    // Fallback to TUN FD duplication
                    val fallbackFd = if (baseTunFileDescriptor >= 0 && vpnInterface != null) {
                        try {
                            val duplicatedPfd = vpnInterface!!.dup()
                            val duplicatedFd = duplicatedPfd.detachFd()
                            if (duplicatedFd >= 0) {
                                duplicatedFd
                            } else {
                                baseTunFileDescriptor
                            }
                        } catch (e2: Exception) {
                            baseTunFileDescriptor
                        }
                    } else {
                        -1
                    }
                    fallbackFd
                }
                
                // Create client and pass the duplicated TUN file descriptor, tunnel ID, and callbacks
                val client = NativeOpenVpnClient(
                    context, 
                    vpnService, 
                    connectionFd,
                    tunnelId,  // Pass tunnel ID
                    ipCallback,   // Pass callback for IP addresses
                    dnsCallback   // Pass callback for DNS servers
                )
            Log.d(TAG, "Created NativeOpenVpnClient with TUN FD: $connectionFd, tunnelId: $tunnelId")
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
     * Sets a callback to be notified when connection state changes.
     * Called with true when connections start connecting, false when all are connected or none connecting.
     * Used by VpnEngineService to pause/resume TUN packet reading.
     */
    fun setConnectionStateListener(callback: (Boolean) -> Unit) {
        connectionStateListener = callback
    }
    
    /**
     * Sets the TUN file descriptor from the established VpnService interface.
     * This should be called by VpnEngineService after establishing the VPN interface.
     * 
     * CRITICAL: We store both the integer FD and the ParcelFileDescriptor.
     * Each connection will get its own duplicated FD to avoid I/O conflicts.
     */
    fun setTunFileDescriptor(fd: Int, pfd: android.os.ParcelFileDescriptor? = null) {
        baseTunFileDescriptor = fd
        vpnInterface = pfd
        Log.d(TAG, "Base TUN file descriptor set: $fd (will be duplicated per connection)")
    }
    
    fun sendPacketToTunnel(tunnelId: String, packet: ByteArray) {
        val client = connections[tunnelId]
        if (client != null && client.isConnected()) {
            // Tunnel is connected - send packet immediately
            try {
                    // Write packet to socket pair instead of calling client.sendPacket()
                    // OpenVPN 3 reads from socket pair, so writing to socket pair = injecting packet into OpenVPN 3
                    val pipeWriteFd = pipeWriteFds[tunnelId]
                    if (pipeWriteFd != null && pipeWriteFd >= 0) {
                        // Get or create socket pair writer for this tunnel
                        val writer = pipeWriters.getOrPut(tunnelId) {
                            val pfd = pipeWritePfds[tunnelId]
                            if (pfd != null) {
                                Log.d(TAG, "📝 Creating socket pair writer for tunnel $tunnelId (FD: $pipeWriteFd)")
                                java.io.FileOutputStream(pfd.fileDescriptor)
                            } else {
                                throw IllegalStateException("PFD not found for tunnel $tunnelId")
                            }
                        }
                        // PERFORMANCE: Removed per-packet logging to prevent binder exhaustion
                        writer.write(packet)
                        writer.flush()
                    } else {
                        // Fallback to old method if no socket pair
                        Log.w(TAG, "⚠️  No socket pair FD for tunnel $tunnelId - using fallback sendPacket()")
                        client.sendPacket(packet)
                        Log.v(TAG, "Sent ${packet.size} bytes to tunnel $tunnelId (no socket pair)")
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending packet to tunnel $tunnelId", e)
            }
        } else if (client != null && !client.isConnected()) {
            // Tunnel exists but not connected yet - queue packet
            val queue = packetQueues.getOrPut(tunnelId) { java.util.concurrent.ConcurrentLinkedQueue() }
            
            // Clean old packets from queue (older than QUEUE_TIMEOUT_MS)
            val now = System.currentTimeMillis()
            val iterator = queue.iterator()
            while (iterator.hasNext()) {
                val queuedPacket = iterator.next()
                if (now - queuedPacket.timestamp > QUEUE_TIMEOUT_MS) {
                    iterator.remove()
                    Log.v(TAG, "Dropped queued packet for tunnel $tunnelId (timed out after ${QUEUE_TIMEOUT_MS}ms)")
                }
            }
            
            // Add packet to queue if not full
            if (queue.size < MAX_QUEUE_SIZE) {
                queue.add(QueuedPacket(packet, now))
                Log.i(TAG, "📦 Queued packet for tunnel $tunnelId (not connected yet, queue size: ${queue.size})")
            } else {
                Log.w(TAG, "⚠️  Packet queue full for tunnel $tunnelId (max $MAX_QUEUE_SIZE = ~${MAX_QUEUE_SIZE * 1500 / 1024 / 1024}MB), dropping packet")
            }
        } else {
            // Tunnel doesn't exist at all - drop packet
            Log.w(TAG, "Tunnel $tunnelId not found - packet dropped")
        }
    }
    
    /**
     * Flush queued packets for a tunnel when it connects.
     * Called automatically when tunnel connection completes.
     */
    private fun flushQueuedPackets(tunnelId: String) {
        val queue = packetQueues[tunnelId]
        if (queue != null && queue.isNotEmpty()) {
            Log.i(TAG, "📤 Flushing ${queue.size} queued packets for tunnel $tunnelId")
            var sentCount = 0
            var droppedCount = 0
            
            while (queue.isNotEmpty()) {
                val queuedPacket = queue.poll()
                if (queuedPacket != null) {
                    val age = System.currentTimeMillis() - queuedPacket.timestamp
                    if (age <= QUEUE_TIMEOUT_MS) {
                        // Packet still valid - send it
                        sendPacketToTunnel(tunnelId, queuedPacket.packet)
                        sentCount++
                    } else {
                        // Packet too old - drop it
                        droppedCount++
                    }
                }
            }
            
            Log.i(TAG, "✅ Flushed queued packets for tunnel $tunnelId: sent=$sentCount, dropped=$droppedCount")
            packetQueues.remove(tunnelId)
        }
    }
    
    /**
     * Create pipes (unnamed pipes) for packet filtering.
     * Returns the read FD for OpenVPN 3 (it will read packets from this).
     * Native code creates the pipes.
     */
    private external fun createPipe(tunnelId: String): Int
    
    /**
     * Get the write FD for writing packets to OpenVPN 3.
     */
    private external fun getPipeWriteFd(tunnelId: String): Int
    
    /**
     * Get the read FD for reading responses from OpenVPN 3.
     */
    private external fun getPipeReadFd(tunnelId: String): Int
    
    /**
     * Start a background coroutine to read response packets from OpenVPN 3 via pipe.
     * OpenVPN 3 writes response packets to the pipe, and we read them and forward to TUN.
     */
    private fun startPipeReader(tunnelId: String, readFd: Int) {
        // Cancel existing reader if any
        pipeReaders[tunnelId]?.cancel()
        
        // Start new reader coroutine
        val job = pipeReaderScope.launch {
            try {
                Log.d(TAG, "Starting pipe reader for tunnel $tunnelId (FD: $readFd)")
                
                // Get PFD from stored map (keeps FD open)
                // FIXED: Use pipeWritePfds since we only create one PFD per FD now
                val pfd = pipeWritePfds[tunnelId]
                    ?: throw IllegalStateException("PFD not found for tunnel $tunnelId")
                val reader = java.io.FileInputStream(pfd.fileDescriptor)
                
                val buffer = ByteArray(32767) // Max IP packet size
                
                Log.d(TAG, "Pipe reader started for tunnel $tunnelId")
                
                var responseCount = 0
                while (isActive && connections.containsKey(tunnelId)) {
                    try {
                        val length = reader.read(buffer)
                        if (length > 0) {
                            responseCount++
                            val packet = buffer.copyOf(length)
                            Log.i(TAG, "📥 [Response #$responseCount] Read ${packet.size} bytes from socket pair for tunnel $tunnelId (response from OpenVPN 3)")
                            
                            // Forward packet to TUN interface via packetReceiver callback
                            // This writes the response packet to the TUN interface so apps receive it
                            if (packetReceiver != null) {
                                packetReceiver!!.invoke(tunnelId, packet)
                                Log.d(TAG, "   ✅ Response #$responseCount forwarded to TUN via packetReceiver")
                            } else {
                                Log.w(TAG, "   ⚠️  Response #$responseCount received but packetReceiver is null!")
                            }
                        } else if (length == -1) {
                            // EOF or error
                            Log.w(TAG, "❌ Socket pair reader reached EOF for tunnel $tunnelId (read $responseCount responses)")
                            break
                        } else if (length == 0) {
                            Log.v(TAG, "   No data available (length=0), continuing...")
                        }
                    } catch (e: java.io.IOException) {
                        if (isActive && connections.containsKey(tunnelId)) {
                            Log.e(TAG, "❌ Error reading from socket pair for tunnel $tunnelId (read $responseCount responses so far)", e)
                            kotlinx.coroutines.delay(100) // Wait before retrying
                        } else {
                            break // Connection closed, exit loop
                        }
                    }
                }
                Log.d(TAG, "📥 Socket pair reader stopped for tunnel $tunnelId (read $responseCount responses total)")
                
                Log.d(TAG, "Pipe reader stopped for tunnel $tunnelId")
                reader.close()
                // Don't close PFD here - it's managed in stopPipeReader()
            } catch (e: Exception) {
                Log.e(TAG, "Pipe reader exception for tunnel $tunnelId", e)
            }
        }
        
        pipeReaders[tunnelId] = job
        Log.d(TAG, "✅ Pipe reader coroutine started for tunnel $tunnelId")
    }
    
    /**
     * Stop pipe reader for a tunnel (called when tunnel disconnects).
     */
    private fun stopPipeReader(tunnelId: String) {
        pipeReaders[tunnelId]?.cancel()
        pipeReaders.remove(tunnelId)
        pipeWriters[tunnelId]?.close()
        pipeWriters.remove(tunnelId)
        
        // CRITICAL FIX: Only close PFD once (we removed the duplicate pipeReadPfds)
        pipeWritePfds[tunnelId]?.close()
        pipeWritePfds.remove(tunnelId)
        pipeWriteFds.remove(tunnelId)
        
        Log.d(TAG, "Stopped pipe reader and writer for tunnel $tunnelId (FD properly closed once)")
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
     * Returns all active connections for inspection.
     * Used by VpnEngineService to check connection states.
     */
    fun getAllConnections(): Map<String, OpenVpnClient> {
        return connections.toMap()
    }
    
    /**
     * Notifies listener about connection state changes.
     * Called when connections start connecting or finish connecting.
     * 
     * CRITICAL: We check if connections are NOT connected (including connecting state).
     * However, isConnected() might return true even when connecting=true (due to our
     * fix in openvpn_wrapper_is_connected). We need to be more careful.
     * 
     * For now, we check if any connection is NOT connected. If a connection exists
     * but isConnected() returns false, it's connecting. If all connections return true
     * for isConnected(), they're all connected.
     */
    private fun notifyConnectionStateChanged() {
        val connectionStates = connections.map { (id, client) ->
            val connected = client.isConnected()
            "$id=${if (connected) "✅" else "⏳"}"
        }
        val hasConnecting = connections.values.any { client ->
            // Check if client is not connected (this includes connecting state)
            // Note: isConnected() might return true for connecting=true, but we check anyway
            !client.isConnected()
        }
        val totalConnections = connections.size
        Log.i(TAG, "═══════════════════════════════════════════════════════")
        Log.i(TAG, "📢 notifyConnectionStateChanged() called")
        Log.i(TAG, "   Total connections: $totalConnections")
        Log.i(TAG, "   Connection states: ${connectionStates.joinToString(", ")}")
        Log.i(TAG, "   hasConnecting: $hasConnecting (if false, TUN reading should RESUME)")
        Log.i(TAG, "   connectionStateListener: ${if (connectionStateListener != null) "✅ set" else "❌ null"}")
        Log.i(TAG, "═══════════════════════════════════════════════════════")
        
        if (connectionStateListener != null) {
            connectionStateListener!!.invoke(hasConnecting)
            Log.i(TAG, "   ✅ Callback invoked with hasConnecting=$hasConnecting")
        } else {
            Log.e(TAG, "   ❌ connectionStateListener is NULL - callback not set!")
        }
    }
    
    /**
     * Called when a tunnel connection attempt starts.
     * Notifies listener that we have connecting connections.
     */
    fun onConnectionStarted(tunnelId: String) {
        Log.d(TAG, "onConnectionStarted: tunnelId=$tunnelId")
        notifyConnectionStateChanged()
    }
    
    /**
     * Called when a tunnel connection completes (successfully or with error).
     * Notifies listener that connection state has changed.
     */
    fun onConnectionCompleted(tunnelId: String) {
        Log.d(TAG, "onConnectionCompleted: tunnelId=$tunnelId")
        notifyConnectionStateChanged()
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
        
        // Add client to connections map BEFORE connecting (so it's tracked during connection)
        // This allows notifyConnectionStateChanged() to detect connecting state
        connections[tunnelId] = client
        
        // Notify that connection is starting (event-based exclusive TUN access)
        onConnectionStarted(tunnelId)
        
        // Connect
        Log.d(TAG, "Attempting to connect tunnel $tunnelId to OpenVPN server...")
        Log.d(TAG, "Config length: ${ovpnConfig.length} bytes")
        
        var connected: Boolean
        var connectionError: VpnError? = null
        
        try {
            // Connect (this starts async connection - returns true immediately if started successfully)
            connected = client.connect(ovpnConfig, authFilePath)
            
            // CRITICAL: connect() returns true immediately if connection started successfully,
            // but the actual connection completes asynchronously. We should NOT call
            // notifyConnectionStateChanged() here - it will be called when connection
            // actually completes (we'll poll for it or client will notify us).
            // For now, we keep the client in connecting state (hasConnecting=true)
            // and will poll isConnected() to detect when it completes.
            
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
            // Client already in connections map (added before connect)
            // Connection started successfully, but may not be fully connected yet
            // (OpenVPN 3 connections are asynchronous)
            Log.i(TAG, "✅ Tunnel connection started for: $tunnelId (connection completing asynchronously)")
            
            // Start a coroutine to monitor connection status and notify when it completes
            // This ensures we only resume TUN reading when connection is actually established
            Log.d(TAG, "🔍 Starting connection completion polling for tunnel $tunnelId")
            GlobalScope.launch {
                var attempts = 0
                val maxAttempts = 120 // 2 minutes (120 * 1 second)
                Log.d(TAG, "   Polling loop started for tunnel $tunnelId (max $maxAttempts attempts)")
                
                while (attempts < maxAttempts && connections.containsKey(tunnelId)) {
                    delay(1000) // Check every second
                    attempts++
                    
                    if (attempts % 10 == 0) {
                        Log.d(TAG, "   Polling attempt #$attempts for tunnel $tunnelId (checking isConnected())")
                    }
                    
                    val client = connections[tunnelId]
                    if (client != null) {
                        val isConnected = client.isConnected()
                        if (attempts % 10 == 0) {
                            Log.d(TAG, "   Tunnel $tunnelId: isConnected()=$isConnected (attempt #$attempts)")
                        }
                        
                    if (isConnected) {
                        // Connection completed - notify listener and flush queued packets
                        Log.i(TAG, "✅✅✅ Tunnel $tunnelId connection completed (after $attempts seconds) ✅✅✅")
                        Log.i(TAG, "   Flushing queued packets and notifying state change...")
                        
                        // Flush any packets that were queued while tunnel was connecting
                        flushQueuedPackets(tunnelId)
                        
                        Log.i(TAG, "   Calling notifyConnectionStateChanged() to resume TUN reading...")
                        notifyConnectionStateChanged()
                        return@launch
                    }
                    } else {
                        Log.w(TAG, "   Client for tunnel $tunnelId is null (attempt #$attempts)")
                    }
                    
                    // Check if connection failed (client removed or error occurred)
                    if (!connections.containsKey(tunnelId)) {
                        Log.w(TAG, "⚠️  Tunnel $tunnelId removed during connection attempt (attempt #$attempts)")
                        notifyConnectionStateChanged()
                        return@launch
                    }
                }
                
                if (attempts >= maxAttempts) {
                    Log.w(TAG, "⚠️  Tunnel $tunnelId connection check timed out after $maxAttempts seconds")
                    Log.w(TAG, "   Final state: isConnected()=${connections[tunnelId]?.isConnected()}")
                    // Still notify - connection might be stuck in connecting state
                    Log.i(TAG, "   Calling notifyConnectionStateChanged() anyway...")
                    notifyConnectionStateChanged()
                }
            }
            
            // Don't notify here - wait for connection to actually complete
            // The monitoring coroutine will call notifyConnectionStateChanged() when done
            return TunnelCreationResult(success = true)
        } else {
            // Remove failed connection from map
            connections.remove(tunnelId)
            Log.e(TAG, "❌ Failed to create tunnel: $tunnelId")
            Log.e(TAG, "   Error type: ${connectionError?.type}")
            Log.e(TAG, "   Error message: ${connectionError?.message}")
            // Notify listener that connection failed (no longer connecting)
            notifyConnectionStateChanged()
            return TunnelCreationResult(success = false, error = connectionError)
        }
    }
    
    suspend fun closeTunnel(tunnelId: String) {
        val client = connections[tunnelId]
        if (client != null) {
            client.disconnect()
            connections.remove(tunnelId)
            
        // Stop pipe reader and close writers
        stopPipeReader(tunnelId)
            
            // Clear any queued packets for this tunnel
            packetQueues.remove(tunnelId)
            
            // Clear readiness state for this tunnel
            tunnelReadinessStates.remove(tunnelId)
            
            Log.d(TAG, "Closed tunnel: $tunnelId")
        }
    }
    
    suspend fun closeAll() {
        connections.values.forEach { 
            runBlocking { it.disconnect() }
        }
        
        // Stop all pipe readers and close writers
        pipeReaders.keys.forEach { tunnelId ->
            stopPipeReader(tunnelId)
        }
        
        connections.clear()
        tunnelReadinessStates.clear()
        Log.d(TAG, "Closed all tunnels")
    }
    
    fun isTunnelConnected(tunnelId: String): Boolean {
        return connections[tunnelId]?.isConnected() == true
    }
    
    /**
     * Get all tunnel IDs from active connections.
     * Used for routing DNS queries to any connected tunnel.
     */
    fun getAllTunnelIds(): List<String> {
        return connections.keys.toList()
    }
    
    /**
     * Checks if a tunnel is fully ready for routing.
     * This means:
     * 1. OpenVPN connection is established (isConnected())
     * 2. Tunnel IP address has been assigned via DHCP
     * 3. DNS servers have been configured
     * 
     * Use this instead of isTunnelConnected() when you need to ensure the tunnel
     * is ready for actual traffic routing (e.g., before making HTTP requests).
     */
    fun isTunnelReadyForRouting(tunnelId: String): Boolean {
        val connected = connections[tunnelId]?.isConnected() == true
        val readinessState = tunnelReadinessStates[tunnelId]
        val ipAssigned = readinessState?.ipAssigned == true
        val dnsConfigured = readinessState?.dnsConfigured == true
        
        val ready = connected && ipAssigned && dnsConfigured
        
        if (!ready) {
            Log.d(TAG, "Tunnel $tunnelId not fully ready: connected=$connected, ipAssigned=$ipAssigned, dnsConfigured=$dnsConfigured")
        }
        
        return ready
    }
    
    /**
     * Gets a human-readable status string for tunnel readiness.
     */
    private fun getTunnelReadinessStatus(tunnelId: String): String {
        val connected = connections[tunnelId]?.isConnected() == true
        val readinessState = tunnelReadinessStates[tunnelId]
        val ipAssigned = readinessState?.ipAssigned == true
        val dnsConfigured = readinessState?.dnsConfigured == true
        
        return "connected=${if (connected) "✅" else "❌"}, " +
               "ip=${if (ipAssigned) "✅" else "⏳"}, " +
               "dns=${if (dnsConfigured) "✅" else "⏳"}"
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
