package com.multiregionvpn.core

import android.util.Log
import java.io.File

/**
 * Helper class to parse /proc/net files and extract UID from connection tuples.
 * Made separate for testability.
 */
internal class ProcNetParser {
    
    companion object {
        private const val TAG = "ProcNetParser"
        
        /**
         * Converts IP address to little-endian hex format used in /proc/net
         * Example: 192.168.1.100 -> "6401A8C0"
         */
        fun ipToHex(ip: String): String {
            val bytes = ip.split(".").map { it.toInt().and(0xFF) }
            return String.format("%02X%02X%02X%02X", bytes[3], bytes[2], bytes[1], bytes[0])
        }
        
        /**
         * Converts port to little-endian hex format used in /proc/net
         * Example: 443 -> "BB01" (443 = 0x01BB, little-endian = BB 01)
         */
        fun portToLittleEndianHex(port: Int): String {
            val low = port.and(0xFF)
            val high = (port shr 8).and(0xFF)
            return String.format("%02X%02X", low, high)
        }
        
        /**
         * Reads UID from /proc/net/tcp or /proc/net/udp based on protocol
         * Returns UID if connection found, null otherwise
         */
        fun readUidFromProcNet(
            srcIp: String,
            srcPort: Int,
            destIp: String,
            destPort: Int,
            protocol: Int,
            procNetDir: String = "/proc/net"
        ): Int? {
            val procFile = when (protocol) {
                6 -> "$procNetDir/tcp"   // TCP
                17 -> "$procNetDir/udp"  // UDP
                else -> return null
            }
            
            return try {
                val file = File(procFile)
                if (!file.exists() || !file.canRead()) {
                    Log.w(TAG, "Cannot read $procFile")
                    return null
                }
                
                val srcIpHex = ipToHex(srcIp)
                val destIpHex = ipToHex(destIp)
                val srcPortHex = portToLittleEndianHex(srcPort)
                val destPortHex = portToLittleEndianHex(destPort)
                
                file.bufferedReader().useLines { lines ->
                    lines.drop(1).forEach { line -> // Skip header line
                        val parts = line.trim().split("\\s+".toRegex())
                        if (parts.size >= 11) {
                            try {
                                val localAddr = parts[1] // Format: IP:PORT in hex
                                val remAddr = parts[2]
                                
                                // Check if this connection matches
                                if (localAddr.startsWith(srcIpHex) && remAddr.startsWith(destIpHex)) {
                                    val localAddrParts = localAddr.split(":")
                                    val remAddrParts = remAddr.split(":")
                                    if (localAddrParts.size == 2 && remAddrParts.size == 2) {
                                        val localPortHex = localAddrParts[1]
                                        val remPortHex = remAddrParts[1]
                                        
                                        // Check if ports match (little-endian format)
                                        val portMatches = localPortHex.equals(srcPortHex, ignoreCase = true) ||
                                                         remPortHex.equals(destPortHex, ignoreCase = true)
                                        
                                        if (portMatches) {
                                            // UID is typically around index 7 (may vary by kernel version)
                                            val uidStr = parts.getOrNull(7) ?: parts.getOrNull(6) ?: parts.getOrNull(8)
                                            return uidStr?.toIntOrNull()
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                // Continue to next line if parsing fails
                            }
                        }
                    }
                }
                null
            } catch (e: Exception) {
                Log.e(TAG, "Error reading from $procFile", e)
                null
            }
        }
    }
}

