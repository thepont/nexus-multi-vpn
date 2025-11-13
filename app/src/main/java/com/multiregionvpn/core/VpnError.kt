package com.multiregionvpn.core

/**
 * Represents a VPN connection error with detailed information.
 * This helps users understand what went wrong and how to fix it.
 */
data class VpnError(
    val type: ErrorType,
    val message: String,
    val details: String? = null,
    val tunnelId: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Categories of VPN errors
     */
    enum class ErrorType {
        /** Authentication failed - credentials are invalid */
        AUTHENTICATION_FAILED,
        
        /** Connection failed - server unreachable or network issue */
        CONNECTION_FAILED,
        
        /** Configuration error - invalid OpenVPN config */
        CONFIG_ERROR,
        
        /** VPN interface failed - permission or system issue */
        INTERFACE_ERROR,
        
        /** Tunnel creation failed - general tunnel error */
        TUNNEL_ERROR,
        
        /** Unknown error */
        UNKNOWN
    }
    
    /**
     * Returns a user-friendly error message
     */
    fun getUserMessage(): String {
        return when (type) {
            ErrorType.AUTHENTICATION_FAILED -> {
                "Authentication failed. Please check your NordVPN credentials:\n\n" +
                "• Go to https://my.nordaccount.com/dashboard/nordvpn/manual-setup/\n" +
                "• Generate new Service Credentials\n" +
                "• Update them in the app settings\n\n" +
                "Error: ${details ?: message}"
            }
            ErrorType.CONNECTION_FAILED -> {
                "Could not connect to VPN server:\n\n" +
                "• Check your internet connection\n" +
                "• The VPN server may be temporarily unavailable\n" +
                "• Try a different server region\n\n" +
                "Error: ${details ?: message}"
            }
            ErrorType.CONFIG_ERROR -> {
                "Invalid VPN configuration:\n\n" +
                "• The server configuration may be outdated\n" +
                "• Try removing and re-adding the VPN server\n" +
                "• Check if the server hostname is correct\n\n" +
                "Error: ${details ?: message}"
            }
            ErrorType.INTERFACE_ERROR -> {
                "VPN interface error:\n\n" +
                "• VPN permission may not be granted\n" +
                "• Another VPN may be active\n" +
                "• Try restarting the app\n\n" +
                "Error: ${details ?: message}"
            }
            ErrorType.TUNNEL_ERROR -> {
                "Tunnel creation failed:\n\n" +
                "• Check your VPN credentials\n" +
                "• Verify the server is reachable\n" +
                "• Try a different server\n\n" +
                "Error: ${details ?: message}"
            }
            ErrorType.UNKNOWN -> {
                "An unexpected error occurred:\n\n${details ?: message}"
            }
        }
    }
    
    companion object {
        fun fromException(e: Throwable, tunnelId: String? = null): VpnError {
            val errorMsg = e.message ?: "Unknown error"
            val details = e.stackTraceToString()
            
            return when {
                errorMsg.contains("auth", ignoreCase = true) ||
                errorMsg.contains("credential", ignoreCase = true) ||
                errorMsg.contains("password", ignoreCase = true) ||
                errorMsg.contains("username", ignoreCase = true) ||
                errorMsg.contains("invalid", ignoreCase = true) -> {
                    VpnError(
                        type = ErrorType.AUTHENTICATION_FAILED,
                        message = errorMsg,
                        details = details,
                        tunnelId = tunnelId
                    )
                }
                errorMsg.contains("connection", ignoreCase = true) ||
                errorMsg.contains("timeout", ignoreCase = true) ||
                errorMsg.contains("unreachable", ignoreCase = true) -> {
                    VpnError(
                        type = ErrorType.CONNECTION_FAILED,
                        message = errorMsg,
                        details = details,
                        tunnelId = tunnelId
                    )
                }
                errorMsg.contains("config", ignoreCase = true) ||
                errorMsg.contains("parse", ignoreCase = true) -> {
                    VpnError(
                        type = ErrorType.CONFIG_ERROR,
                        message = errorMsg,
                        details = details,
                        tunnelId = tunnelId
                    )
                }
                errorMsg.contains("interface", ignoreCase = true) ||
                errorMsg.contains("permission", ignoreCase = true) ||
                errorMsg.contains("vpn", ignoreCase = true) -> {
                    VpnError(
                        type = ErrorType.INTERFACE_ERROR,
                        message = errorMsg,
                        details = details,
                        tunnelId = tunnelId
                    )
                }
                else -> {
                    VpnError(
                        type = ErrorType.UNKNOWN,
                        message = errorMsg,
                        details = details,
                        tunnelId = tunnelId
                    )
                }
            }
        }
    }
}


