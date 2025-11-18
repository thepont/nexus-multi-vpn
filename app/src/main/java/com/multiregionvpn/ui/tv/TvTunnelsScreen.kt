package com.multiregionvpn.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.multiregionvpn.ui.shared.RouterViewModel
import com.multiregionvpn.ui.shared.ServerGroup

@Composable
fun TvTunnelsScreen(
    viewModel: RouterViewModel
) {
    val serverGroups by viewModel.allServerGroups.collectAsState()
    val selectedGroup by viewModel.selectedServerGroup.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            text = "VPN Tunnels",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier
                .padding(bottom = 24.dp)
                .semantics { contentDescription = "VPN Tunnels" }
        )

        if (serverGroups.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "No VPN tunnels configured",
                        fontSize = 24.sp,
                        color = Color(0xFF9E9E9E)
                    )
                    Button(
                        onClick = { viewModel.onAddServerGroup() }
                    ) {
                        Text("Add Tunnel", fontSize = 18.sp)
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(serverGroups) { group ->
                    TvTunnelCard(
                        group = group,
                        isSelected = selectedGroup?.id == group.id,
                        onClick = { viewModel.onServerGroupSelected(group) },
                        onDelete = { viewModel.onRemoveServerGroup(group) }
                    )
                }
            }
        }
    }
}

@Composable
fun TvTunnelCard(
    group: ServerGroup,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .focusable()
            .onFocusChanged { isFocused = it.isFocused }
            .then(
                if (isFocused) {
                    Modifier.border(
                        width = 3.dp,
                        color = Color(0xFF2196F3),
                        shape = RoundedCornerShape(12.dp)
                    )
                } else {
                    Modifier
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF1E2433) else Color(0xFF151B28)
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isFocused) 8.dp else 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Circle,
                    contentDescription = null,
                    tint = if (group.isConnected) Color(0xFF4CAF50) else Color(0xFF9E9E9E),
                    modifier = Modifier.size(16.dp)
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = group.name,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.semantics { contentDescription = group.name }
                    )

                    Text(
                        text = "${group.serverCount} server${if (group.serverCount != 1) "s" else ""} available",
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF9E9E9E)
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Connection status
                Text(
                    text = when {
                        group.isConnected -> {
                            if (group.latencyMs != null) {
                                "Connected (${group.latencyMs}ms)"
                            } else {
                                "Connected"
                            }
                        }
                        group.isActive -> "Connecting..."
                        else -> "Idle"
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        group.isConnected -> Color(0xFF4CAF50) // Green
                        group.isActive -> Color(0xFF2196F3) // Blue (connecting)
                        else -> Color(0xFF9E9E9E) // Gray (idle)
                    }
                )
                
                // Latency indicator (if connected)
                if (group.isConnected && group.latencyMs != null) {
                    Text(
                        text = "${group.latencyMs}ms",
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF81C784) // Light green
                    )
                }
            }
        }
    }
}
