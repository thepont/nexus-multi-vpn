package com.multiregionvpn.ui.shared

/**
 * Shared VPN status enum used by both Mobile and TV UIs.
 * Includes displayText for UI and Maestro compatibility.
 */
enum class VpnStatus(val displayText: String) {
    PROTECTED("Protected"),
    DISCONNECTED("Disconnected"),
    CONNECTING("Connecting"),
    ERROR("Error")
}
