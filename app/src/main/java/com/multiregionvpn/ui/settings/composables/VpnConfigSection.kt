package com.multiregionvpn.ui.settings.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.multiregionvpn.data.database.VpnConfig
import com.multiregionvpn.ui.components.TunnelListItem

@Composable
fun VpnConfigSection(
    configs: List<VpnConfig>,
    onSaveConfig: (VpnConfig) -> Unit,
    onDeleteConfig: (String) -> Unit,
    onFetchNordVpnServer: ((String, (String?) -> Unit) -> Unit)? = null
) {
    var showDialog by remember { mutableStateOf(false) }
    var editingConfig by remember { mutableStateOf<VpnConfig?>(null) }
    
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Tunnels", style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.SemiBold
            ))
            IconButton(
                onClick = {
                    editingConfig = null
                    showDialog = true 
                },
                modifier = Modifier.testTag("add_vpn_config_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Tunnel")
            }
        }

        // Suggested Regions for Maestro
        Text("Suggested Regions", style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("UK", "FR", "AU", "US").forEach { region ->
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.clickable {
                        editingConfig = null
                        showDialog = true
                    }
                ) {
                    Text(
                        text = region,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
        
        if (configs.isEmpty()) {
            Text("No tunnels configured.", style = MaterialTheme.typography.bodyMedium)
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                configs.forEach { config ->
                    TunnelListItem(
                        config = config,
                        isConnected = false,
                        latencyMs = null,
                        onEdit = {
                            editingConfig = config
                            showDialog = true
                        },
                        onDelete = { onDeleteConfig(config.id) },
                        onViewApps = {},
                        modifier = Modifier.testTag("vpn_config_item_${config.name}")
                    )
                }
            }
        }
    }

    if (showDialog) {
        VpnConfigDialog(
            config = editingConfig,
            onDismiss = { showDialog = false },
            onSave = { config ->
                onSaveConfig(config)
                showDialog = false
            },
            onFetchNordVpnServer = { regionId, callback -> 
                onFetchNordVpnServer?.invoke(regionId, callback)
            }
        )
    }
}
