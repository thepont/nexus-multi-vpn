package com.multiregionvpn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.multiregionvpn.ui.navigation.MainScreen
import com.multiregionvpn.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Main Activity - Professional VPN Router UI (NOC Style)
 * 
 * Features:
 * - Persistent header bar with real-time status
 * - Tab navigation (Tunnels, Apps, Connections, Settings)
 * - Professional design with Material 3
 * - Performance-optimized connection logging
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

