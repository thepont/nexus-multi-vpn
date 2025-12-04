package com.multiregionvpn.test

import android.util.Log
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * Utility class for managing connections to the host machine where Docker containers run.
 * 
 * Docker containers run on the host machine (development machine), not on Android.
 * This class helps Android tests connect to services running on the host.
 * 
 * For Android emulator: host IP is typically 10.0.2.2
 * For physical device: host IP needs to be detected or configured
 */
object HostMachineManager {
    private const val TAG = "HostMachineManager"
    
    // Default host IP for Android emulator
    private const val EMULATOR_HOST_IP = "10.0.2.2"
    
    /**
     * Gets the IP address of the host machine from the Android device's perspective.
     * 
     * For emulator: Returns 10.0.2.2 (default gateway)
     * For physical device: Attempts to detect host IP, or uses configured value
     * 
     * @param configuredHostIp Optional configured host IP (from test arguments or environment)
     * @return The host machine IP address
     */
    fun getHostIp(configuredHostIp: String? = null): String {
        // If explicitly configured, use that
        if (!configuredHostIp.isNullOrBlank()) {
            Log.d(TAG, "Using configured host IP: $configuredHostIp")
            return configuredHostIp
        }
        
        // For emulator, use special alias
        // TODO: Detect if running on emulator vs physical device
        // For now, default to emulator IP
        val hostIp = EMULATOR_HOST_IP
        Log.d(TAG, "Using host IP: $hostIp (emulator default)")
        return hostIp
    }
    
    /**
     * Gets the service URL on the host machine.
     * 
     * @param servicePort The port the service is listening on (host port)
     * @param configuredHostIp Optional configured host IP
     * @return Full URL (e.g., "http://10.0.2.2:8080")
     */
    fun getHostServiceUrl(servicePort: Int, configuredHostIp: String? = null): String {
        val hostIp = getHostIp(configuredHostIp)
        return "http://$hostIp:$servicePort"
    }
    
    /**
     * Gets the VPN server hostname for connecting to a VPN server on the host.
     * 
     * @param servicePort The port the VPN server is listening on (host port)
     * @param configuredHostIp Optional configured host IP
     * @return Hostname string (e.g., "10.0.2.2:11940")
     */
    fun getVpnServerHostname(servicePort: Int, configuredHostIp: String? = null): String {
        val hostIp = getHostIp(configuredHostIp)
        return "$hostIp:$servicePort"
    }
    
    /**
     * Attempts to detect the host machine IP by looking at network interfaces.
     * This is a best-effort approach and may not work reliably.
     * 
     * @return Detected host IP, or null if not found
     */
    private fun detectHostIp(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is InetAddress) {
                        val hostAddress = address.hostAddress
                        if (hostAddress != null && !hostAddress.contains(":")) {
                            // IPv4 address found
                            Log.d(TAG, "Detected potential host IP: $hostAddress")
                            return hostAddress
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error detecting host IP", e)
        }
        return null
    }
}


