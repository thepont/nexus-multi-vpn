@file:OptIn(ExperimentalMaterial3Api::class)

package com.multiregionvpn.ui.tv

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.multiregionvpn.ui.shared.AppRule
import com.multiregionvpn.ui.shared.RouterViewModel
import com.multiregionvpn.ui.shared.ServerGroup

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
        Text(
            text = "App Routing Rules",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier
                .padding(bottom = 24.dp)
                .semantics { contentDescription = "App Routing Rules" }
        )

        if (installedApps.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No installed apps detected",
                    fontSize = 24.sp,
                    color = Color(0xFF9E9E9E)
                )
            }
        } else {
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

@Composable
fun TvAppRuleCard(
    app: AppRule,
    serverGroups: List<ServerGroup>,
    onClick: () -> Unit,
    onRuleChange: (String?) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .focusable()
            .onFocusChanged { isFocused = it.isFocused }
            .then(
                if (isFocused) {
                    Modifier.border(
                        width = 3.dp,
                        color = Color(0xFF2196F3),
                        shape = RoundedCornerShape(12.dp)
                    )
                } else {
                    Modifier
                }
            )
            .testTag("tv_app_rule_card_${app.packageName}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) Color(0xFF1E2433) else Color(0xFF151B28)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                app.icon?.let { drawable ->
                    Image(
                        bitmap = drawable.toBitmap().asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = app.appName,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.semantics { contentDescription = app.appName }
                    )

                    Text(
                        text = app.packageName,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF9E9E9E)
                    )
                }
            }

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
                        null -> Color(0xFF9E9E9E)
                        "block" -> Color(0xFFF44336)
                        else -> Color(0xFF4CAF50)
                    }
                )
            }
        }
    }
}

@Composable
fun TvRoutingMenu(
    app: AppRule,
    serverGroups: List<ServerGroup>,
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
                TvRoutingOption(
                    label = "Bypass VPN (Direct Internet)",
                    isSelected = app.routedGroupId == null,
                    onClick = { onSelectGroup(null) }
                )

                serverGroups.forEach { group ->
                    TvRoutingOption(
                        label = "Route via ${group.name}",
                        isSelected = app.routedGroupId == group.id,
                        onClick = { onSelectGroup(group.id) }
                    )
                }

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
            .onFocusChanged { isFocused = it.isFocused }
            .semantics { contentDescription = label },
        colors = ButtonDefaults.buttonColors(
            containerColor = when {
                isSelected -> Color(0xFF4CAF50)
                isFocused -> Color(0xFF2196F3)
                else -> Color(0xFF424242)
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
