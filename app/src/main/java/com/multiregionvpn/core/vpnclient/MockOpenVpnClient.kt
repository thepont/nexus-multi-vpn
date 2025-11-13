package com.multiregionvpn.core.vpnclient

import android.util.Log

/**
 * Mock implementation of OpenVpnClient for testing.
 * Simulates VPN connection behavior without actual network operations.
 */
class MockOpenVpnClient : OpenVpnClient {
    private var connected = false
    private var packetReceiver: ((ByteArray) -> Unit)? = null
    
    // For testing: track connection attempts and packets
    var connectionAttempts = 0
    var sentPackets = mutableListOf<ByteArray>()
    var receivedPackets = mutableListOf<ByteArray>()
    
    override suspend fun connect(ovpnConfig: String, authFilePath: String?): Boolean {
        connectionAttempts++
        
        // Validate config has required elements
        if (!ovpnConfig.contains("remote") || !ovpnConfig.contains("proto")) {
            try {
                Log.w(TAG, "Invalid OpenVPN config: missing required fields")
            } catch (e: Exception) {
                // Log may not be available in unit tests
            }
            connected = false
            return false
        }
        
        connected = true
        try {
            Log.d(TAG, "Mock VPN connection established")
        } catch (e: Exception) {
            // Log may not be available in unit tests
        }
        
        // Simulate receiving initial packets (like control channel setup)
        // In real implementation, this would come from the OpenVPN client
        
        return true
    }
    
    override fun sendPacket(packet: ByteArray) {
        if (!connected) {
            try {
                Log.w(TAG, "Cannot send packet: not connected")
            } catch (e: Exception) {
                // Log may not be available in unit tests
            }
            return
        }
        
        sentPackets.add(packet)
        try {
            Log.v(TAG, "Mock: Sent packet of size ${packet.size}")
        } catch (e: Exception) {
            // Log may not be available in unit tests
        }
        
        // In a real implementation, this would forward to the OpenVPN library
        // For testing, we just track it
    }
    
    override suspend fun disconnect() {
        if (!connected) return
        
        connected = false
        try {
            Log.d(TAG, "Mock VPN disconnected")
        } catch (e: Exception) {
            // Log may not be available in unit tests
        }
        
        // Clear state
        sentPackets.clear()
        receivedPackets.clear()
    }
    
    override fun isConnected(): Boolean = connected
    
    override fun setPacketReceiver(callback: (ByteArray) -> Unit) {
        packetReceiver = callback
    }
    
    /**
     * Test helper: Simulate receiving a packet from the VPN.
     * In real implementation, this would come from the OpenVPN library.
     */
    fun simulateReceivePacket(packet: ByteArray) {
        receivedPackets.add(packet)
        packetReceiver?.invoke(packet)
    }
    
    /**
     * Test helper: Reset all state
     */
    fun reset() {
        connected = false
        connectionAttempts = 0
        sentPackets.clear()
        receivedPackets.clear()
        packetReceiver = null
    }
    
    companion object {
        private const val TAG = "MockOpenVpnClient"
    }
}

