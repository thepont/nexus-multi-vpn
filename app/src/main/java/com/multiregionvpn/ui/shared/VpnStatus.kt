package com.multiregionvpn.ui.shared

/**
 * Shared VPN status enum used by both Mobile and TV UIs.
 * This is the single source of truth for VPN states across the app.
 */
enum class VpnStatus(val displayText: String) {
    PROTECTED("Protected"),
    CONNECTING("Connecting"),
    DISCONNECTED("Disconnected"),
    ERROR("Error")
}
