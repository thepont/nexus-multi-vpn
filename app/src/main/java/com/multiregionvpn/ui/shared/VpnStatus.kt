package com.multiregionvpn.ui.shared

/**
 * Single source of truth for VPN status across the application.
 * Used by Mobile and TV UIs, and tracked by VpnServiceStateTracker.
 *
 * Using PROTECTED terminology for compatibility with Maestro E2E tests.
 */
enum class VpnStatus(val displayText: String) {
    PROTECTED("Protected"),
    CONNECTING("Connecting"),
    DISCONNECTED("Disconnected"),
    ERROR("Error");

    val isConnected: Boolean get() = this == PROTECTED
}
