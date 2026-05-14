package com.multiregionvpn.ui.shared

/**
 * Single source of truth for VPN status states.
 * Used by Mobile and TV UIs, and referenced in Maestro E2E tests.
 */
enum class VpnStatus(val displayText: String) {
    PROTECTED("Protected"),
    CONNECTING("Connecting"),
    DISCONNECTED("Disconnected"),
    ERROR("Error")
}
