package com.multiregionvpn.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.multiregionvpn.ui.shared.RouterViewModel
import com.multiregionvpn.ui.shared.RouterViewModelImpl
import com.multiregionvpn.ui.shared.VpnStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvMainScreen(
    viewModel: RouterViewModel = hiltViewModel<RouterViewModelImpl>()
) {
    val vpnStatus by viewModel.vpnStatus.collectAsState()
    val liveStats by viewModel.liveStats.collectAsState()
    var selectedTab by remember { mutableStateOf(TvTab.TUNNELS) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0E1A))
    ) {
        TvHeaderBar(
            vpnStatus = vpnStatus,
            liveStats = liveStats,
            onToggleVpn = { enable -> viewModel.onToggleVpn(enable) }
        )

        TvTabRow(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 24.dp)
        ) {
            when (selectedTab) {
                TvTab.TUNNELS -> TvTunnelsScreen(viewModel)
                TvTab.APP_RULES -> TvAppRulesScreen(viewModel)
                TvTab.SETTINGS -> TvSettingsScreen()
            }
        }
    }
}

@Composable
fun TvHeaderBar(
    vpnStatus: VpnStatus,
    liveStats: com.multiregionvpn.ui.shared.VpnStats,
    onToggleVpn: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        color = Color(0xFF121828),
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Multi-Region VPN",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.semantics { contentDescription = "Multi-Region VPN" }
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            color = when (vpnStatus) {
                                VpnStatus.CONNECTED -> Color(0xFF4CAF50)
                                VpnStatus.CONNECTING -> Color(0xFF2196F3)
                                VpnStatus.DISCONNECTED -> Color(0xFF9E9E9E)
                                VpnStatus.ERROR -> Color(0xFFF44336)
                            },
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                )

                Text(
                    text = when (vpnStatus) {
                        VpnStatus.CONNECTED -> {
                            val dataRateMbps = (liveStats.bytesReceived / 1_000_000.0).toFloat()
                            "Protected ${String.format("%.1f", dataRateMbps)} MB/s"
                        }
                        VpnStatus.CONNECTING -> "Connecting..."
                        VpnStatus.DISCONNECTED -> "Disconnected"
                        VpnStatus.ERROR -> "Error"
                    },
                    fontSize = 20.sp,
                    fontFamily = FontFamily.Monospace,
                    color = when (vpnStatus) {
                        VpnStatus.CONNECTED -> Color(0xFF4CAF50)
                        VpnStatus.CONNECTING -> Color(0xFF2196F3)
                        VpnStatus.DISCONNECTED -> Color(0xFF9E9E9E)
                        VpnStatus.ERROR -> Color(0xFFF44336)
                    }
                )
            }

            Switch(
                checked = vpnStatus == VpnStatus.CONNECTED || vpnStatus == VpnStatus.CONNECTING,
                onCheckedChange = onToggleVpn,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF4CAF50),
                    checkedTrackColor = Color(0xFF81C784),
                    uncheckedThumbColor = Color(0xFF9E9E9E),
                    uncheckedTrackColor = Color(0xFF424242)
                ),
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

@Composable
fun TvTabRow(
    selectedTab: TvTab,
    onTabSelected: (TvTab) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        color = Color(0xFF0F1419)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            TvTab.values().forEach { tab ->
                TvTabButton(
                    tab = tab,
                    isSelected = tab == selectedTab,
                    onClick = { onTabSelected(tab) }
                )
            }
        }
    }
}

@Composable
fun TvTabButton(
    tab: TvTab,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) Color(0xFF2196F3) else Color.Transparent,
            contentColor = if (isSelected) Color.White else Color(0xFF9E9E9E)
        ),
        modifier = Modifier
            .height(48.dp)
            .semantics { contentDescription = tab.title }
    ) {
        Text(
            text = tab.title,
            fontSize = 18.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

enum class TvTab(val title: String) {
    TUNNELS("Tunnels"),
    APP_RULES("App Rules"),
    SETTINGS("Settings")
}
