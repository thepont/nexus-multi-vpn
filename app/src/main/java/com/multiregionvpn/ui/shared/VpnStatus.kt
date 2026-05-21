package com.multiregionvpn.ui.shared

/**
 * Shared VPN status enum used by both Mobile and TV UIs.
 * This is the single source of truth for VPN states.
 */
enum class VpnStatus(val displayText: String) {
    /** VPN is active and traffic is being routed */
    CONNECTED("Protected"),

    /** VPN is in the process of connecting */
    CONNECTING("Connecting"),

    /** VPN is not active */
    DISCONNECTED("Disconnected"),

    /** An error occurred during connection or operation */
    ERROR("Error")
}
