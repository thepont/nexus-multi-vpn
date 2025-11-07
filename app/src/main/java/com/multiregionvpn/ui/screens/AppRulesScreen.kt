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
import androidx.compose.ui.graphics.Color
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
    
    // Detect user's current region (for smart routing logic)
    // TODO: Get from GeoIP or settings - for now assume AU based on your Pixel
    val userRegion = "AU" // Change this to "UK", "US", "FR", etc. based on actual location
    
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
        
        // Sort by priority (ASCENDING order)
        filtered.sortedWith(compareBy(
            // 1. Configured apps first (already working)
            { uiState.appRules[it.packageName] == null },
            
            // 2. Within unconfigured: Foreign geo-blocked apps first
            { app ->
                val recommendedRegion = GeoBlockedApps.getRecommendedRegion(app.packageName)
                when {
                    // Not geo-blocked - lowest priority
                    recommendedRegion == null -> 3
                    // Multi-region apps - high priority
                    recommendedRegion == "Multiple" -> 0
                    // Foreign region - highest priority (NEEDS VPN!)
                    !recommendedRegion.equals(userRegion, ignoreCase = true) -> 0
                    // Local region - still geo-blocked but works locally
                    else -> 2
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
                    userRegion = userRegion,
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
    userRegion: String,
    onRuleChanged: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    // Find matching tunnel for recommended region
    val suggestedTunnel = if (recommendedRegion != null) {
        vpnConfigs.find { it.regionId.equals(recommendedRegion, ignoreCase = true) }
    } else null
    
    // Determine if app is properly configured for its region
    val isProperlyRouted = when {
        // Not a geo-blocked app - always works
        recommendedRegion == null -> true
        
        // Multi-region app - works with Direct Internet OR any tunnel
        recommendedRegion == "Multiple" -> true
        
        // App is for user's current region - Direct Internet (null) is valid!
        recommendedRegion.equals(userRegion, ignoreCase = true) && currentRule == null -> true
        
        // App routed through matching tunnel
        currentRule != null -> {
            val currentTunnel = vpnConfigs.find { it.id == currentRule }
            currentTunnel?.regionId?.equals(recommendedRegion, ignoreCase = true) == true
        }
        
        // Otherwise not properly routed
        else -> false
    }
    
    // Badge color: Green if working, Gray if needs setup
    val badgeColor = if (isProperlyRouted) {
        Color(0xFF4CAF50) // Green - Properly routed
    } else {
        Color(0xFF9E9E9E) // Gray - Needs configuration
    }
    
    ListItem(
        headlineContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(app.name)
                if (isGeoBlocked) {
                    Badge(
                        containerColor = badgeColor,
                        contentColor = Color.White
                    ) {
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


