package com.multiregionvpn.ui.settings.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
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
        
        if (configs.isEmpty()) {
            Text("No tunnels configured.", style = MaterialTheme.typography.bodyMedium)
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                configs.forEach { config ->
                    TunnelListItem(
                        config = config,
                        isConnected = false, // TODO: Get actual connection status
                        latencyMs = null, // TODO: Get actual latency
                        onEdit = {
                            editingConfig = config
                            showDialog = true
                        },
                        onDelete = { onDeleteConfig(config.id) },
                        onViewApps = {
                            // TODO: Navigate to app rules filtered by this tunnel
                        },
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
