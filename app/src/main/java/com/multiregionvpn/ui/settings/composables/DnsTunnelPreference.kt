package com.multiregionvpn.ui.settings.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DnsTunnelPreference(
    configs: List<VpnConfig>,
    selectedTunnelId: String?,
    onTunnelSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    Column(modifier = modifier) {
        Text(
            "Default DNS Tunnel",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold
            )
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "DNS queries are routed to this tunnel when no specific app rule applies. " +
            "If not set, DNS queries route to the first available tunnel.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            val selectedConfig = configs.find { it.id == selectedTunnelId }
            val displayText = when {
                selectedTunnelId == null -> "Auto (first available tunnel)"
                selectedConfig != null -> selectedConfig.name
                else -> "Unknown tunnel"
            }
            
            OutlinedTextField(
                value = displayText,
                onValueChange = {},
                readOnly = true,
                label = { Text("DNS Tunnel") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
                    .testTag("dns_tunnel_dropdown")
            )
            
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                // "Auto" option
                DropdownMenuItem(
                    text = { Text("Auto (first available tunnel)") },
                    onClick = {
                        onTunnelSelected(null)
                        expanded = false
                    },
                    modifier = Modifier.testTag("dns_tunnel_option_auto")
                )
                
                // Individual tunnel options
                configs.forEach { config ->
                    val tunnelId = "${config.templateId}_${config.regionId}"
                    DropdownMenuItem(
                        text = { 
                            Column {
                                Text(config.name)
                                Text(
                                    "${config.regionId} - ${config.serverHostname}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = {
                            onTunnelSelected(tunnelId)
                            expanded = false
                        },
                        modifier = Modifier.testTag("dns_tunnel_option_${config.id}")
                    )
                }
            }
        }
    }
}
