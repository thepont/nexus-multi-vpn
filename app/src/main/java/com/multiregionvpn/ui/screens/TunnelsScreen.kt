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
import com.multiregionvpn.ui.settings.composables.VpnConfigSection

/**
 * Tunnels Screen - VPN Tunnel Configuration
 * 
 * Shows list of configured tunnels with professional NOC-style cards.
 */
@Composable
fun TunnelsScreen(
    viewModel: SettingsViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        VpnConfigSection(
            configs = uiState.vpnConfigs,
            tunnelStatus = uiState.tunnelStatus,
            onSaveConfig = { config -> viewModel.saveVpnConfig(config) },
            onDeleteConfig = { configId -> viewModel.deleteVpnConfig(configId) },
            onFetchNordVpnServer = { regionId, callback -> 
                viewModel.fetchNordVpnServer(regionId, callback)
            }
        )
    }
}

