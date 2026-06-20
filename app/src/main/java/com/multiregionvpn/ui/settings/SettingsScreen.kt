package com.multiregionvpn.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.multiregionvpn.ui.settings.composables.AppRuleCard
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
    
    LaunchedEffect(uiState.currentError) {
        uiState.currentError?.let { error ->
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
            
            when (result) {
                SnackbarResult.ActionPerformed -> showErrorDialog = error
                SnackbarResult.Dismissed -> viewModel.clearError()
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
                        if (intent != null) vpnPermissionLauncher.launch(intent)
                        else viewModel.startVpn(context)
                    } else {
                        viewModel.stopVpn(context)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .testTag("settings_screen")
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                ProviderCredentialsSection(
                    credentials = uiState.nordCredentials,
                    onSaveCredentials = { u, p -> viewModel.saveNordCredentials(u, p) }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            }

            item {
                VpnConfigSection(
                    configs = uiState.vpnConfigs,
                    onSaveConfig = { config -> viewModel.saveVpnConfig(config) },
                    onDeleteConfig = { id -> viewModel.deleteVpnConfig(id) },
                    onFetchNordVpnServer = { regionId, callback -> viewModel.fetchNordVpnServer(regionId, callback) }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            }

            item {
                Text("App Routing Rules", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
            }

            items(uiState.installedApps, key = { it.packageName }) { app ->
                AppRuleCard(
                    app = app,
                    selectedConfigId = uiState.appRules[app.packageName],
                    vpnConfigs = uiState.vpnConfigs,
                    onRuleChanged = { pkg, id -> viewModel.saveAppRule(pkg, id) }
                )
            }
        }
        
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
                        modifier = Modifier.fillMaxWidth()
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
                    Button(onClick = {
                        showErrorDialog = null
                        viewModel.clearError()
                    }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}
