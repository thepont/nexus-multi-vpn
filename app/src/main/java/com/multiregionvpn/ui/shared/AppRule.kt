package com.multiregionvpn.ui.shared

import android.graphics.drawable.Drawable

/**
 * Represents a routing rule for an application
 * 
 * @param packageName Android package name (e.g., "com.bbc.iplayer")
 * @param appName User-friendly app name (e.g., "BBC iPlayer")
 * @param icon Application icon
 * @param routedGroupId null = Bypass (direct internet), "block" = Block all traffic, otherwise = ServerGroup ID
 */
data class AppRule(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val routedGroupId: String? = null
) {
    /**
     * Returns human-readable routing description
     */
    fun getRoutingDescription(): String = when (routedGroupId) {
        null -> "Direct Internet"
        "block" -> "Blocked"
        else -> "Routed via VPN"
    }
}

