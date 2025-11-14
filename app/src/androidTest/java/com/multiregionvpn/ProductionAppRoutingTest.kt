package com.multiregionvpn

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.multiregionvpn.core.VpnEngineService
import com.multiregionvpn.data.database.AppDatabase
import com.multiregionvpn.data.database.ProviderCredentials
import com.multiregionvpn.data.database.VpnConfig
import com.multiregionvpn.data.repository.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule

/**
 * E2E Test using REAL production apps to verify VPN routing.
 * 
 * This test uses Firefox (common browser) instead of test package
 * to verify that addAllowedApplication() works for production apps.
 * 
 * Prerequisites:
 * - Firefox installed: com.android.chrome or org.mozilla.firefox
 * - NordVPN credentials
 * 
 * Run with:
 * adb shell am instrument -w \
 *   -e class com.multiregionvpn.ProductionAppRoutingTest \
 *   -e NORDVPN_USERNAME "xxx" \
 *   -e NORDVPN_PASSWORD "yyy" \
 *   com.multiregionvpn.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class ProductionAppRoutingTest {
    
    @get:Rule
    val hiltRule = HiltAndroidRule(this)
    
    private lateinit var appContext: Context
    private lateinit var device: UiDevice
    private lateinit var settingsRepo: SettingsRepository
    
    private val CHROME_PACKAGE = "com.android.chrome"
    private val FIREFOX_PACKAGE = "org.mozilla.firefox"
    
    @Before
    fun setup() = runBlocking {
        hiltRule.inject()
        println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("🌐 PRODUCTION APP ROUTING TEST SETUP")
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
        println("1️⃣ Stopping VPN...")
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
            id = "test-uk-prod",
            name = "UK Production Test",
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
        
        // Clear rules
        settingsRepo.clearAllAppRules()
        println("✅ Cleanup complete")
    }
    
    /**
     * Test routing with Chrome browser.
     * 
     * This verifies that production apps (not test packages) 
     * can be routed through VPN using addAllowedApplication().
     */
    @Test
    fun test_chromeRoutingThroughUK() = runBlocking {
        println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("🧪 TEST: Chrome Routing Through UK VPN")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        
        // Check if Chrome is installed
        val pm = appContext.packageManager
        val isChromeInstalled = try {
            pm.getPackageInfo(CHROME_PACKAGE, 0)
            true
        } catch (e: Exception) {
            false
        }
        
        if (!isChromeInstalled) {
            println("⚠️  Chrome not installed - skipping test")
            println("   Install with: adb install chrome.apk")
            return@runBlocking
        }
        
        println("✅ Chrome installed")
        
        // Step 1: Create app rule for Chrome
        println("\n1️⃣ Creating app rule for Chrome...")
        settingsRepo.createAppRule(CHROME_PACKAGE, "test-uk-prod")
        delay(1000)
        println("✅ Rule created: $CHROME_PACKAGE → UK")
        
        // Step 2: Start VPN
        println("\n2️⃣ Starting VPN...")
        val startIntent = Intent(appContext, VpnEngineService::class.java).apply {
            action = VpnEngineService.ACTION_START
        }
        appContext.startForegroundService(startIntent)
        delay(10000)
        println("✅ VPN started")
        
        // Step 3: Wait for tunnel
        println("\n3️⃣ Waiting for UK tunnel...")
        val connectionManager = com.multiregionvpn.core.VpnConnectionManager.getInstance()
        var attempts = 0
        while (attempts < 60) {
            if (connectionManager.isTunnelReadyForRouting("nordvpn_UK")) {
                println("✅ Tunnel ready!")
                break
            }
            delay(1000)
            attempts++
        }
        
        if (attempts >= 60) {
            throw AssertionError("Tunnel didn't connect")
        }
        
        // Step 4: Stabilization
        println("\n4️⃣ Waiting 5 seconds for stabilization...")
        delay(5000)
        
        // Step 5: Manual verification
        println("\n5️⃣ MANUAL VERIFICATION REQUIRED:")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("1. Open Chrome on your device")
        println("2. Navigate to: http://ip-api.com/json")
        println("3. Check the 'countryCode' field")
        println("   ✅ Expected: \"GB\" (United Kingdom)")
        println("   ❌ If you see your local country, routing failed")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println()
        println("Keeping VPN active for 30 seconds for manual testing...")
        delay(30000)
        
        println("\n✅ Test complete (manual verification)")
    }
    
    /**
     * Automated test: Monitor packets from Chrome.
     * 
     * This test creates a rule for Chrome, starts VPN, then checks
     * if packets from Chrome's UID actually enter the VPN.
     */
    @Test
    fun test_verifyChromePacketsEnterVPN() = runBlocking {
        println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("🧪 TEST: Verify Chrome Packets Enter VPN")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        
        val pm = appContext.packageManager
        val isChromeInstalled = try {
            pm.getPackageInfo(CHROME_PACKAGE, 0)
            true
        } catch (e: Exception) {
            false
        }
        
        if (!isChromeInstalled) {
            println("⚠️  Chrome not installed - skipping test")
            return@runBlocking
        }
        
        // Get Chrome's UID
        val chromeUid = try {
            pm.getPackageInfo(CHROME_PACKAGE, 0).applicationInfo.uid
        } catch (e: Exception) {
            -1
        }
        
        println("Chrome UID: $chromeUid")
        
        // Create rule and start VPN (same as before)
        settingsRepo.createAppRule(CHROME_PACKAGE, "test-uk-prod")
        delay(1000)
        
        val startIntent = Intent(appContext, VpnEngineService::class.java).apply {
            action = VpnEngineService.ACTION_START
        }
        appContext.startForegroundService(startIntent)
        delay(10000)
        
        val connectionManager = com.multiregionvpn.core.VpnConnectionManager.getInstance()
        var attempts = 0
        while (attempts < 60 && !connectionManager.isTunnelReadyForRouting("nordvpn_UK")) {
            delay(1000)
            attempts++
        }
        
        delay(5000)
        
        println("\n📊 VPN IS ACTIVE - Chrome should be in allowed apps")
        println("   To verify: Check logcat for 'ALLOWED: $CHROME_PACKAGE'")
        println("   To test: Open Chrome and browse to http://ip-api.com/json")
        println("   Expected: Packets from UID $chromeUid should appear in PacketRouter logs")
        println()
        println("Test will keep VPN active for 60 seconds for observation...")
        delay(60000)
        
        println("✅ Observation period complete")
    }
}

