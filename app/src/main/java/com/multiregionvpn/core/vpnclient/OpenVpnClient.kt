package com.multiregionvpn.core.vpnclient

/**
 * Interface for OpenVPN client implementations.
 * This allows for testable mocks and future real implementations.
 */
interface OpenVpnClient {
    /**
     * Connects to the VPN using the provided OpenVPN configuration.
     * @param ovpnConfig The complete .ovpn configuration file content
     * @param username VPN username
     * @param password VPN password
     * @return true if connection was initiated successfully, false otherwise
     */
    suspend fun connect(ovpnConfig: String, username: String? = null, password: String? = null): Boolean
    
    /**
     * Sends a packet to the VPN tunnel.
     * @param packet The raw IP packet (including IP header)
     */
    fun sendPacket(packet: ByteArray)
    
    /**
     * Disconnects from the VPN.
     */
    suspend fun disconnect()
    
    /**
     * Returns true if currently connected to the VPN.
     */
    fun isConnected(): Boolean
    
    /**
     * Sets a callback for receiving packets from the VPN tunnel.
     * The callback receives raw IP packets that should be written back to the TUN interface.
     */
    fun setPacketReceiver(callback: (ByteArray) -> Unit)
}


