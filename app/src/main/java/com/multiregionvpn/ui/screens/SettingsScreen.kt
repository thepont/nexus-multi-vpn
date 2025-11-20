package com.multiregionvpn.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.multiregionvpn.ui.shared.RouterViewModel
import com.multiregionvpn.ui.settings.composables.ProviderCredentialsSection

/**
 * Settings Screen - App Configuration
 * 
 * Provider credentials and app-wide settings.
 */
@Composable
fun SettingsScreen(
    viewModel: RouterViewModel
) {
    val providerCredentials by viewModel.providerCredentials.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ProviderCredentialsSection(
            credentials = providerCredentials,
            onSaveCredentials = { username, password ->
                viewModel.saveProviderCredentials(username, password)
            }
        )
        
        // TODO: Add logging settings
        // - Enable Connection Log toggle
        // - Enable Full Debug Log toggle
    }
}
