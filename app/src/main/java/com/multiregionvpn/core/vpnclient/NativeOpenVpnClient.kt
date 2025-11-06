package com.multiregionvpn.core.vpnclient

import android.content.Context
import android.net.VpnService
import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Callback interface for receiving tunnel IP addresses from OpenVPN DHCP.
 * This is called from native code when tun_builder_add_address() is invoked.
 */
interface TunnelIpCallback {
    /**
     * Called when OpenVPN assigns an IP address to a tunnel via DHCP.
     * @param tunnelId The tunnel identifier (e.g., "nordvpn_UK")
     * @param ip The IP address assigned (e.g., "10.100.0.2")
     * @param prefixLength The subnet prefix length (e.g., 16 for /16)
     */
    fun onTunnelIpReceived(tunnelId: String, ip: String, prefixLength: Int)
}

/**
 * Callback interface for receiving DNS servers from OpenVPN DHCP.
 * This is called from native code when tun_builder_set_dns_options() is invoked.
 */
interface TunnelDnsCallback {
    /**
     * Called when OpenVPN pushes DNS servers via DHCP options.
     * @param tunnelId The tunnel identifier (e.g., "nordvpn_UK")
     * @param dnsServers List of DNS server IP addresses (e.g., ["103.86.96.100", "103.86.99.100"])
     */
    fun onTunnelDnsReceived(tunnelId: String, dnsServers: List<String>)
}

/**
 * Native OpenVPN client implementation using OpenVPN 3 C++ library via JNI.
 * 
 * This replaces RealOpenVpnClient and uses the native OpenVPN 3 library
 * for better performance and reliability.
 */
class NativeOpenVpnClient(
    private val context: Context,
    private val vpnService: VpnService,
    private val tunFd: Int = -1,  // Optional: TUN file descriptor if already available
    private val tunnelId: String? = null,  // Tunnel ID for identifying this connection
    private val ipCallback: TunnelIpCallback? = null,  // Callback for IP address notifications
    private val dnsCallback: TunnelDnsCallback? = null  // Callback for DNS server notifications
) : OpenVpnClient {

    private val connected = AtomicBoolean(false)
    private val sessionHandle = AtomicLong(0)
    private var packetReceiver: ((ByteArray) -> Unit)? = null
    private val connectionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lastError: String? = null
    
    /**
     * OpenVPN error codes (matching C++ definitions)
     */
    enum class OpenVpnError(val code: Int) {
        SUCCESS(0),
        INVALID_PARAMS(-1),
        CONFIG_FAILED(-2),
        AUTH_FAILED(-3),
        CONNECTION_FAILED(-4),
        UNKNOWN(-5)
    }
    
    companion object {
        private const val TAG = "NativeOpenVpnClient"
        
        // Load native library
        init {
            try {
                System.loadLibrary("openvpn-jni")
                Log.d(TAG, "Native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library", e)
                throw RuntimeException("Failed to load OpenVPN native library", e)
            }
        }
    }

    // Native methods (implemented in C++)
    @JvmName("nativeConnect")
    private external fun nativeConnect(
        config: String,
        username: String,
        password: String,
        vpnBuilder: android.net.VpnService.Builder?,  // Pass VpnService.Builder for TunBuilderBase
        tunFd: Int,  // File descriptor of already-established TUN interface
        vpnService: android.net.VpnService  // Pass VpnService instance for protect() calls
    ): Long // Returns session handle (0 = error)

    @JvmName("nativeDisconnect")
    private external fun nativeDisconnect(sessionHandle: Long)

    @JvmName("nativeSendPacket")
    private external fun nativeSendPacket(sessionHandle: Long, packet: ByteArray): Int

    @JvmName("nativeReceivePacket")
    private external fun nativeReceivePacket(sessionHandle: Long): ByteArray?

    @JvmName("nativeIsConnected")
    private external fun nativeIsConnected(sessionHandle: Long): Boolean

    @JvmName("nativeGetLastError")
    private external fun nativeGetLastError(sessionHandle: Long): String

    @JvmName("nativeSetTunnelIdAndCallback")
    private external fun nativeSetTunnelIdAndCallback(sessionHandle: Long, tunnelId: String, ipCallback: TunnelIpCallback, dnsCallback: TunnelDnsCallback?)

    override suspend fun connect(ovpnConfig: String, authFilePath: String?): Boolean {
        // NOTE: We don't need to call protect() here anymore
        // OpenVPN 3 will call tun_builder_protect() for each socket it creates
        // This is handled in the native C++ wrapper (AndroidOpenVPNClient::tun_builder_protect())
        
        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "═══════════════════════════════════════════════════════")
                Log.i(TAG, "🔌 Connecting using OpenVPN 3 ClientAPI service...")
                Log.i(TAG, "═══════════════════════════════════════════════════════")

                // Read credentials from auth file if provided
                val username: String
                val password: String
                
                if (authFilePath != null) {
                    val authFile = java.io.File(authFilePath)
                    if (authFile.exists()) {
                        // Read file as UTF-8 (OpenVPN expects UTF-8)
                        // readLines() uses UTF-8 by default, which is correct
                        val lines = authFile.readLines(Charsets.UTF_8)
                        if (lines.size >= 2) {
                            username = lines[0].trim()
                            password = lines[1].trim()
                            
                            // Verify credentials are not empty
                            if (username.isEmpty() || password.isEmpty()) {
                                Log.e(TAG, "❌ Credentials are empty after reading from auth file")
                                Log.e(TAG, "   Username length: ${username.length}, Password length: ${password.length}")
                                return@withContext false
                            }
                            
                            // Log encoding info (without exposing actual credentials)
                            val usernameBytes = username.toByteArray(Charsets.UTF_8)
                            val passwordBytes = password.toByteArray(Charsets.UTF_8)
                            Log.d(TAG, "Credentials loaded from auth file (UTF-8):")
                            Log.d(TAG, "   Username: ${username.length} chars, ${usernameBytes.size} UTF-8 bytes")
                            Log.d(TAG, "   Password: ${password.length} chars, ${passwordBytes.size} UTF-8 bytes")
                            
                            // Verify UTF-8 encoding is valid
                            try {
                                // Attempt to decode as UTF-8 to verify encoding
                                String(usernameBytes, Charsets.UTF_8)
                                String(passwordBytes, Charsets.UTF_8)
                                Log.d(TAG, "✅ Credentials are valid UTF-8 strings")
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ Invalid UTF-8 encoding in credentials", e)
                                return@withContext false
                            }
                        } else {
                            Log.e(TAG, "❌ Auth file does not contain username/password (lines: ${lines.size})")
                            return@withContext false
                        }
                    } else {
                        Log.e(TAG, "❌ Auth file does not exist: $authFilePath")
                        return@withContext false
                    }
                } else {
                    Log.e(TAG, "❌ No auth file provided")
                    return@withContext false
                }

                Log.d(TAG, "Calling native connect() - config length: ${ovpnConfig.length} bytes")
                
                // Get the TUN file descriptor - try multiple methods
                var finalTunFd = tunFd  // Use provided FD if available
                
                if (finalTunFd < 0) {
                    // Try to get it from VpnService via reflection
                    try {
                        val field = VpnService::class.java.getDeclaredField("mInterface")
                        field.isAccessible = true
                        val parcelFileDescriptor = field.get(vpnService) as? android.os.ParcelFileDescriptor
                        if (parcelFileDescriptor != null) {
                            // Try to get FD without detaching (we need it for both reading and writing)
                            val fdField = parcelFileDescriptor.javaClass.getDeclaredField("mFd")
                            fdField.isAccessible = true
                            finalTunFd = fdField.getInt(parcelFileDescriptor)
                            Log.d(TAG, "Got TUN FD via reflection from VpnService.mInterface: $finalTunFd")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not get TUN file descriptor via reflection: ${e.message}")
                    }
                }
                
                if (finalTunFd < 0) {
                    // Last resort: try detachFd (but this might break other uses)
                    try {
                        val field = VpnService::class.java.getDeclaredField("mInterface")
                        field.isAccessible = true
                        val parcelFileDescriptor = field.get(vpnService) as? android.os.ParcelFileDescriptor
                        finalTunFd = parcelFileDescriptor?.detachFd() ?: -1
                        if (finalTunFd >= 0) {
                            Log.d(TAG, "Got TUN FD via detachFd: $finalTunFd")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not detach TUN file descriptor: ${e.message}")
                    }
                }
                
                Log.d(TAG, "TUN file descriptor: $finalTunFd (${if (finalTunFd >= 0) "valid" else "invalid - OpenVPN 3 will need it via tun_builder_establish()"})")
                
                // Pass null for builder since we can't get the original builder after establishment
                // OpenVPN 3 will use TunBuilderBase methods we override
                val builder: android.net.VpnService.Builder? = null
                
                // Call native connect with VpnService.Builder, TUN FD, and VpnService instance
                val handle = try {
                    nativeConnect(ovpnConfig, username, password, builder, finalTunFd, vpnService)
                } catch (e: UnsatisfiedLinkError) {
                    Log.e(TAG, "❌ UnsatisfiedLinkError - native library not loaded properly", e)
                    lastError = "Native library not loaded: ${e.message}"
                    return@withContext false
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Exception calling native connect()", e)
                    e.printStackTrace()
                    lastError = "Exception during connection: ${e.message}"
                    return@withContext false
                }
                
                if (handle == 0L) {
                    // Connection failed - get error message from native layer if available
                    // Note: Session is destroyed on failure, so we rely on stored error
                    val errorMsg = lastError ?: "Connection failed (unknown reason)"
                    
                    // Try to get error from native layer if session handle is still valid
                    // (This won't work if session was already destroyed, but worth trying)
                    try {
                        val nativeError = nativeGetLastError(handle)
                        if (nativeError.isNotEmpty() && nativeError != "No error") {
                            lastError = nativeError
                        }
                    } catch (e: Exception) {
                        // Session may have been destroyed, ignore
                    }
                    
                    // Check if it's an authentication error
                    val lowerError = errorMsg.lowercase()
                    val isAuthError = lowerError.contains("auth") || 
                                     lowerError.contains("credential") ||
                                     lowerError.contains("password") ||
                                     lowerError.contains("username") ||
                                     lowerError.contains("invalid")
                    
                    if (isAuthError) {
                        Log.e(TAG, "❌ Authentication failed: $errorMsg")
                        lastError = "Authentication failed: $errorMsg"
                        throw AuthenticationException("OpenVPN authentication failed: $errorMsg")
                    }
                    
                    Log.e(TAG, "❌ Native connect returned invalid handle (0) - connection failed")
                    Log.e(TAG, "Error: $errorMsg")
                    lastError = errorMsg
                    return@withContext false
                }

                sessionHandle.set(handle)
                
                // Set tunnel ID and callbacks if provided
                // This must be done AFTER nativeConnect() succeeds (session is created)
                // but BEFORE connection completes (so we can receive IP address and DNS)
                if (tunnelId != null && ipCallback != null) {
                    try {
                        nativeSetTunnelIdAndCallback(handle, tunnelId, ipCallback, dnsCallback)
                        Log.d(TAG, "✅ Tunnel ID and callbacks set: tunnelId=$tunnelId")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to set tunnel ID and callbacks: ${e.message}")
                        // Continue anyway - connection can still work without callbacks
                    }
                }
                
                // CRITICAL: Do NOT set connected=true here!
                // nativeConnect() returns successfully when connection is STARTED, not when it completes.
                // The connection completes asynchronously in the background thread.
                // We'll set connected=true when nativeIsConnected() actually returns true.
                // For now, keep connected=false and poll for actual connection completion.
                connected.set(false)
                
                Log.i(TAG, "✅ Native OpenVPN connection STARTED (completing asynchronously)")
                Log.i(TAG, "   Session handle: $handle")
                Log.i(TAG, "   Connection will complete in background thread")
                
                // Start a coroutine to monitor connection status and set connected=true when ready
                connectionScope.launch {
                    var attempts = 0
                    val maxAttempts = 120 // 2 minutes
                    while (attempts < maxAttempts && sessionHandle.get() != 0L) {
                        delay(1000) // Check every second
                        attempts++
                        
                        val isConnected = try {
                            nativeIsConnected(handle)
                        } catch (e: Exception) {
                            Log.w(TAG, "Error checking connection status: ${e.message}")
                            false
                        }
                        
                        if (attempts % 10 == 0) {
                            Log.d(TAG, "Connection status check (attempt $attempts/$maxAttempts): isConnected=$isConnected")
                        }
                        
                        if (isConnected) {
                            // Connection actually established - set connected flag
                            connected.set(true)
                            Log.i(TAG, "✅ OpenVPN connection FULLY ESTABLISHED (after $attempts seconds)")
                            Log.i(TAG, "   Session handle: $handle")
                            Log.i(TAG, "   Native isConnected() returned: true")
                            
                            // Now start receiving packets
                            startPacketReception()
                            return@launch
                        }
                    }
                    
                    if (attempts >= maxAttempts) {
                        Log.w(TAG, "❌ Connection monitoring timed out after $maxAttempts seconds")
                        Log.w(TAG, "   Session handle: $handle")
                        Log.w(TAG, "   Final isConnected() check: ${try { nativeIsConnected(handle) } catch (e: Exception) { "error: ${e.message}" }}")
                        // Connection might have failed or is stuck
                        connected.set(false)
                    }
                }

                Log.i(TAG, "═══════════════════════════════════════════════════════")
                true
            } catch (e: AuthenticationException) {
                // Re-throw authentication exceptions so callers can handle them
                Log.e(TAG, "❌ Authentication failed", e)
                connected.set(false)
                sessionHandle.set(0)
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to connect to OpenVPN", e)
                e.printStackTrace()
                lastError = e.message ?: "Unknown error"
                connected.set(false)
                sessionHandle.set(0)
                false
            }
        }
    }
    
    /**
     * Gets the last error message from the connection attempt.
     */
    fun getLastError(): String? = lastError

    override fun sendPacket(packet: ByteArray) {
        if (!connected.get()) {
            Log.w(TAG, "Cannot send packet: not connected")
            return
        }

        val handle = sessionHandle.get()
        if (handle == 0L) {
            Log.w(TAG, "Cannot send packet: invalid session handle")
            return
        }

        try {
            val result = nativeSendPacket(handle, packet)
            if (result != 0) {
                Log.e(TAG, "Failed to send packet, error code: $result")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending packet", e)
        }
    }

    override suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            connected.set(false)
            
            val handle = sessionHandle.get()
            if (handle != 0L) {
                try {
                    nativeDisconnect(handle)
                    Log.d(TAG, "Disconnected from OpenVPN")
                } catch (e: Exception) {
                    Log.e(TAG, "Error during disconnect", e)
                }
                sessionHandle.set(0)
            }
        }
    }

    override fun isConnected(): Boolean = connected.get() && sessionHandle.get() != 0L

    override fun setPacketReceiver(callback: (ByteArray) -> Unit) {
        packetReceiver = callback
    }

    /**
     * Receives packets from the native OpenVPN connection.
     */
    private suspend fun startPacketReception() {
        try {
            Log.d(TAG, "Packet reception started")
            
            // Wait for connection to be fully established before starting packet reception
            // OpenVPN 3 connection might take a moment to become fully ready
            var connectionWaitAttempts = 0
            val maxConnectionWaitAttempts = 50 // 5 seconds total (50 * 100ms)
            while (!nativeIsConnected(sessionHandle.get()) && connectionWaitAttempts < maxConnectionWaitAttempts) {
                delay(100)
                connectionWaitAttempts++
                if (connectionWaitAttempts % 10 == 0) {
                    Log.d(TAG, "Waiting for connection to be ready... (attempt $connectionWaitAttempts/$maxConnectionWaitAttempts)")
                }
            }
            
            if (!nativeIsConnected(sessionHandle.get())) {
                Log.w(TAG, "Connection not ready after waiting, but continuing anyway...")
            } else {
                Log.d(TAG, "✅ Connection is ready, starting packet reception loop")
            }
            
            while (connected.get() && connectionScope.isActive) {
                val handle = sessionHandle.get()
                if (handle == 0L) {
                    delay(100)
                    continue
                }

                // Try to receive a packet
                val packet = nativeReceivePacket(handle)
                
                if (packet != null) {
                    // Forward to callback
                    packetReceiver?.invoke(packet)
                } else {
                    // No packet available, wait a bit
                    delay(10)
                }
                
                // Check connection status less frequently to avoid false positives
                // Only check every 100 iterations (about once per second)
                if (connectionWaitAttempts % 100 == 0) {
                    if (!nativeIsConnected(handle)) {
                        Log.w(TAG, "Native connection lost")
                        connected.set(false)
                        break
                    }
                }
                connectionWaitAttempts++
            }
        } catch (e: Exception) {
            if (connected.get()) {
                Log.e(TAG, "Error in packet reception", e)
            }
        } finally {
            Log.d(TAG, "Packet reception stopped")
        }
    }
}

