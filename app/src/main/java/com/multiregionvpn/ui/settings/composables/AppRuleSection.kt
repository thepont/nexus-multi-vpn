package com.multiregionvpn.ui.settings.composables

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.multiregionvpn.data.database.AppRule
import com.multiregionvpn.data.database.ProviderAccountEntity
import com.multiregionvpn.data.database.VpnConfig
import com.multiregionvpn.ui.settings.InstalledApp
import android.graphics.drawable.BitmapDrawable
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRuleSection(
    installedApps: List<InstalledApp>,
    appRules: Map<String, AppRule>,
    vpnConfigs: List<VpnConfig>,
    providerAccounts: List<ProviderAccountEntity>,
    onRuleChanged: (String, String?) -> Unit,
    onJitRuleChanged: (String, String?, String?, String?, String?, Boolean) -> Unit
) {
    Column {
        Text("App Routing Rules", style = MaterialTheme.typography.titleLarge)
        
        LazyColumn(
            modifier = Modifier.height(400.dp) // Give it a fixed height in a scrolling screen
        ) {
            items(installedApps, key = { it.packageName }) { app ->
                val rule = appRules[app.packageName]
                var showJitOptions by remember { mutableStateOf(rule?.providerAccountId != null) }
                var selectedConfigId by remember(rule) { 
                    mutableStateOf(rule?.vpnConfigId) 
                }
                var selectedProviderAccountId by remember(rule) {
                    mutableStateOf(rule?.providerAccountId)
                }
                var selectedRegion by remember(rule) {
                    mutableStateOf(rule?.regionCode ?: "UK")
                }
                var selectedProtocol by remember(rule) {
                    mutableStateOf(rule?.preferredProtocol)
                }
                var fallbackDirect by remember(rule) {
                    mutableStateOf(rule?.fallbackDirect ?: false)
                }
                var isDropdownExpanded by remember { mutableStateOf(false) }

                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Convert Drawable to ImageBitmap for Compose
                            val bitmap = remember(app.icon) {
                                if (app.icon is BitmapDrawable) {
                                    app.icon.bitmap.asImageBitmap()
                                } else {
                                    app.icon.toBitmap().asImageBitmap()
                                }
                            }
                            Image(
                                bitmap = bitmap,
                                contentDescription = "${app.name} icon",
                                modifier = Modifier.size(40.dp),
                                contentScale = ContentScale.Fit
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.name, style = MaterialTheme.typography.titleMedium)
                                
                                ExposedDropdownMenuBox(
                                    expanded = isDropdownExpanded,
                                    onExpandedChange = { isDropdownExpanded = !isDropdownExpanded }
                                ) {
                                    val selectedText = when {
                                        selectedProviderAccountId != null -> "JIT VPN (${providerAccounts.find { it.id == selectedProviderAccountId }?.displayLabel ?: "Unknown"})"
                                        selectedConfigId != null -> vpnConfigs.firstOrNull { it.id == selectedConfigId }?.name ?: "Unknown"
                                        else -> "Direct Internet"
                                    }
                                    
                                    OutlinedTextField(
                                        value = selectedText,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Routing") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDropdownExpanded) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .menuAnchor()
                                            .testTag("app_rule_dropdown_${app.packageName}")
                                    )
                                    
                                    DropdownMenu(
                                        expanded = isDropdownExpanded,
                                        onDismissRequest = { isDropdownExpanded = false }
                                    ) {
                                        // "Direct Internet" option
                                        DropdownMenuItem(
                                            text = { Text("Direct Internet") },
                                            onClick = {
                                                selectedConfigId = null
                                                selectedProviderAccountId = null
                                                showJitOptions = false
                                                isDropdownExpanded = false
                                                onRuleChanged(app.packageName, null)
                                            }
                                        )
                                        // Static VPN configs
                                        vpnConfigs.forEach { config ->
                                            DropdownMenuItem(
                                                text = { Text(config.name) },
                                                onClick = {
                                                    selectedConfigId = config.id
                                                    selectedProviderAccountId = null
                                                    showJitOptions = false
                                                    isDropdownExpanded = false
                                                    onRuleChanged(app.packageName, config.id)
                                                }
                                            )
                                        }
                                        // JIT VPN option (if provider accounts exist)
                                        if (providerAccounts.isNotEmpty()) {
                                            DropdownMenuItem(
                                                text = { Text("JIT VPN (On-Demand)") },
                                                onClick = {
                                                    selectedConfigId = null
                                                    selectedProviderAccountId = providerAccounts.firstOrNull()?.id
                                                    showJitOptions = true
                                                    isDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        
                        // JIT VPN options
                        if (showJitOptions && selectedProviderAccountId != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Divider()
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Provider account selection
                            var providerDropdownExpanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = providerDropdownExpanded,
                                onExpandedChange = { providerDropdownExpanded = !providerDropdownExpanded }
                            ) {
                                OutlinedTextField(
                                    value = providerAccounts.find { it.id == selectedProviderAccountId }?.displayLabel ?: "Select Account",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Provider Account") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerDropdownExpanded) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor()
                                )
                                ExposedDropdownMenu(
                                    expanded = providerDropdownExpanded,
                                    onDismissRequest = { providerDropdownExpanded = false }
                                ) {
                                    providerAccounts.forEach { account ->
                                        DropdownMenuItem(
                                            text = { Text(account.displayLabel) },
                                            onClick = {
                                                selectedProviderAccountId = account.id
                                                providerDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Region selection
                            var regionDropdownExpanded by remember { mutableStateOf(false) }
                            val regions = listOf("UK", "FR", "US", "DE", "AU", "CA", "JP", "IT", "ES", "NL")
                            ExposedDropdownMenuBox(
                                expanded = regionDropdownExpanded,
                                onExpandedChange = { regionDropdownExpanded = !regionDropdownExpanded }
                            ) {
                                OutlinedTextField(
                                    value = selectedRegion,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Region") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = regionDropdownExpanded) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor()
                                )
                                ExposedDropdownMenu(
                                    expanded = regionDropdownExpanded,
                                    onDismissRequest = { regionDropdownExpanded = false }
                                ) {
                                    regions.forEach { region ->
                                        DropdownMenuItem(
                                            text = { Text(region) },
                                            onClick = {
                                                selectedRegion = region
                                                regionDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Protocol selection
                            var protocolDropdownExpanded by remember { mutableStateOf(false) }
                            val protocols = listOf("openvpn_udp", "wireguard", null)
                            ExposedDropdownMenuBox(
                                expanded = protocolDropdownExpanded,
                                onExpandedChange = { protocolDropdownExpanded = !protocolDropdownExpanded }
                            ) {
                                OutlinedTextField(
                                    value = selectedProtocol ?: "Auto",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Protocol") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = protocolDropdownExpanded) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor()
                                )
                                ExposedDropdownMenu(
                                    expanded = protocolDropdownExpanded,
                                    onDismissRequest = { protocolDropdownExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Auto") },
                                        onClick = {
                                            selectedProtocol = null
                                            protocolDropdownExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("OpenVPN UDP") },
                                        onClick = {
                                            selectedProtocol = "openvpn_udp"
                                            protocolDropdownExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("WireGuard") },
                                        onClick = {
                                            selectedProtocol = "wireguard"
                                            protocolDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Fallback option
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Fallback to Direct Internet on Failure")
                                Switch(
                                    checked = fallbackDirect,
                                    onCheckedChange = { fallbackDirect = it }
                                )
                            }
                            
                            // Save button
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    onJitRuleChanged(
                                        app.packageName,
                                        null,
                                        selectedProviderAccountId,
                                        selectedRegion,
                                        selectedProtocol,
                                        fallbackDirect
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Save JIT VPN Rule")
                            }
                        }
                    }
                }
            }
        }
    }
}
