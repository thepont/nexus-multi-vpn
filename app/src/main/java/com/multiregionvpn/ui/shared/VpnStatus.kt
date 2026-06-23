package com.multiregionvpn.ui.shared

/**
 * Shared VPN status enum used by both Mobile and TV UIs
 */
enum class VpnStatus(val displayText: String) {
    PROTECTED("Protected"),
    CONNECTED("Connected"),
    CONNECTING("Connecting"),
    DISCONNECTED("Disconnected"),
    ERROR("Error")
}
