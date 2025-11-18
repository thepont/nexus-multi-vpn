package com.multiregionvpn.ui.settings.composables

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.Icons.Default
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.multiregionvpn.core.provider.VpnProvider
import com.multiregionvpn.data.database.ProviderAccountEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderAccountSection(
    providerAccounts: List<ProviderAccountEntity>,
    providers: List<VpnProvider>,
    onAddAccount: (String, String, Map<String, String>) -> Unit,
    onDeleteAccount: (String) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedProvider by remember { mutableStateOf<VpnProvider?>(null) }
    
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "VPN Provider Accounts",
                style = MaterialTheme.typography.titleLarge
            )
            Button(
                onClick = { showAddDialog = true }
            ) {
                Text("Add")
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        if (providerAccounts.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    "No provider accounts configured. Tap 'Add' to add one.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(providerAccounts, key = { it.id }) { account ->
                    ProviderAccountCard(
                        account = account,
                        provider = providers.find { it.id == account.providerId },
                        onDelete = { onDeleteAccount(account.id) }
                    )
                }
            }
        }
    }
    
    if (showAddDialog) {
        AddProviderAccountDialog(
            providers = providers,
            onDismiss = { showAddDialog = false },
            onConfirm = { providerId, displayLabel, credentials ->
                onAddAccount(providerId, displayLabel, credentials)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun ProviderAccountCard(
    account: ProviderAccountEntity,
    provider: VpnProvider?,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.displayLabel,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = provider?.displayName ?: account.providerId,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                account.lastAuthState?.let { authState ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Status: $authState",
                        style = MaterialTheme.typography.bodySmall,
                        color = when (authState) {
                            "authenticated" -> MaterialTheme.colorScheme.primary
                            "failed" -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete account"
                )
            }
        }
    }
    
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Account?") },
            text = { Text("Are you sure you want to delete ${account.displayLabel}?") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteConfirm = false
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProviderAccountDialog(
    providers: List<VpnProvider>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Map<String, String>) -> Unit
) {
    var selectedProvider by remember { mutableStateOf<VpnProvider?>(null) }
    var displayLabel by remember { mutableStateOf("") }
    val credentialFields = remember(selectedProvider) {
        selectedProvider?.requiredCredentials() ?: emptyList()
    }
    val credentialValues = remember(credentialFields) {
        val map = mutableStateMapOf<String, String>()
        credentialFields.forEach { field ->
            map[field.key] = ""
        }
        map
    }
    var isExpanded by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Provider Account") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Provider selection
                ExposedDropdownMenuBox(
                    expanded = isExpanded,
                    onExpandedChange = { isExpanded = !isExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedProvider?.displayName ?: "Select Provider",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Provider") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = isExpanded,
                        onDismissRequest = { isExpanded = false }
                    ) {
                        providers.forEach { provider ->
                            DropdownMenuItem(
                                text = { Text(provider.displayName) },
                                onClick = {
                                    selectedProvider = provider
                                    isExpanded = false
                                    if (displayLabel.isEmpty()) {
                                        displayLabel = "${provider.displayName} Account"
                                    }
                                }
                            )
                        }
                    }
                }
                
                // Display label
                OutlinedTextField(
                    value = displayLabel,
                    onValueChange = { displayLabel = it },
                    label = { Text("Account Label") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Credential fields
                if (selectedProvider != null) {
                    credentialFields.forEach { field ->
                        OutlinedTextField(
                            value = credentialValues[field.key] ?: "",
                            onValueChange = { credentialValues[field.key] = it },
                            label = { Text(field.label) },
                            visualTransformation = if (field.isPassword) {
                                PasswordVisualTransformation()
                            } else {
                                VisualTransformation.None
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedProvider != null && displayLabel.isNotBlank()) {
                        val allFieldsFilled = credentialFields.all { field ->
                            (credentialValues[field.key] ?: "").isNotBlank()
                        }
                        if (allFieldsFilled) {
                            onConfirm(
                                selectedProvider!!.id,
                                displayLabel,
                                credentialValues.toMap() as Map<String, String>
                            )
                        }
                    }
                },
                enabled = selectedProvider != null && 
                         displayLabel.isNotBlank() &&
                         credentialFields.all { (credentialValues[it.key] ?: "").isNotBlank() }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

