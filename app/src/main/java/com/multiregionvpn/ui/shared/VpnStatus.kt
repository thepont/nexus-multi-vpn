package com.multiregionvpn.ui.shared

/**
 * Shared VPN status enum used by both Mobile and TV UIs.
 * Renamed CONNECTED to PROTECTED for consistency with Maestro tests.
 */
enum class VpnStatus(val displayText: String) {
    PROTECTED("Protected"),
    DISCONNECTED("Disconnected"),
    CONNECTING("Connecting"),
    ERROR("Error")
}
