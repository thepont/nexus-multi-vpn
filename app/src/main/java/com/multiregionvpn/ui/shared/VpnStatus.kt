package com.multiregionvpn.ui.shared

/**
 * Shared VPN status enum used by both Mobile and TV UIs.
 *
 * Note: PROTECTED is used instead of CONNECTED to match Maestro E2E test assertions
 * and the 'Network Operations Center' (NOC) technical aesthetic.
 */
enum class VpnStatus(val displayText: String) {
    PROTECTED("Protected"),
    CONNECTING("Connecting"),
    DISCONNECTED("Disconnected"),
    ERROR("Error")
}
