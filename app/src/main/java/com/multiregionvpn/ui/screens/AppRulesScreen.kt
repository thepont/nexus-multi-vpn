package com.multiregionvpn.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.multiregionvpn.data.GeoBlockedApps
import com.multiregionvpn.ui.settings.SettingsViewModel
import com.multiregionvpn.ui.settings.InstalledApp

/**
 * App Rules Screen - Smart Per-App Routing
 * 
 * Features:
 * - Search bar for 343+ apps
 * - Smart ordering (configured → suggestions → others)
 * - Geo-blocked app detection
 * - Full-screen list (no scroll container)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRulesScreen(
    viewModel: SettingsViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    
    // Detect user's current region (for deprioritizing local apps)
    // TODO: Get from GeoIP or settings - for now assume AU based on your Pixel
    val userRegion = "AU"
    
    // Smart ordering and filtering
    val orderedApps = remember(uiState.installedApps, uiState.appRules, uiState.vpnConfigs, searchQuery, userRegion) {
        val filtered = if (searchQuery.isEmpty()) {
            uiState.installedApps
        } else {
            uiState.installedApps.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
        
        // Sort by priority (ASCENDING order, so use negative/inverted logic)
        filtered.sortedWith(compareBy(
            // 1. Apps WITH rules last (configured apps at top)
            { uiState.appRules[it.packageName] == null },
            
            // 2. Within unconfigured: Geo-blocked apps from OTHER regions first
            { app ->
                val recommendedRegion = GeoBlockedApps.getRecommendedRegion(app.packageName)
                when {
                    recommendedRegion == null -> 1 // Not geo-blocked (lower priority)
                    recommendedRegion == "Multiple" -> 0 // Multi-region apps (high priority)
                    recommendedRegion.equals(userRegion, ignoreCase = true) -> 2 // Local region (deprioritize)
                    else -> 0 // Foreign region (high priority - needs VPN!)
                }
            },
            
            // 3. Then alphabetically
            { it.name.lowercase() }
        ))
    }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            placeholder = { Text("Search ${uiState.installedApps.size} apps...") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Search")
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true
        )
        
        // App list (full screen, no wrapper scroll)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(
                items = orderedApps,
                key = { it.packageName }
            ) { app ->
                SmartAppRuleItem(
                    app = app,
                    currentRule = uiState.appRules[app.packageName],
                    vpnConfigs = uiState.vpnConfigs,
                    isGeoBlocked = GeoBlockedApps.isGeoBlocked(app.packageName),
                    recommendedRegion = GeoBlockedApps.getRecommendedRegion(app.packageName),
                    onRuleChanged = { vpnConfigId ->
                        viewModel.saveAppRule(app.packageName, vpnConfigId)
                    }
                )
            }
        }
    }
}

@Composable
fun SmartAppRuleItem(
    app: InstalledApp,
    currentRule: String?,
    vpnConfigs: List<com.multiregionvpn.data.database.VpnConfig>,
    isGeoBlocked: Boolean,
    recommendedRegion: String?,
    onRuleChanged: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    // Find matching tunnel for recommended region
    val suggestedTunnel = if (recommendedRegion != null) {
        vpnConfigs.find { it.regionId.equals(recommendedRegion, ignoreCase = true) }
    } else null
    
    ListItem(
        headlineContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(app.name)
                if (isGeoBlocked) {
                    Badge {
                        Text(
                            text = recommendedRegion ?: "GEO",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        },
        supportingContent = {
            Column {
                if (currentRule != null) {
                    val tunnel = vpnConfigs.find { it.id == currentRule }
                    Text(
                        "→ ${tunnel?.name ?: "Unknown"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (suggestedTunnel != null) {
                    Text(
                        "💡 Suggestion: ${suggestedTunnel.name}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        },
        leadingContent = {
            Image(
                painter = BitmapPainter(app.icon.toBitmap().asImageBitmap()),
                contentDescription = app.name,
                modifier = Modifier.size(40.dp)
            )
        },
        trailingContent = {
            Box {
                TextButton(onClick = { expanded = true }) {
                    Text(
                        text = when {
                            currentRule != null -> vpnConfigs.find { it.id == currentRule }?.name ?: "Set"
                            suggestedTunnel != null -> "Set"
                            else -> "None"
                        }
                    )
                }
                
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    // Suggestion first if available
                    if (suggestedTunnel != null && currentRule == null) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "💡 ${suggestedTunnel.name} (Recommended)",
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            onClick = {
                                onRuleChanged(suggestedTunnel.id)
                                expanded = false
                            }
                        )
                        HorizontalDivider()
                    }
                    
                    // No routing option
                    DropdownMenuItem(
                        text = { Text("Direct Internet (No VPN)") },
                        onClick = {
                            onRuleChanged(null)
                            expanded = false
                        }
                    )
                    
                    if (vpnConfigs.isNotEmpty()) {
                        HorizontalDivider()
                    }
                    
                    // All tunnels
                    vpnConfigs.forEach { config ->
                        DropdownMenuItem(
                            text = { Text(config.name) },
                            onClick = {
                                onRuleChanged(config.id)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    )
}


