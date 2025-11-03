package com.multiregionvpn.ui.settings.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ExperimentalMaterial3Api
import com.multiregionvpn.data.database.VpnConfig
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VpnConfigDialog(
    config: VpnConfig?, // Null if adding new
    onDismiss: () -> Unit,
    onSave: (VpnConfig) -> Unit,
    onFetchNordVpnServer: ((String, (String?) -> Unit) -> Unit)? = null // regionId, callback -> void
) {
    val isEditing = config != null
    var name by remember { mutableStateOf(config?.name ?: "") }
    var region by remember { mutableStateOf(config?.regionId ?: "UK") }
    var server by remember { mutableStateOf(config?.serverHostname ?: "") }
    var template by remember { mutableStateOf(config?.templateId ?: "nordvpn") }
    var isFetchingServer by remember { mutableStateOf(false) }
    var serverError by remember { mutableStateOf<String?>(null) }
    
    // Region list
    val regionList = listOf("UK", "FR", "AU", "US", "DE", "CA", "JP", "IT", "ES", "NL")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit Server" else "Add Server") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Friendly Name") },
                    placeholder = { Text("e.g., My Nord UK") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("config_name_textfield")
                )
                Spacer(modifier = Modifier.height(10.dp))
                
                // Provider dropdown
                var providerExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = providerExpanded,
                    onExpandedChange = { providerExpanded = !providerExpanded }
                ) {
                    OutlinedTextField(
                        value = when (template) {
                            "nordvpn" -> "NordVPN"
                            "customopenvpn" -> "Custom OpenVPN"
                            else -> template
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Provider") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                            .testTag("config_provider_dropdown")
                    )
                    DropdownMenu(
                        expanded = providerExpanded,
                        onDismissRequest = { providerExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("NordVPN") },
                            onClick = {
                                template = "nordvpn"
                                providerExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Custom OpenVPN") },
                            onClick = {
                                template = "customopenvpn"
                                providerExpanded = false
                            }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(10.dp))
                
                // Region dropdown
                var regionExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = regionExpanded,
                    onExpandedChange = { regionExpanded = !regionExpanded }
                ) {
                    OutlinedTextField(
                        value = region,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Region") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = regionExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                            .testTag("config_region_dropdown")
                    )
                    DropdownMenu(
                        expanded = regionExpanded,
                        onDismissRequest = { regionExpanded = false }
                    ) {
                        regionList.forEach { reg ->
                            DropdownMenuItem(
                                text = { Text(reg) },
                                onClick = {
                                    region = reg
                                    regionExpanded = false
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(10.dp))
                
                // Server address field (shown only for Custom OpenVPN OR when editing existing config)
                if (template == "customopenvpn" || isEditing) {
                    OutlinedTextField(
                        value = server,
                        onValueChange = { server = it },
                        label = { Text("Server Hostname") },
                        placeholder = { Text("e.g., uk1234.nordvpn.com") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("config_server_textfield"),
                        enabled = !isFetchingServer
                    )
                } else if (template == "nordvpn" && !isEditing) {
                    // Show fetching indicator or error for NordVPN auto-fetch
                    if (isFetchingServer) {
                        Column {
                            CircularProgressIndicator()
                            Text("Fetching best server from NordVPN...")
                        }
                    } else if (serverError != null) {
                        Text("Error: $serverError", color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                    } else if (server.isNotBlank()) {
                        Text("Server: $server")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val finalHostname = if (template == "nordvpn" && server.isBlank() && !isEditing) {
                            // For new NordVPN configs, server will be auto-fetched
                            // For now, use placeholder - should be set before calling onSave
                            "auto-fetch-needed"
                        } else {
                            server.ifBlank { "auto" }
                        }
                        
                        val newConfig = VpnConfig(
                            id = config?.id ?: UUID.randomUUID().toString(),
                            name = name,
                            regionId = region,
                            templateId = template,
                            serverHostname = finalHostname
                        )
                        onSave(newConfig)
                    }
                },
                enabled = !isFetchingServer && name.isNotBlank(),
                modifier = Modifier.testTag("config_save_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
    
    // Auto-fetch NordVPN server when template is NordVPN and region is selected
    if (template == "nordvpn" && !isEditing && onFetchNordVpnServer != null) {
        LaunchedEffect(region) {
            if (server.isBlank() || server == "auto-fetch-needed") {
                isFetchingServer = true
                serverError = null
                try {
                    onFetchNordVpnServer(region) { fetchedServer ->
                        if (fetchedServer != null) {
                            server = fetchedServer
                        } else {
                            serverError = "Could not fetch server. Please enter manually."
                        }
                        isFetchingServer = false
                    }
                } catch (e: Exception) {
                    serverError = e.message ?: "Unknown error"
                    isFetchingServer = false
                }
            }
        }
    }
}