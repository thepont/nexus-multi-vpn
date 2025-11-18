package com.multiregionvpn.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.multiregionvpn.ui.settings.SettingsViewModel
import com.multiregionvpn.ui.settings.composables.ProviderCredentialsSection
import com.multiregionvpn.ui.settings.composables.ProviderAccountSection
import com.multiregionvpn.ui.settings.composables.VpnConfigSection
import com.multiregionvpn.ui.settings.composables.AppRuleSection

/**
 * Settings Screen - App Configuration
 * 
 * Provider credentials, provider accounts, VPN configs, and app routing rules.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section 1: Provider Credentials (Legacy - can be removed later)
        ProviderCredentialsSection(
            credentials = uiState.nordCredentials,
            onSaveCredentials = { username, password ->
                viewModel.saveNordCredentials(username, password)
            }
        )
        
        Divider(modifier = Modifier.padding(vertical = 8.dp))

        // Section 2: VPN Provider Accounts (New)
        ProviderAccountSection(
            providerAccounts = uiState.providerAccounts,
            providers = viewModel.getAllProviders(),
            onAddAccount = { providerId, displayLabel, credentials ->
                viewModel.saveProviderAccount(providerId, displayLabel, credentials)
            },
            onDeleteAccount = { id -> viewModel.deleteProviderAccount(id) }
        )
        
        Divider(modifier = Modifier.padding(vertical = 8.dp))

        // Section 3: My VPN Servers (CRUD)
        VpnConfigSection(
            configs = uiState.vpnConfigs,
            onSaveConfig = { config -> viewModel.saveVpnConfig(config) },
            onDeleteConfig = { id -> viewModel.deleteVpnConfig(id) },
            onFetchNordVpnServer = { regionId, callback -> viewModel.fetchNordVpnServer(regionId, callback) }
        )

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        // Section 4: App Routing Rules
        AppRuleSection(
            installedApps = uiState.installedApps,
            appRules = uiState.appRules,
            vpnConfigs = uiState.vpnConfigs,
            providerAccounts = uiState.providerAccounts,
            onRuleChanged = { pkg, id -> viewModel.saveAppRule(pkg, id) },
            onJitRuleChanged = { pkg, vpnConfigId, providerAccountId, regionCode, preferredProtocol, fallbackDirect ->
                viewModel.saveAppRuleWithJit(pkg, vpnConfigId, providerAccountId, regionCode, preferredProtocol, fallbackDirect)
            }
        )
    }
}

