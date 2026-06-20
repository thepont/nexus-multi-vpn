package com.multiregionvpn.ui.settings.composables

import androidx.compose.runtime.Composable
import com.multiregionvpn.data.database.VpnConfig
import com.multiregionvpn.ui.settings.InstalledApp

/**
 * DEPRECATED: Use AppRuleCard directly in a flat LazyColumn.
 */
@Composable
fun AppRuleSection(
    installedApps: List<InstalledApp>,
    appRules: Map<String, String?>,
    vpnConfigs: List<VpnConfig>,
    onRuleChanged: (String, String?) -> Unit
) {
    // This component is now a no-op as SettingsScreen handles the items directly for performance
}
