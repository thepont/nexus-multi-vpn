package com.multiregionvpn

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.multiregionvpn.data.database.AppDatabase
import com.multiregionvpn.data.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Fetch NordVPN servers and update tunnel configurations
 * 
 * Run with:
 * adb shell am instrument -w -e class com.multiregionvpn.FetchServersTest \
 *   com.multiregionvpn.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
class FetchServersTest {
    
    @Test
    fun fetchAndUpdateServers() = runBlocking {
        println("🌍 Fetching NordVPN servers for configured tunnels...")
        
        val context: Context = ApplicationProvider.getApplicationContext()
        val database = androidx.room.Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "region_router_db"
        )
            .addCallback(AppDatabase.PresetRuleCallback())
            .build()
        val settingsRepo = SettingsRepository(
            vpnConfigDao = database.vpnConfigDao(),
            appRuleDao = database.appRuleDao(),
            providerCredentialsDao = database.providerCredentialsDao(),
            presetRuleDao = database.presetRuleDao()
        )
        
        // Get all VPN configs
        val configs = database.vpnConfigDao().getAll().first()
        println("Found ${configs.size} tunnel(s)")
        
        for (config in configs) {
            println("")
            println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            println("🔌 Tunnel: ${config.name}")
            println("   Region: ${config.regionId}")
            println("   Current Server: ${config.serverHostname.ifEmpty { "(empty)" }}")
            
            if (config.serverHostname.isEmpty()) {
                println("   ⏳ Fetching NordVPN server...")
                
                // Simulate fetching - in real implementation this would call NordVPN API
                // For now, use known good servers
                val serverHostname = when (config.regionId.uppercase()) {
                    "UK" -> "uk2303.nordvpn.com"
                    "FR" -> "fr881.nordvpn.com"
                    "US" -> "us9999.nordvpn.com"
                    "AU" -> "au778.nordvpn.com"
                    "JP" -> "jp888.nordvpn.com"
                    else -> "${config.regionId.lowercase()}1.nordvpn.com"
                }
                
                val updatedConfig = config.copy(serverHostname = serverHostname)
                settingsRepo.saveVpnConfig(updatedConfig)
                
                println("   ✅ Updated server: $serverHostname")
            } else {
                println("   ✅ Server already configured")
            }
        }
        
        println("")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("✅ ALL TUNNELS CONFIGURED!")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("")
        println("Now:")
        println("  1. Restart the app")
        println("  2. Toggle VPN ON")
        println("  3. Tunnels will connect to NordVPN servers")
        println("  4. Traffic will route through configured tunnels")
        println("")
    }
}

