package com.multiregionvpn.ui.tv

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.multiregionvpn.ui.shared.AppRule
import com.multiregionvpn.ui.shared.RouterViewModel

/**
 * TV App Rules Screen - Application routing configuration
 * 
 * Shows:
 * - List of installed apps
 * - Current routing rule for each app
 * - Option to change rule (Bypass / Route via Group / Block)
 * 
 * D-pad navigation:
 * - Up/Down: Navigate app list
 * - Select: Open routing menu
 * - Back: Close menu
 */
@Composable
fun TvAppRulesScreen(
    viewModel: RouterViewModel
) {
    val installedApps by viewModel.allInstalledApps.collectAsState()
    val serverGroups by viewModel.allServerGroups.collectAsState()
    var selectedApp by remember { mutableStateOf<AppRule?>(null) }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Title
        Text(
            text = "App Routing Rules",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        if (installedApps.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Loading installed apps...",
                    fontSize = 24.sp,
                    color = Color(0xFF9E9E9E)
                )
            }
        } else {
            // App list (ALL installed apps)
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(installedApps) { app ->
                    TvAppRuleCard(
                        app = app,
                        serverGroups = serverGroups,
                        onClick = { selectedApp = app },
                        onRuleChange = { newGroupId ->
                            viewModel.onAppRuleChange(app, newGroupId)
                            selectedApp = null
                        }
                    )
                }
            }
        }
    }
    
    // Routing menu dialog
    selectedApp?.let { app ->
        TvRoutingMenu(
            app = app,
            serverGroups = serverGroups,
            onDismiss = { selectedApp = null },
            onSelectGroup = { groupId ->
                viewModel.onAppRuleChange(app, groupId)
                selectedApp = null
            }
        )
    }
}

/**
 * TV App Rule Card - Individual app item
 * 
 * Layout:
 * ┌──────────────────────────────────────────────────────────────┐
 * │ [Icon] BBC iPlayer                  Route via → United Kingdom│
 * │        com.bbc.iplayer                                        │
 * └──────────────────────────────────────────────────────────────┘
 */
@Composable
fun TvAppRuleCard(
    app: AppRule,
    serverGroups: List<com.multiregionvpn.ui.shared.ServerGroup>,
    onClick: () -> Unit,
    onRuleChange: (String?) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .focusable()
            .onFocusChanged { isFocused = it.isFocused }
            .then(
                if (isFocused) {
                    Modifier.border(
                        width = 3.dp,
                        color = Color(0xFF2196F3), // Blue focus indicator
                        shape = RoundedCornerShape(12.dp)
                    )
                } else {
                    Modifier
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF151B28)
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isFocused) 8.dp else 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: App info
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // App icon
                app.icon?.let { drawable ->
                    val bitmap = drawable.toBitmap()
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                } ?: run {
                    // Placeholder if no icon
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(0xFF424242), RoundedCornerShape(8.dp))
                    )
                }
                
                // App name and package
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Line 1: App name (large, bold)
                    Text(
                        text = app.appName,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    
                    // Line 2: Package name (smaller, gray, monospace)
                    Text(
                        text = app.packageName,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF9E9E9E)
                    )
                }
            }
            
            // Right: Current rule
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val groupName = when (app.routedGroupId) {
                    null -> "Bypass VPN"
                    "block" -> "Block All Traffic"
                    else -> serverGroups.firstOrNull { it.id == app.routedGroupId }?.name ?: app.routedGroupId
                }
                
                Text(
                    text = "Route via →",
                    fontSize = 14.sp,
                    color = Color(0xFF9E9E9E)
                )
                
                Text(
                    text = groupName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (app.routedGroupId) {
                        null -> Color(0xFF9E9E9E) // Gray for bypass
                        "block" -> Color(0xFFF44336) // Red for block
                        else -> Color(0xFF4CAF50) // Green for routed
                    }
                )
            }
        }
    }
}

/**
 * TV Routing Menu - Dialog to change app routing
 * 
 * Shows:
 * - Bypass VPN (direct internet)
 * - Route via [Group Name] (for each server group)
 * - Block All Traffic
 */
@Composable
fun TvRoutingMenu(
    app: AppRule,
    serverGroups: List<com.multiregionvpn.ui.shared.ServerGroup>,
    onDismiss: () -> Unit,
    onSelectGroup: (String?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Route ${app.appName}",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Bypass option
                TvRoutingOption(
                    label = "Bypass VPN (Direct Internet)",
                    isSelected = app.routedGroupId == null,
                    onClick = { onSelectGroup(null) }
                )
                
                // Route via group options
                serverGroups.forEach { group ->
                    TvRoutingOption(
                        label = "Route via ${group.name}",
                        isSelected = app.routedGroupId == group.id,
                        onClick = { onSelectGroup(group.id) }
                    )
                }
                
                // Block option
                TvRoutingOption(
                    label = "Block All Traffic",
                    isSelected = app.routedGroupId == "block",
                    onClick = { onSelectGroup("block") }
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close", fontSize = 16.sp)
            }
        }
    )
}

/**
 * TV Routing Option - Single option in routing menu
 */
@Composable
fun TvRoutingOption(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .focusable()
            .onFocusChanged { isFocused = it.isFocused },
        colors = ButtonDefaults.buttonColors(
            containerColor = when {
                isSelected -> Color(0xFF4CAF50) // Green if selected
                isFocused -> Color(0xFF2196F3) // Blue if focused
                else -> Color(0xFF424242) // Gray otherwise
            }
        )
    ) {
        Text(
            text = label,
            fontSize = 18.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

