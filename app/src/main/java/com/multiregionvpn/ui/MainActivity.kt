package com.multiregionvpn.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.multiregionvpn.ui.navigation.MainScreen
import com.multiregionvpn.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Main Activity for Phone/Tablet - Shows tabbed navigation
 * 
 * Tabs:
 * - Tunnels: VPN tunnel configuration
 * - Apps: Per-app routing rules (searchable, with region badges)
 * - Connections: Connection log
 * - Settings: App settings and provider credentials
 * 
 * For TV, see TvActivity which uses TvMainScreen
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
}


