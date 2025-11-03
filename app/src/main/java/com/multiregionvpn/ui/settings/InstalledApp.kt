package com.multiregionvpn.ui.settings

/**
 * A simple model for the UI to display installed apps
 */
data class InstalledApp(
    val name: String,
    val packageName: String,
    val icon: android.graphics.drawable.Drawable
)

