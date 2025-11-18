package com.multiregionvpn.core

/**
 * State of a VPN tunnel connection in the JIT lifecycle.
 */
enum class ConnectionState {
    DISCONNECTED,      // Tunnel is not connected
    SELECTING_SERVER,  // Selecting best server (latency testing)
    CONNECTING,        // Establishing connection
    CONNECTED,         // Fully connected and ready
    IDLE,              // Connected but no traffic (may disconnect soon)
    DISCONNECTING      // Gracefully disconnecting
}


