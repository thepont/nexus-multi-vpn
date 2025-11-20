package com.multiregionvpn.ui.navigation

import android.content.Context
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.multiregionvpn.ui.components.VpnHeaderBar
import com.multiregionvpn.ui.shared.RouterViewModel
import com.multiregionvpn.ui.shared.RouterViewModelImpl
import com.multiregionvpn.ui.screens.TunnelsScreen
import com.multiregionvpn.ui.screens.AppRulesScreen
import com.multiregionvpn.ui.screens.ConnectionsScreen
import com.multiregionvpn.ui.screens.SettingsScreen as ConfigScreen

/**
 * Main screen with tab navigation (NOC Style)
 * 
 * Tabs:
 * 1. Tunnels - VPN tunnel configuration
 * 2. App Rules - Per-app routing rules
 * 3. Connections - Connection log (performance-optimized)
 * 4. Settings - App settings and provider credentials
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: RouterViewModel = hiltViewModel<RouterViewModelImpl>()
) {
    val vpnStatus by viewModel.vpnStatus.collectAsState()
    val isVpnRunning by viewModel.isVpnRunning.collectAsState()
    val dataRateMbps by viewModel.dataRateMbps.collectAsState()
    var selectedTab by remember { mutableStateOf(NavTab.TUNNELS) }
    val context = LocalContext.current
    
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.onToggleVpn(true)
        }
    }
    
    Scaffold(
        topBar = {
            VpnHeaderBar(
                isVpnRunning = isVpnRunning,
                status = vpnStatus,
                dataRateMbps = dataRateMbps,
                onToggleVpn = { enabled ->
                    if (enabled) {
                        val intent = VpnService.prepare(context)
                        if (intent != null) {
                            vpnPermissionLauncher.launch(intent)
                        } else {
                            viewModel.onToggleVpn(true)
                        }
                    } else {
                        viewModel.onToggleVpn(false)
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavTab.values().forEach { tab ->
                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title
                            )
                        },
                        label = { Text(tab.title) },
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                NavTab.TUNNELS -> TunnelsScreen(viewModel = viewModel)
                NavTab.APP_RULES -> AppRulesScreen(viewModel = viewModel)
                NavTab.CONNECTIONS -> ConnectionsScreen()
                NavTab.SETTINGS -> ConfigScreen(viewModel = viewModel)
            }
        }
    }
}

/**
 * Navigation tabs
 */
enum class NavTab(
    val title: String,
    val icon: ImageVector
) {
    TUNNELS("Tunnels", Icons.Filled.VpnKey),
    APP_RULES("Apps", Icons.Filled.Apps),
    CONNECTIONS("Connections", Icons.Filled.Timeline),
    SETTINGS("Settings", Icons.Filled.Settings)
}

