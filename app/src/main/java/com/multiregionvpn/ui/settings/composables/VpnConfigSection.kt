package com.multiregionvpn.ui.settings.composables

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
import androidx.compose.ui.unit.dp
import com.multiregionvpn.data.database.VpnConfig

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
            Text("My VPN Servers", style = MaterialTheme.typography.titleLarge)
            IconButton(
                onClick = {
                    editingConfig = null // Ensure we're adding new
                    showDialog = true 
                },
                modifier = Modifier.testTag("add_vpn_config_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add VPN Server")
            }
        }
        
        if (configs.isEmpty()) {
            Text("No VPN servers configured.", style = MaterialTheme.typography.bodyMedium)
        } else {
            // This list is not virtualized, which is fine for a settings screen
            Column {
                configs.forEach { config ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("vpn_config_item_${config.name}")
                    ) {
                        ListItem(
                            headlineContent = { Text(config.name) },
                            supportingContent = { Text("Region: ${config.regionId}  •  ${config.serverHostname}") },
                            trailingContent = {
                                Row {
                                    IconButton(onClick = {
                                        editingConfig = config
                                        showDialog = true
                                    }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                                    }
                                    IconButton(onClick = { onDeleteConfig(config.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        )
                    }
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
