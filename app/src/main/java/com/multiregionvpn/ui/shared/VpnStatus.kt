package com.multiregionvpn.ui.shared

/**
 * Shared VPN status enum used by both Mobile and TV UIs.
 *
 * Using PROTECTED instead of CONNECTED to match UI requirements and Maestro tests.
 */
enum class VpnStatus(val displayText: String) {
    PROTECTED("Protected"),
    DISCONNECTED("Disconnected"),
    CONNECTING("Connecting"),
    ERROR("Error")
}
