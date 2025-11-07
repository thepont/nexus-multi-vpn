package com.multiregionvpn.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.multiregionvpn.ui.settings.SettingsViewModel
import com.multiregionvpn.ui.settings.composables.AppRuleSection

/**
 * App Rules Screen - Per-App Routing Configuration
 * 
 * Shows routing rules for installed apps with smart suggestions.
 */
@Composable
fun AppRulesScreen(
    viewModel: SettingsViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        AppRuleSection(
            installedApps = uiState.installedApps,
            appRules = uiState.appRules,
            vpnConfigs = uiState.vpnConfigs,
            onRuleChanged = { packageName, vpnConfigId ->
                viewModel.saveAppRule(packageName, vpnConfigId)
            }
        )
    }
}

