package com.multiregionvpn.ui.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.multiregionvpn.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * TV Activity - Entry point for Android TV interface
 * 
 * Optimized for:
 * - D-pad navigation
 * - 10-foot UI (large text, clear focus indicators)
 * - Remote control input
 * - Leanback launcher integration
 */
@AndroidEntryPoint
class TvActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TvMainScreen()
                }
            }
        }
    }
}

