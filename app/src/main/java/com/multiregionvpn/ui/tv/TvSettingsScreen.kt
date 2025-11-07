package com.multiregionvpn.ui.tv

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * TV Settings Screen - Configuration and preferences
 * 
 * TODO: Implement settings UI
 * - Provider credentials
 * - Add/remove tunnels
 * - Advanced options
 */
@Composable
fun TvSettingsScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Settings",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Text(
                text = "Coming soon...",
                fontSize = 20.sp,
                color = Color(0xFF9E9E9E)
            )
            
            Text(
                text = "Use mobile interface to configure provider credentials and add tunnels",
                fontSize = 16.sp,
                color = Color(0xFF757575),
                modifier = Modifier.padding(horizontal = 48.dp)
            )
        }
    }
}

