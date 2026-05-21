package com.multiregionvpn.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Professional VPN Header Bar (NOC Style)
 * 
 * Persistent header showing VPN status, data rate, and quick toggle.
 * Designed for "Network Operations Center" aesthetics.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VpnHeaderBar(
    isVpnRunning: Boolean,
    status: VpnStatus,
    dataRateMbps: Double = 0.0,
    onToggleVpn: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    // Animate status color
    val statusColor by animateColorAsState(
        targetValue = when (status) {
            VpnStatus.PROTECTED -> Color(0xFF4CAF50)  // Green
            VpnStatus.CONNECTING -> Color(0xFF2196F3) // Blue
            VpnStatus.DISCONNECTED -> Color(0xFF9E9E9E) // Gray
            VpnStatus.ERROR -> Color(0xFFF44336) // Red
        },
        label = "statusColor"
    )
    
    TopAppBar(
        modifier = modifier.testTag("vpn_header_bar"),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // App icon
                Icon(
                    imageVector = Icons.Filled.Shield,
                    contentDescription = "VPN Router",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                
                // App name
                Text(
                    text = "Region Router",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        actions = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 16.dp)
            ) {
                // Status indicator with data rate
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    // Status text
                    Text(
                        text = status.displayText,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        ),
                        color = statusColor
                    )
                    
                    // Data rate (only show when protected)
                    if (status == VpnStatus.PROTECTED && dataRateMbps > 0.0) {
                        Text(
                            text = String.format("%.1f MB/s", dataRateMbps),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (status == VpnStatus.CONNECTING) {
                        Text(
                            text = "Initializing...",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Compact toggle switch
                Switch(
                    checked = isVpnRunning,
                    onCheckedChange = onToggleVpn,
                    modifier = Modifier
                        .height(32.dp)
                        .testTag("start_service_toggle")
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

/**
 * VPN Status States
 */
enum class VpnStatus(val displayText: String) {
    PROTECTED("Protected"),
    CONNECTING("Connecting"),
    DISCONNECTED("Disconnected"),
    ERROR("Error")
}

