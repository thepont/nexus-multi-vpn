package com.multiregionvpn

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.multiregionvpn.core.VpnEngineService
import com.multiregionvpn.data.database.AppDatabase
import com.multiregionvpn.data.database.AppRule
import com.multiregionvpn.data.database.ProviderCredentials
import com.multiregionvpn.data.database.VpnConfig
import com.multiregionvpn.data.repository.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

/**
 * Diagnostic test to understand WHY HTTP requests bypass VPN
 * even when test package is in allowed apps and tunnel is connected.
 */
@RunWith(AndroidJUnit4::class)
class DiagnosticRoutingTest {
    
    private lateinit var appContext: Context
    private lateinit var device: UiDevice
    private lateinit var settingsRepo: SettingsRepository
    
    @Before
    fun setup() = runBlocking {
        println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("🔬 DIAGNOSTIC TEST SETUP")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        
        appContext = ApplicationProvider.getApplicationContext()
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        
        val database = AppDatabase.getDatabase(appContext)
        settingsRepo = SettingsRepository(
            database.vpnConfigDao(),
            database.appRuleDao(),
            database.providerCredentialsDao(),
            database.presetRuleDao()
        )
        
        // Stop VPN
        println("1️⃣ Stopping VPN if running...")
        val stopIntent = Intent(appContext, VpnEngineService::class.java).apply {
            action = VpnEngineService.ACTION_STOP
        }
        appContext.startService(stopIntent)
        delay(2000)
        println("✅ VPN stopped")
        
        // Clear data
        println("2️⃣ Clearing test data...")
        settingsRepo.clearAllAppRules()
        settingsRepo.clearAllVpnConfigs()
        println("✅ Test data cleared")
        
        // Save credentials
        println("3️⃣ Saving NordVPN credentials...")
        val testArgs = InstrumentationRegistry.getArguments()
        val username = testArgs.getString("NORDVPN_USERNAME") ?: throw IllegalArgumentException("Need NORDVPN_USERNAME")
        val password = testArgs.getString("NORDVPN_PASSWORD") ?: throw IllegalArgumentException("Need NORDVPN_PASSWORD")
        settingsRepo.saveProviderCredentials(ProviderCredentials("nordvpn", username, password))
        println("✅ Credentials saved")
        
        // Save UK config
        println("4️⃣ Creating UK tunnel config...")
        val ukConfig = VpnConfig(
            id = "test-uk-diag",
            name = "UK Diagnostic",
            regionId = "UK",
            templateId = "nordvpn",
            serverHostname = "uk2303.nordvpn.com"
        )
        settingsRepo.saveVpnConfig(ukConfig)
        println("✅ UK tunnel config saved")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }
    
    @After
    fun teardown() = runBlocking {
        println("\n🧹 Teardown: Stopping VPN...")
        val stopIntent = Intent(appContext, VpnEngineService::class.java).apply {
            action = VpnEngineService.ACTION_STOP
        }
        appContext.startService(stopIntent)
        delay(2000)
        println("✅ VPN stopped")
    }
    
    /**
     * Test Scenario: Add app rule BEFORE starting VPN
     * This is the cleanest case - rule exists before VPN reads it
     */
    @Test
    fun test_diagnostic_ruleBeforeVpn() = runBlocking {
        println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("🧪 DIAGNOSTIC: App Rule Created BEFORE VPN Start")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        
        val testPackage = InstrumentationRegistry.getInstrumentation().context.packageName
        println("Test package: $testPackage")
        println("Test UID: ${android.os.Process.myUid()}")
        
        // Step 1: Create app rule FIRST
        println("\n1️⃣ Creating app rule BEFORE VPN starts...")
        settingsRepo.createAppRule(testPackage, "test-uk-diag")
        delay(1000)  // Let DB commit
        
        // Verify saved
        val saved = settingsRepo.getAppRuleByPackageName(testPackage)
        println("✅ Rule saved: ${saved?.packageName} → ${saved?.vpnConfigId}")
        assert(saved != null) { "Rule not saved!" }
        
        // Step 2: Start VPN
        println("\n2️⃣ Starting VPN (should read 1 app rule)...")
        val startIntent = Intent(appContext, VpnEngineService::class.java).apply {
            action = VpnEngineService.ACTION_START
        }
        appContext.startForegroundService(startIntent)
        
        // Step 3: Wait for VPN to fully start and read rules
        println("\n3️⃣ Waiting 10 seconds for VPN to start and read app rules...")
        delay(10000)
        
        // Step 4: Verify tunnel connected
        println("\n4️⃣ Checking tunnel status...")
        try {
            val connectionManager = com.multiregionvpn.core.VpnConnectionManager.getInstance()
            val tunnelId = "nordvpn_UK"
            
            var attempts = 0
            while (attempts < 60) {
                if (connectionManager.isTunnelReadyForRouting(tunnelId)) {
                    println("✅ Tunnel $tunnelId is ready!")
                    break
                }
                println("   ⏳ Waiting for tunnel... (${attempts}s)")
                delay(1000)
                attempts++
            }
            
            if (attempts >= 60) {
                throw AssertionError("Tunnel didn't connect!")
            }
        } catch (e: Exception) {
            println("❌ Error checking tunnel: ${e.message}")
            throw e
        }
        
        // Step 5: Additional stabilization
        println("\n5️⃣ Waiting 5 seconds for routing to stabilize...")
        delay(5000)
        
        // Step 6: Make simple HTTP request securely
        println("\n6️⃣ Making HTTP request securely...")
        println("   URL: https://free.freeipapi.com/api/json")
        println("   Expected: This request should go through VPN → UK servers")
        println("   Method: Direct HttpURLConnection (no Retrofit)")
        
        val url = URL("https://free.freeipapi.com/api/json")
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        
        try {
            connection.connect()
            val responseCode = connection.responseCode
            println("   Response code: $responseCode")
            
            if (responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                println("   Response: ${response.take(200)}...")
                
                // Parse country
                val countryMatch = Regex(""""countryCode":"([A-Z]{2})"""").find(response)
                val country = countryMatch?.groupValues?.get(1) ?: "UNKNOWN"
                
                println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                println("📍 RESULT:")
                println("   Country: $country")
                println("   Expected: GB")
                println("   Match: ${country == "GB"}")
                println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                
                assert(country == "GB") { "Expected GB, got $country" }
                println("✅ TEST PASSED!")
            } else {
                throw AssertionError("HTTP request failed: $responseCode")
            }
        } finally {
            connection.disconnect()
        }
    }
}

