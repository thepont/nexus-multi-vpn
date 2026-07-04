package com.multiregionvpn.ui.settings.composables

import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.multiregionvpn.data.database.VpnConfig
import com.multiregionvpn.ui.settings.InstalledApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRuleCard(
    app: InstalledApp,
    selectedConfigId: String?,
    vpnConfigs: List<VpnConfig>,
    onRuleChanged: (String, String?) -> Unit
) {
    var isDropdownExpanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        ListItem(
            leadingContent = {
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
                        DropdownMenuItem(
                            text = { Text("Direct Internet") },
                            onClick = {
                                isDropdownExpanded = false
                                onRuleChanged(app.packageName, null)
                            }
                        )
                        vpnConfigs.forEach { config ->
                            DropdownMenuItem(
                                text = { Text(config.name) },
                                onClick = {
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
