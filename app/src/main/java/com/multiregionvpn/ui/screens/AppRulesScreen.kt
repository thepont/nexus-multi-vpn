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
import com.multiregionvpn.ui.shared.RouterViewModel
import com.multiregionvpn.ui.shared.AppRule

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
    viewModel: RouterViewModel
) {
    val installedApps by viewModel.allInstalledApps.collectAsState()
    val vpnConfigs by viewModel.allVpnConfigs.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    
    // Detect user's current region (for smart routing logic)
    // TODO: Get from GeoIP or settings - for now assume AU based on your Pixel
    val userRegion = "AU" // Change this to "UK", "US", "FR", etc. based on actual location
    
    // Smart ordering and filtering
    val orderedApps = remember(installedApps, vpnConfigs, searchQuery, userRegion) {
        val filtered = if (searchQuery.isEmpty()) {
            installedApps
        } else {
            installedApps.filter {
                it.appName.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
        
        // Sort by priority (ASCENDING order)
        filtered.sortedWith(compareBy(
            // 1. Configured apps first (already working)
            { it.routedGroupId == null },
            
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
            { it.appName.lowercase() }
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
            placeholder = { Text("Search ${installedApps.size} apps...") },
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
                    vpnConfigs = vpnConfigs,
                    isGeoBlocked = GeoBlockedApps.isGeoBlocked(app.packageName),
                    recommendedRegion = GeoBlockedApps.getRecommendedRegion(app.packageName),
                    userRegion = userRegion,
                    onRuleChanged = { newGroupId ->
                        viewModel.onAppRuleChange(app, newGroupId)
                    }
                )
            }
        }
    }
}

@Composable
fun SmartAppRuleItem(
    app: AppRule,
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
        recommendedRegion.equals(userRegion, ignoreCase = true) && app.routedGroupId == null -> true
        
        // App routed through matching region group
        app.routedGroupId != null -> {
            app.routedGroupId.equals(recommendedRegion, ignoreCase = true)
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
                Text(app.appName)
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
                if (app.routedGroupId != null) {
                    val tunnel = vpnConfigs.find { it.regionId == app.routedGroupId }
                    Text(
                        "→ ${tunnel?.name ?: app.routedGroupId.uppercase()}",
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
            app.icon?.let { icon ->
                Image(
                    painter = BitmapPainter(icon.toBitmap().asImageBitmap()),
                    contentDescription = app.appName,
                    modifier = Modifier.size(40.dp)
                )
            }
        },
        trailingContent = {
            Box {
                TextButton(onClick = { expanded = true }) {
                    Text(
                        text = when {
                            app.routedGroupId != null -> {
                                val tunnel = vpnConfigs.find { it.regionId == app.routedGroupId }
                                tunnel?.name ?: app.routedGroupId.uppercase()
                            }
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
                    if (suggestedTunnel != null && app.routedGroupId == null) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "💡 ${suggestedTunnel.name} (Recommended)",
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            onClick = {
                                onRuleChanged(suggestedTunnel.regionId)
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
                    
                    // Group tunnels by region
                    val regions = vpnConfigs.map { it.regionId }.distinct()
                    regions.forEach { regionId ->
                        val regionConfigs = vpnConfigs.filter { it.regionId == regionId }
                        val regionName = regionConfigs.firstOrNull()?.name ?: regionId.uppercase()
                        
                        DropdownMenuItem(
                            text = { Text(regionName) },
                            onClick = {
                                onRuleChanged(regionId)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    )
}
