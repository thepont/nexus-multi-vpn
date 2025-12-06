package com.multiregionvpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.test.uiautomator.By
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
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltAndroidRule
import org.junit.Rule
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.multiregionvpn.diagnostic.DiagnosticHttpProbeService
import javax.inject.Inject


/**
 * Diagnostic test to understand WHY HTTP requests bypass VPN
 * even when test package is in allowed apps and tunnel is connected.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class DiagnosticRoutingTest {
    
    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    private lateinit var appContext: Context
    private lateinit var device: UiDevice
    @Inject
    lateinit var settingsRepo: SettingsRepository
    
    @Before
    fun setup() {
        println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("🔬 DIAGNOSTIC TEST SETUP")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        
        hiltRule.inject()
        appContext = ApplicationProvider.getApplicationContext()
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        
        // Pre-approve VPN permission using appops (App Operations)
        try {
            val appopsCommand = "appops set ${appContext.packageName} ACTIVATE_VPN allow"
            device.executeShellCommand(appopsCommand)
            println("✅ VPN permission pre-approved via appops")
        } catch (e: Exception) {
            println("⚠️  Could not pre-approve VPN permission via appops: ${e.message}")
        }
        
        runBlocking {
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
        }
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }
    
    @After
    fun teardown() = runBlocking {
        // CRITICAL: Always reset TestGlobalModeOverride to prevent test pollution
        try {
            VpnEngineService.setTestGlobalModeOverride(null)
        } catch (e: Exception) {
            println("⚠️  Could not reset TestGlobalModeOverride: ${e.message}")
        }
        
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
        
        val testPackage = InstrumentationRegistry.getInstrumentation().targetContext.packageName
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
        // Check if VPN permission is granted
        val permissionIntent = android.net.VpnService.prepare(appContext)
        if (permissionIntent != null) {
            println("⚠️  VPN permission not granted - handling dialog...")
            val startIntent = Intent(appContext, VpnEngineService::class.java).apply {
                action = VpnEngineService.ACTION_START
            }
            appContext.startForegroundService(startIntent)
            delay(1000) // Give dialog time to appear
            handleVpnPermissionDialog()
        } else {
            val startIntent = Intent(appContext, VpnEngineService::class.java).apply {
                action = VpnEngineService.ACTION_START
            }
            appContext.startForegroundService(startIntent)
        }
        
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
        
        // Step 6: Make simple HTTP request
        println("\n6️⃣ Making HTTP request from app process...")
        println("   URL: http://ip-api.com/json")
        println("   Expected: This request should go through VPN → UK servers")
        println("   Method: DiagnosticHttpProbeService (runs in app UID)")

        val latch = CountDownLatch(1)
        var probeSuccess = false
        var probeResponseBody: String? = null
        var probeError: String? = null

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent) {
                probeSuccess = intent.getBooleanExtra(DiagnosticHttpProbeService.EXTRA_SUCCESS, false)
                probeResponseBody = intent.getStringExtra(DiagnosticHttpProbeService.EXTRA_RESPONSE_BODY)
                probeError = intent.getStringExtra(DiagnosticHttpProbeService.EXTRA_ERROR_MESSAGE)
                latch.countDown()
            }
        }

        val broadcastManager = LocalBroadcastManager.getInstance(appContext)
        broadcastManager.registerReceiver(
            receiver,
            IntentFilter(DiagnosticHttpProbeService.ACTION_PROBE_RESULT)
        )

        try {
            val serviceIntent = Intent(appContext, DiagnosticHttpProbeService::class.java).apply {
                putExtra(DiagnosticHttpProbeService.EXTRA_URL, "http://ip-api.com/json")
            }
            val started = appContext.startService(serviceIntent)
            assert(started != null) { "Failed to start DiagnosticHttpProbeService" }

            val completed = latch.await(30, TimeUnit.SECONDS)
            assert(completed) { "Diagnostic HTTP probe timed out" }
            assert(probeSuccess) { "HTTP probe failed: $probeError" }

            val response = probeResponseBody ?: error("HTTP probe returned empty body")
            println("   Response: ${response.take(200)}...")

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
        } finally {
            broadcastManager.unregisterReceiver(receiver)
        }
    }
    
    private fun handleVpnPermissionDialog() = runBlocking {
        println("   Waiting for VPN permission dialog...")
        
        // Wait for the VPN permission dialog to appear
        var allowButton = device.wait(
            Until.findObject(By.text("OK")),
            3000
        )
        
        if (allowButton == null) {
            allowButton = device.wait(
                Until.findObject(By.text("Allow")),
                3000
            )
        }
        
        if (allowButton == null) {
            // Try finding by resource ID (standard Android button IDs)
            allowButton = device.wait(
                Until.findObject(By.res("android:id/button1")), // Usually "OK" or positive button
                3000
            )
        }
        
        if (allowButton != null) {
            println("   Found VPN permission dialog button, clicking...")
            allowButton.click()
            delay(2000) // Wait for permission to be granted
            println("   ✅ VPN permission dialog handled")
        } else {
            println("   No VPN permission dialog found (may already be granted)")
        }
    }
}

