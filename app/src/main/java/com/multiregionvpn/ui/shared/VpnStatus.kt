package com.multiregionvpn.ui.shared

/**
 * Shared VPN status enum used by both Mobile and TV UIs.
 * The term PROTECTED is preferred over CONNECTED to match UI and Maestro requirements.
 */
enum class VpnStatus(val displayText: String) {
    PROTECTED("Protected"),
    DISCONNECTED("Disconnected"),
    CONNECTING("Connecting"),
    ERROR("Error")
}
