package com.multiregionvpn.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Row
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
import com.multiregionvpn.ui.settings.composables.DnsTunnelPreference
import com.multiregionvpn.ui.settings.composables.ProviderCredentialsSection
import com.multiregionvpn.ui.settings.composables.VpnConfigSection
import com.multiregionvpn.core.VpnError
import com.multiregionvpn.ui.components.VpnHeaderBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showErrorDialog by remember { mutableStateOf<VpnError?>(null) }
    
    val context = LocalContext.current
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.startVpn(context)
        }
    }
    
    // Show error when it occurs
    LaunchedEffect(uiState.currentError) {
        uiState.currentError?.let { error ->
            // Show snackbar with error
            val message = when (error.type) {
                VpnError.ErrorType.AUTHENTICATION_FAILED -> "Authentication failed. Tap for details."
                VpnError.ErrorType.CONNECTION_FAILED -> "Connection failed. Tap for details."
                VpnError.ErrorType.CONFIG_ERROR -> "Configuration error. Tap for details."
                VpnError.ErrorType.INTERFACE_ERROR -> "VPN interface error. Tap for details."
                VpnError.ErrorType.TUNNEL_ERROR -> "Tunnel error. Tap for details."
                VpnError.ErrorType.UNKNOWN -> "VPN error occurred. Tap for details."
            }
            
            val result = snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Long,
                withDismissAction = true,
                actionLabel = "Details"
            )
            
            // Show detailed error dialog when user taps "Details"
            when (result) {
                SnackbarResult.ActionPerformed -> {
                    showErrorDialog = error
                }
                SnackbarResult.Dismissed -> {
                    // User dismissed, clear error
                    viewModel.clearError()
                }
            }
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            VpnHeaderBar(
                isVpnRunning = uiState.isVpnRunning,
                status = uiState.vpnStatus,
                dataRateMbps = uiState.dataRateMbps,
                onToggleVpn = { enabled ->
                    if (enabled) {
                        val intent = VpnService.prepare(context)
                        if (intent != null) {
                            vpnPermissionLauncher.launch(intent)
                        } else {
                            viewModel.startVpn(context)
                        }
                    } else {
                        viewModel.stopVpn(context)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .testTag("settings_screen")
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

            // Section 3: DNS Tunnel Preference
            DnsTunnelPreference(
                configs = uiState.vpnConfigs,
                selectedTunnelId = uiState.defaultDnsTunnelId,
                onTunnelSelected = { tunnelId -> viewModel.setDefaultDnsTunnel(tunnelId) }
            )

            Divider(modifier = Modifier.padding(vertical = 16.dp))

            // Section 4: App Routing Rules
            AppRuleSection(
                installedApps = uiState.installedApps,
                appRules = uiState.appRules,
                vpnConfigs = uiState.vpnConfigs,
                onRuleChanged = { pkg, id -> viewModel.saveAppRule(pkg, id) }
            )
        }
        
        // Error Detail Dialog
        showErrorDialog?.let { error ->
            AlertDialog(
                onDismissRequest = {
                    showErrorDialog = null
                    viewModel.clearError()
                },
                title = {
                    Text(
                        text = when (error.type) {
                            VpnError.ErrorType.AUTHENTICATION_FAILED -> "Authentication Failed"
                            VpnError.ErrorType.CONNECTION_FAILED -> "Connection Failed"
                            VpnError.ErrorType.CONFIG_ERROR -> "Configuration Error"
                            VpnError.ErrorType.INTERFACE_ERROR -> "VPN Interface Error"
                            VpnError.ErrorType.TUNNEL_ERROR -> "Tunnel Error"
                            VpnError.ErrorType.UNKNOWN -> "VPN Error"
                        },
                        color = MaterialTheme.colorScheme.error
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = error.getUserMessage(),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (error.tunnelId != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Tunnel: ${error.tunnelId}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showErrorDialog = null
                            viewModel.clearError()
                        }
                    ) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    if (error.type == VpnError.ErrorType.AUTHENTICATION_FAILED) {
                        TextButton(
                            onClick = {
                                // Scroll to credentials section (could be enhanced)
                                showErrorDialog = null
                                viewModel.clearError()
                            }
                        ) {
                            Text("Go to Credentials")
                        }
                    }
                }
            )
        }
    }
}
