package com.multiregionvpn.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Connection event logged when a new connection is detected.
 * Tracks which app connected to which destination via which tunnel.
 */
@Entity(tableName = "connection_events")
data class ConnectionEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val timestamp: Long, // Unix timestamp in milliseconds
    
    val packageName: String, // Package name of the app (e.g., "com.bbc.iplayer")
    
    val appName: String, // Display name of the app (e.g., "BBC iPlayer")
    
    val destinationIp: String, // Destination IP address
    
    val destinationPort: Int, // Destination port
    
    val protocol: String, // "TCP" or "UDP"
    
    val tunnelId: String?, // Tunnel ID used for routing (null if direct internet)
    
    val tunnelAlias: String? // Human-readable tunnel name (e.g., "UK VPN", null if direct)
)
