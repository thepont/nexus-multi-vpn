package com.multiregionvpn.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.multiregionvpn.ui.settings.composables.AppRuleSection
import com.multiregionvpn.ui.settings.composables.ProviderCredentialsSection
import com.multiregionvpn.ui.settings.composables.VpnConfigSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    val context = LocalContext.current
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.startVpn(context)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Region Router Settings") },
                actions = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Start VPN")
                        Switch(
                            checked = uiState.isVpnRunning,
                            onCheckedChange = {
                                if (it) {
                                    val intent = VpnService.prepare(context)
                                    if (intent != null) {
                                        vpnPermissionLauncher.launch(intent)
                                    } else {
                                        viewModel.startVpn(context)
                                    }
                                } else {
                                    viewModel.stopVpn(context)
                                }
                            },
                            modifier = Modifier.testTag("start_service_toggle")
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Section 1: Provider Credentials
            ProviderCredentialsSection(
                credentials = uiState.nordCredentials,
                onSaveCredentials = { username, password -> viewModel.saveNordCredentials(username, password) }
            )
            
            Divider(modifier = Modifier.padding(vertical = 16.dp))

            // Section 2: My VPN Servers (CRUD)
            VpnConfigSection(
                configs = uiState.vpnConfigs,
                onSaveConfig = { config -> viewModel.saveVpnConfig(config) },
                onDeleteConfig = { id -> viewModel.deleteVpnConfig(id) },
                onFetchNordVpnServer = { regionId, callback -> viewModel.fetchNordVpnServer(regionId, callback) }
            )

            Divider(modifier = Modifier.padding(vertical = 16.dp))

            // Section 3: App Routing Rules
            AppRuleSection(
                installedApps = uiState.installedApps,
                appRules = uiState.appRules,
                vpnConfigs = uiState.vpnConfigs,
                onRuleChanged = { pkg, id -> viewModel.saveAppRule(pkg, id) }
            )
        }
    }
}
