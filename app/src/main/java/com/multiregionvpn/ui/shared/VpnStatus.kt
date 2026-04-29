package com.multiregionvpn.ui.shared

/**
 * Shared VPN status enum used by both Mobile and TV UIs.
 * Optimized for "Network Operations Center" (NOC) style display.
 */
enum class VpnStatus(val displayText: String) {
    CONNECTED("Connected"),
    PROTECTED("Protected"),
    CONNECTING("Connecting"),
    DISCONNECTED("Disconnected"),
    ERROR("Error")
}
