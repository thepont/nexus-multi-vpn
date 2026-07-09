package com.multiregionvpn.ui.settings.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.unit.dp
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
    appRules: Map<String, String?>,
    vpnConfigs: List<VpnConfig>,
    onRuleChanged: (String, String?) -> Unit
) {
    Column {
        Text("App Routing Rules", style = MaterialTheme.typography.titleLarge)
        
        LazyColumn(
            modifier = Modifier.height(400.dp) // Give it a fixed height in a scrolling screen
        ) {
            items(installedApps, key = { it.packageName }) { app ->
                var selectedConfigId by remember(appRules) { 
                    mutableStateOf(appRules[app.packageName]) 
                }
                var isDropdownExpanded by remember { mutableStateOf(false) }

                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    ListItem(
                        leadingContent = {
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
                        },
                        headlineContent = { Text(app.name) },
                        supportingContent = {
                            ExposedDropdownMenuBox(
                                expanded = isDropdownExpanded,
                                onExpandedChange = { isDropdownExpanded = !isDropdownExpanded }
                            ) {
                                val selectedText = vpnConfigs.firstOrNull { it.id == selectedConfigId }?.name ?: "Direct Internet"
                                
                                OutlinedTextField(
                                    value = selectedText,
                                    onValueChange = {},
                                    readOnly = true,
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
                                            isDropdownExpanded = false
                                            onRuleChanged(app.packageName, null)
                                        }
                                    )
                                    // Mapped VPN configs
                                    vpnConfigs.forEach { config ->
                                        DropdownMenuItem(
                                            text = { Text(config.name) },
                                            onClick = {
                                                selectedConfigId = config.id
                                                isDropdownExpanded = false
                                                onRuleChanged(app.packageName, config.id)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
