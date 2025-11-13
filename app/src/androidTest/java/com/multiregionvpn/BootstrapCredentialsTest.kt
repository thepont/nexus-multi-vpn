package com.multiregionvpn

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.multiregionvpn.data.database.AppDatabase
import com.multiregionvpn.data.database.ProviderCredentials
import com.multiregionvpn.data.database.VpnConfig
import com.multiregionvpn.data.repository.SettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Bootstrap test to set up NordVPN credentials and sample tunnels
 * 
 * CREDENTIALS MUST BE PASSED AS TEST ARGUMENTS (never hardcoded!)
 * 
 * Run with:
 * adb shell am instrument -w \
 *   -e class com.multiregionvpn.BootstrapCredentialsTest \
 *   -e NORDVPN_USERNAME "your_username" \
 *   -e NORDVPN_PASSWORD "your_password" \
 *   com.multiregionvpn.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
class BootstrapCredentialsTest {
    
    @Test
    fun bootstrapNordVpnCredentials() = runBlocking {
        println("🔐 Bootstrapping NordVPN credentials and sample tunnels...")
        
        // Get credentials from test arguments (NEVER hardcode!)
        val testArgs = androidx.test.platform.app.InstrumentationRegistry.getArguments()
        val username = testArgs.getString("NORDVPN_USERNAME")
            ?: throw IllegalArgumentException("NORDVPN_USERNAME must be passed via test arguments")
        val password = testArgs.getString("NORDVPN_PASSWORD")
            ?: throw IllegalArgumentException("NORDVPN_PASSWORD must be passed via test arguments")
        
        val context: Context = ApplicationProvider.getApplicationContext()
        val database = AppDatabase.getDatabase(context)
        val settingsRepo = SettingsRepository(
            vpnConfigDao = database.vpnConfigDao(),
            appRuleDao = database.appRuleDao(),
            providerCredentialsDao = database.providerCredentialsDao(),
            presetRuleDao = database.presetRuleDao()
        )
        
        // Save NordVPN credentials
        val nordCreds = ProviderCredentials(
            templateId = "nordvpn",
            username = username,
            password = password
        )
        settingsRepo.saveProviderCredentials(nordCreds)
        println("✅ Saved NordVPN credentials")
        
        // Create sample UK tunnel
        val ukConfig = VpnConfig(
            id = UUID.randomUUID().toString(),
            name = "UK - BBC",
            regionId = "UK",
            templateId = "nordvpn",
            serverHostname = "" // Will be fetched from NordVPN API
        )
        settingsRepo.saveVpnConfig(ukConfig)
        println("✅ Created UK tunnel: ${ukConfig.name}")
        
        // Create sample FR tunnel
        val frConfig = VpnConfig(
            id = UUID.randomUUID().toString(),
            name = "France - Streaming",
            regionId = "FR",
            templateId = "nordvpn",
            serverHostname = "" // Will be fetched from NordVPN API
        )
        settingsRepo.saveVpnConfig(frConfig)
        println("✅ Created FR tunnel: ${frConfig.name}")
        
        println("")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("✅ BOOTSTRAP COMPLETE!")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("")
        println("Configured:")
        println("  ✅ NordVPN credentials")
        println("  ✅ UK tunnel: ${ukConfig.name}")
        println("  ✅ FR tunnel: ${frConfig.name}")
        println("")
        println("Now you can:")
        println("  1. Go to Apps tab")
        println("  2. See BBC iPlayer with UK suggestion")
        println("  3. Configure apps to use tunnels")
        println("  4. Toggle VPN ON in header")
        println("")
    }
}

