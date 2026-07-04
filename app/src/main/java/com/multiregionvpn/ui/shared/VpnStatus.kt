package com.multiregionvpn.ui.shared

/**
 * Shared VPN status enum used by both Mobile and TV UIs.
 *
 * 🛡️ Sentinel: Consolidated to a single source of truth with displayText for Maestro compatibility.
 */
enum class VpnStatus(val displayText: String) {
    PROTECTED("Protected"),
    CONNECTING("Connecting"),
    DISCONNECTED("Disconnected"),
    ERROR("Error")
}
