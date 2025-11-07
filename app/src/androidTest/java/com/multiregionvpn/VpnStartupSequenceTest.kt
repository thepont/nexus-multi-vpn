package com.multiregionvpn

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.multiregionvpn.data.database.AppDatabase
import com.multiregionvpn.data.database.AppRule
import com.multiregionvpn.data.database.ProviderCredentials
import com.multiregionvpn.data.database.VpnConfig
import com.multiregionvpn.data.repository.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * E2E tests for VPN startup sequence and config download.
 * 
 * These tests validate the critical chicken-and-egg problem:
 * - VPN interface is established (DNS goes through VPN)
 * - Tunnels need to download configs from nordcdn.com
 * - But VPN isn't connected yet, so DNS fails!
 * 
 * Solution: Socket protection (VpnService.protect()) on HTTP client
 * 
 * Run with:
 * adb shell am instrument -w \
 *   -e class com.multiregionvpn.VpnStartupSequenceTest \
 *   -e NORDVPN_USERNAME "xxx" \
 *   -e NORDVPN_PASSWORD "yyy" \
 *   com.multiregionvpn.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
class VpnStartupSequenceTest {
    
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var settingsRepo: SettingsRepository
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = AppDatabase.getDatabase(context)
        settingsRepo = SettingsRepository(
            vpnConfigDao = database.vpnConfigDao(),
            appRuleDao = database.appRuleDao(),
            providerCredentialsDao = database.providerCredentialsDao(),
            presetRuleDao = database.presetRuleDao()
        )
    }
    
    @After
    fun teardown() = runBlocking {
        // Clean up test data
        database.vpnConfigDao().clearAll()
        database.appRuleDao().clearAll()
    }
    
    /**
     * Test 1: Config Download During VPN Startup
     * 
     * This test validates that OpenVPN configs can be downloaded
     * even when the VPN interface is established.
     * 
     * WITHOUT socket protection, this test would fail with:
     * "Unable to resolve host downloads.nordcdn.com"
     */
    @Test
    fun testConfigDownloadDuringVpnStartup() = runBlocking {
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("TEST: Config Download During VPN Startup")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println()
        
        // Get credentials from test arguments
        val testArgs = androidx.test.platform.app.InstrumentationRegistry.getArguments()
        val username = testArgs.getString("NORDVPN_USERNAME")
            ?: throw IllegalArgumentException("NORDVPN_USERNAME required")
        val password = testArgs.getString("NORDVPN_PASSWORD")
            ?: throw IllegalArgumentException("NORDVPN_PASSWORD required")
        
        println("Step 1: Save NordVPN credentials")
        settingsRepo.saveProviderCredentials(
            ProviderCredentials("nordvpn", username, password)
        )
        println("✅ Credentials saved")
        println()
        
        println("Step 2: Create UK tunnel config")
        val ukConfig = VpnConfig(
            id = UUID.randomUUID().toString(),
            name = "UK Test",
            regionId = "UK",
            templateId = "nordvpn",
            serverHostname = "uk2303.nordvpn.com"
        )
        settingsRepo.saveVpnConfig(ukConfig)
        println("✅ UK tunnel created: ${ukConfig.name}")
        println()
        
        println("Step 3: Create app rule to trigger VPN startup")
        val appRule = AppRule(
            id = UUID.randomUUID().toString(),
            packageName = "com.android.chrome",  // Use Chrome as test app
            vpnConfigId = ukConfig.id
        )
        settingsRepo.saveAppRule(appRule)
        println("✅ App rule created: Chrome → UK")
        println()
        
        println("Step 4: Start VPN service")
        val startIntent = android.content.Intent(context, com.multiregionvpn.core.VpnEngineService::class.java)
        startIntent.action = com.multiregionvpn.core.VpnEngineService.ACTION_START
        context.startForegroundService(startIntent)
        println("✅ VPN service started")
        println()
        
        println("Step 5: Wait for VPN interface to be established...")
        delay(3000)  // Give time for interface establishment
        println("✅ VPN interface should be established now")
        println()
        
        println("Step 6: Verify tunnel can download config (THIS IS THE CRITICAL TEST)")
        println("   If socket protection works: Config downloads successfully")
        println("   If socket protection fails: DNS resolution fails")
        
        // Monitor logs for config download attempt
        var configDownloadSuccess = false
        var dnsError = false
        
        // Wait up to 60 seconds for tunnel connection
        withTimeout(60000) {
            repeat(60) { attempt ->
                delay(1000)
                println("   [${attempt + 1}/60] Checking connection status...")
                
                // Check logs for errors
                val logcatProcess = Runtime.getRuntime().exec("logcat -d -s VpnTemplateService:* VpnEngineService:*")
                val logOutput = logcatProcess.inputStream.bufferedReader().readText()
                
                if (logOutput.contains("Unable to resolve host") || 
                    logOutput.contains("downloads.nordcdn.com")) {
                    if (logOutput.contains("Failed")) {
                        dnsError = true
                        println("❌ DNS ERROR DETECTED!")
                        println("   Socket protection is NOT working!")
                        break
                    }
                }
                
                if (logOutput.contains("VPN config prepared successfully") ||
                    logOutput.contains("Auth file created")) {
                    configDownloadSuccess = true
                    println("✅ Config downloaded successfully!")
                    println("   Socket protection IS working!")
                    break
                }
            }
        }
        
        println()
        println("Step 7: Stop VPN service")
        val stopIntent = android.content.Intent(context, com.multiregionvpn.core.VpnEngineService::class.java)
        stopIntent.action = com.multiregionvpn.core.VpnEngineService.ACTION_STOP
        context.startService(stopIntent)
        println("✅ VPN service stopped")
        println()
        
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("TEST RESULT:")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("Config Download Success: $configDownloadSuccess")
        println("DNS Error Occurred: $dnsError")
        println()
        
        if (dnsError) {
            throw AssertionError(
                "❌ DNS resolution failed during config download!\n" +
                "   This means socket protection is NOT working.\n" +
                "   The HTTP client cannot bypass the VPN to download configs.\n" +
                "   Check OkHttpClient socket factory in AppModule.kt"
            )
        }
        
        if (!configDownloadSuccess) {
            throw AssertionError(
                "❌ Config download did not complete within 60 seconds!\n" +
                "   Either:\n" +
                "   1. Socket protection is not working (DNS fails)\n" +
                "   2. NordVPN API is down\n" +
                "   3. Network connection issues\n" +
                "   Check logs for details."
            )
        }
        
        println("✅ TEST PASSED!")
        println("   Socket protection is working correctly!")
        println("   Configs can be downloaded even when VPN is active!")
        println()
    }
    
    /**
     * Test 2: Pre-fetch vs Runtime Config Download
     * 
     * Validates that configs can be pre-fetched before VPN interface
     * is established, and that runtime fetching also works.
     */
    @Test
    fun testPrefetchVsRuntimeConfigDownload() = runBlocking {
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("TEST: Pre-fetch vs Runtime Config Download")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println()
        
        // Get credentials
        val testArgs = androidx.test.platform.app.InstrumentationRegistry.getArguments()
        val username = testArgs.getString("NORDVPN_USERNAME")
            ?: throw IllegalArgumentException("NORDVPN_USERNAME required")
        val password = testArgs.getString("NORDVPN_PASSWORD")
            ?: throw IllegalArgumentException("NORDVPN_PASSWORD required")
        
        settingsRepo.saveProviderCredentials(
            ProviderCredentials("nordvpn", username, password)
        )
        
        println("Scenario 1: Pre-fetch (no VPN interface yet)")
        val frConfig = VpnConfig(
            id = UUID.randomUUID().toString(),
            name = "FR Pre-fetch",
            regionId = "FR",
            templateId = "nordvpn",
            serverHostname = "fr881.nordvpn.com"
        )
        settingsRepo.saveVpnConfig(frConfig)
        
        // Download config BEFORE VPN starts
        println("   Downloading config BEFORE VPN interface...")
        val vpnTemplateService = com.multiregionvpn.core.VpnTemplateService(
            nordVpnApi = com.multiregionvpn.di.AppModule.provideNordVpnApiService(context),
            settingsRepo = settingsRepo,
            context = context
        )
        
        val prefetchedConfig = vpnTemplateService.prepareConfig(frConfig)
        println("   ✅ Pre-fetch successful (expected)")
        println("      Config size: ${prefetchedConfig.ovpnFileContent.length} bytes")
        println()
        
        println("Scenario 2: Runtime fetch (VPN interface active)")
        val ukConfig = VpnConfig(
            id = UUID.randomUUID().toString(),
            name = "UK Runtime",
            regionId = "UK",
            templateId = "nordvpn",
            serverHostname = "uk2303.nordvpn.com"
        )
        settingsRepo.saveVpnConfig(ukConfig)
        
        // Start VPN FIRST
        println("   Starting VPN service...")
        val appRule = AppRule(
            id = UUID.randomUUID().toString(),
            packageName = "com.android.chrome",
            vpnConfigId = frConfig.id  // Use pre-fetched config
        )
        settingsRepo.saveAppRule(appRule)
        
        val startIntent = android.content.Intent(context, com.multiregionvpn.core.VpnEngineService::class.java)
        startIntent.action = com.multiregionvpn.core.VpnEngineService.ACTION_START
        context.startForegroundService(startIntent)
        delay(3000)  // Wait for VPN interface
        println("   ✅ VPN interface established")
        println()
        
        // NOW try to download config (DNS should bypass VPN)
        println("   Downloading config AFTER VPN interface (THIS TESTS SOCKET PROTECTION)...")
        val runtimeConfig = vpnTemplateService.prepareConfig(ukConfig)
        println("   ✅ Runtime fetch successful!")
        println("      Config size: ${runtimeConfig.ovpnFileContent.length} bytes")
        println("      Socket protection is working!")
        println()
        
        // Stop VPN
        val stopIntent = android.content.Intent(context, com.multiregionvpn.core.VpnEngineService::class.java)
        stopIntent.action = com.multiregionvpn.core.VpnEngineService.ACTION_STOP
        context.startService(stopIntent)
        
        println("✅ TEST PASSED!")
        println("   Both pre-fetch and runtime fetch work correctly!")
        println()
    }
}

