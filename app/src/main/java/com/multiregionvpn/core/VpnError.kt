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
        // Regex to find sensitive keywords followed by values
        // Handles cases like "password: mysecret", "password = mysecret", "password is mysecret"
        // Case-insensitive match for keywords.
        // Negative lookahead to avoid re-redacting [REDACTED]
        private val SENSITIVE_PATTERNS = listOf(
            Regex("(?i)(password|token|key|secret|credential|username)(\\s*[:= ]\\s*)(?!\\[REDACTED\\])([^\\s,;\\n]+)"),
            Regex("(?i)(nord_auth_)[^\\s,;\\n]+")
        )

        /**
         * Redacts sensitive information from error messages and details.
         */
        fun sanitize(input: String): String {
            var sanitized = input
            SENSITIVE_PATTERNS.forEach { regex ->
                sanitized = regex.replace(sanitized) { result ->
                    val label = result.groups[1]?.value ?: ""
                    val separator = result.groups[2]?.value ?: ""
                    "$label$separator[REDACTED]"
                }
            }
            return sanitized
        }

        /**
         * Factory method to create a sanitized VpnError.
         */
        fun create(
            type: ErrorType,
            message: String,
            details: String? = null,
            tunnelId: String? = null
        ): VpnError {
            return VpnError(
                type = type,
                message = sanitize(message),
                details = details?.let { sanitize(it) },
                tunnelId = tunnelId
            )
        }

        fun fromException(e: Throwable, tunnelId: String? = null): VpnError {
            val errorMsg = e.message ?: "Unknown error"
            // SECURITY: Use e.toString() instead of e.stackTraceToString() to prevent stack trace leakage
            val details = e.toString()
            
            val type = when {
                errorMsg.contains("auth", ignoreCase = true) ||
                errorMsg.contains("credential", ignoreCase = true) ||
                errorMsg.contains("password", ignoreCase = true) ||
                errorMsg.contains("username", ignoreCase = true) ||
                errorMsg.contains("invalid", ignoreCase = true) -> {
                    ErrorType.AUTHENTICATION_FAILED
                }
                errorMsg.contains("connection", ignoreCase = true) ||
                errorMsg.contains("timeout", ignoreCase = true) ||
                errorMsg.contains("unreachable", ignoreCase = true) -> {
                    ErrorType.CONNECTION_FAILED
                }
                errorMsg.contains("config", ignoreCase = true) ||
                errorMsg.contains("parse", ignoreCase = true) -> {
                    ErrorType.CONFIG_ERROR
                }
                errorMsg.contains("interface", ignoreCase = true) ||
                errorMsg.contains("permission", ignoreCase = true) ||
                errorMsg.contains("vpn", ignoreCase = true) -> {
                    ErrorType.INTERFACE_ERROR
                }
                else -> {
                    ErrorType.UNKNOWN
                }
            }

            return create(type, errorMsg, details, tunnelId)
        }
    }
}
