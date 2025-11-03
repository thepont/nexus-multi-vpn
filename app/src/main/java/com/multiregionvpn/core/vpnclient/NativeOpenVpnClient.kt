package com.multiregionvpn.core.vpnclient

import android.content.Context
import android.net.VpnService
import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Native OpenVPN client implementation using OpenVPN 3 C++ library via JNI.
 * 
 * This replaces RealOpenVpnClient and uses the native OpenVPN 3 library
 * for better performance and reliability.
 */
class NativeOpenVpnClient(
    private val context: Context,
    private val vpnService: VpnService
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
        password: String
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

    override suspend fun connect(ovpnConfig: String, authFilePath: String?): Boolean {
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
                        val lines = authFile.readLines()
                        if (lines.size >= 2) {
                            username = lines[0].trim()
                            password = lines[1].trim()
                            Log.d(TAG, "Credentials loaded from auth file (username length: ${username.length})")
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
                
                // Call native connect
                val handle = try {
                    nativeConnect(ovpnConfig, username, password)
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
                connected.set(true)
                
                Log.i(TAG, "✅ Native OpenVPN connection established!")
                Log.i(TAG, "   Session handle: $handle")
                Log.i(TAG, "   Connection status: ${nativeIsConnected(handle)}")

                // Start receiving packets
                connectionScope.launch {
                    startPacketReception()
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
                
                // Check connection status
                if (!nativeIsConnected(handle)) {
                    Log.w(TAG, "Native connection lost")
                    connected.set(false)
                    break
                }
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

