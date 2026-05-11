package com.multiregionvpn.ui.shared.model

/**
 * Shared VPN status enum used by both Mobile and TV UIs
 */
enum class VpnStatus(val displayText: String) {
    PROTECTED("Protected"),
    CONNECTING("Connecting"),
    DISCONNECTED("Disconnected"),
    ERROR("Error")
}
