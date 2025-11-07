package com.multiregionvpn.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.multiregionvpn.data.database.VpnConfig

/**
 * Professional Tunnel List Item (NOC Style)
 * 
 * Shows:
 * - User alias (editable, prominent)
 * - Provider name + server + protocol (technical details)
 * - Flag icon for region
 * - Connection status with latency
 * - More menu for actions
 */
@Composable
fun TunnelListItem(
    config: VpnConfig,
    isConnected: Boolean = false,
    latencyMs: Int? = null,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onViewApps: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else 
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Region flag icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Placeholder for flag emoji/icon
                Text(
                    text = getRegionEmoji(config.regionId),
                    style = MaterialTheme.typography.headlineMedium
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Center: Tunnel info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Line 1: User alias (bold, prominent)
                Text(
                    text = config.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Line 2: Provider + server + protocol (gray, technical)
                val protocol = detectProtocol(config)
                Text(
                    text = buildString {
                        append(config.templateId.replaceFirstChar { it.uppercase() })
                        append(" - ")
                        append(config.regionId.uppercase())
                        append(" ($protocol)")
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                // Line 3: Server hostname (monospace, even smaller)
                Text(
                    text = config.serverHostname,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                
                // Connection status
                if (isConnected) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Color(0xFF4CAF50), CircleShape)
                        )
                        Text(
                            text = if (latencyMs != null) "Connected (${latencyMs}ms)" else "Connected",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = Color(0xFF4CAF50)
                        )
                    }
                }
            }
            
            // Right: More menu
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = {
                            showMenu = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("View Apps Using This") },
                        onClick = {
                            showMenu = false
                            onViewApps()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

/**
 * Get emoji flag for region
 */
private fun getRegionEmoji(regionId: String): String {
    return when (regionId.uppercase()) {
        "UK", "GB" -> "🇬🇧"
        "FR", "FRANCE" -> "🇫🇷"
        "US", "USA" -> "🇺🇸"
        "DE", "GERMANY" -> "🇩🇪"
        "JP", "JAPAN" -> "🇯🇵"
        "CA", "CANADA" -> "🇨🇦"
        "AU", "AUSTRALIA" -> "🇦🇺"
        "NL", "NETHERLANDS" -> "🇳🇱"
        "SE", "SWEDEN" -> "🇸🇪"
        "NO", "NORWAY" -> "🇳🇴"
        "CH", "SWITZERLAND" -> "🇨🇭"
        "ES", "SPAIN" -> "🇪🇸"
        "IT", "ITALY" -> "🇮🇹"
        else -> "🌍" // Globe for unknown
    }
}

/**
 * Detect protocol from config
 */
private fun detectProtocol(config: VpnConfig): String {
    // This would ideally come from the config itself
    // For now, we can infer from templateId or add a protocol field
    return when {
        config.templateId.contains("wireguard", ignoreCase = true) -> "WireGuard"
        config.templateId.contains("nord", ignoreCase = true) -> "OpenVPN"
        else -> "OpenVPN" // Default assumption
    }
}

