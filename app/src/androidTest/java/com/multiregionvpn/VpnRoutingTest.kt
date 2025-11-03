package com.multiregionvpn

import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.multiregionvpn.core.VpnEngineService
import com.multiregionvpn.data.database.ProviderCredentials
import com.multiregionvpn.data.database.VpnConfig
import com.multiregionvpn.data.repository.SettingsRepository
import com.multiregionvpn.ui.MainActivity
import com.multiregionvpn.data.database.AppDatabase
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Comprehensive E2E test suite that validates VPN routing functionality.
 * Tests three scenarios:
 * 1. Routing to UK VPN
 * 2. Routing to FR VPN  
 * 3. Routing to Direct Internet (no rule)
 */
@RunWith(AndroidJUnit4::class)
class VpnRoutingTest {

    private lateinit var settingsRepo: SettingsRepository

    private lateinit var device: UiDevice
    private lateinit var appContext: Context
    private lateinit var testContext: Context
    private lateinit var defaultCountry: String

    // --- Test Configuration ---
    private val TEST_PACKAGE_NAME = "com.multiregionvpn.test" // Test runner package
    private val UK_VPN_ID = "test-uk-${UUID.randomUUID().toString().take(8)}"
    private val FR_VPN_ID = "test-fr-${UUID.randomUUID().toString().take(8)}"
    
    // Use known NordVPN server hostnames (these are public and don't require auth)
    private val UK_SERVER_HOSTNAME = "uk1234.nordvpn.com" // Example UK server
    private val FR_SERVER_HOSTNAME = "fr1234.nordvpn.com" // Example FR server

    @Before
    fun setup() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        device = UiDevice.getInstance(instrumentation)
        appContext = instrumentation.targetContext
        testContext = instrumentation.context

        // Note: App data should be cleared before running tests via:
        // adb shell pm clear com.multiregionvpn

        // Initialize repository manually (without Hilt for E2E tests)
        val database = AppDatabase.getDatabase(appContext)
        settingsRepo = SettingsRepository(
            database.vpnConfigDao(),
            database.appRuleDao(),
            database.providerCredentialsDao(),
            database.presetRuleDao()
        )

        // 1. Clear all old test data
        settingsRepo.clearAllAppRules()
        settingsRepo.clearAllVpnConfigs()

        // 2. Get our baseline IP (Direct Internet) - make call BEFORE VPN starts
        val defaultIpInfo = IpCheckService.api.getIpInfo()
        defaultCountry = defaultIpInfo.normalizedCountryCode ?: "US"
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("📍 Baseline Country (Direct IP): $defaultCountry")
        println("📍 Baseline IP: ${defaultIpInfo.normalizedIpAddress}")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        // 3. Bootstrap the app with credentials and VPN configs
        bootstrapVpnCredentials()
        bootstrapVpnConfigs()
    }

    /**
     * Loads NordVPN credentials from environment variables and saves them to the database.
     * Credentials are passed via test instrumentation arguments.
     */
    private suspend fun bootstrapVpnCredentials() {
        try {
            // Read credentials from instrumentation arguments passed via adb -e flags
            // androidx.test.platform.app.InstrumentationRegistry provides getArguments()
            val bundle = InstrumentationRegistry.getArguments()
            
            val username = bundle.getString("NORDVPN_USERNAME")
            val password = bundle.getString("NORDVPN_PASSWORD")
            
            if (username.isNullOrBlank() || password.isNullOrBlank()) {
                throw Exception(
                    "NORDVPN_USERNAME and NORDVPN_PASSWORD must be passed via test arguments.\n" +
                    "Use: scripts/run-e2e-tests.sh"
                )
            }
            
            val providerCreds = ProviderCredentials(
                templateId = "nordvpn",
                username = username,
                password = password
            )
            settingsRepo.saveProviderCredentials(providerCreds)
            println("✓ NordVPN credentials loaded from environment variables and saved")
        } catch (e: Exception) {
            println("⚠️  Warning: Could not load credentials: ${e.message}")
            println("   Test will continue but VPN connections may fail")
            throw e // Re-throw to fail fast if credentials are missing
        }
    }

    /**
     * Creates test VPN configurations for UK and FR.
     * Uses known server hostnames that are publicly available.
     */
    private suspend fun bootstrapVpnConfigs() {
        println("📡 Setting up VPN configurations...")
        
        // Create UK VPN config
        val ukConfig = VpnConfig(
            id = UK_VPN_ID,
            name = "Test UK Server",
            regionId = "UK",
            templateId = "nordvpn",
            serverHostname = UK_SERVER_HOSTNAME
        )
        settingsRepo.saveVpnConfig(ukConfig)
        println("✓ Saved UK Config: $UK_SERVER_HOSTNAME")

        // Create FR VPN config
        val frConfig = VpnConfig(
            id = FR_VPN_ID,
            name = "Test FR Server",
            regionId = "FR",
            templateId = "nordvpn",
            serverHostname = FR_SERVER_HOSTNAME
        )
        settingsRepo.saveVpnConfig(frConfig)
        println("✓ Saved FR Config: $FR_SERVER_HOSTNAME")
    }

    // --- THE THREE MAIN TESTS ---

    @Test
    fun test_routesToUK() = runBlocking {
        println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("🧪 TEST: Routing to UK VPN")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        
        // GIVEN: A rule routing our test package to the UK VPN
        settingsRepo.createAppRule(TEST_PACKAGE_NAME, UK_VPN_ID)
        println("✓ Created app rule: $TEST_PACKAGE_NAME -> UK VPN")

        // WHEN: The VPN service is started
        startVpnEngine()

        // Verify tunnel connection before checking IP
        val tunnelId = "nordvpn_UK"
        verifyTunnelConnected(tunnelId, timeoutMs = 60000)
        
        // THEN: Our IP should be in the UK
        delay(5000) // Wait for routing to stabilize
        val vpnIpInfo = IpCheckService.api.getIpInfo()
        println("📍 Resulting IP: ${vpnIpInfo.normalizedIpAddress}")
        println("📍 Resulting Country: ${vpnIpInfo.normalizedCountryCode}")
        println("📍 Resulting City: ${vpnIpInfo.city}")
        
        assertEquals(
            "❌ Traffic was not routed to the UK! Expected GB, got ${vpnIpInfo.normalizedCountryCode}",
            "GB",
            vpnIpInfo.normalizedCountryCode
        )
        println("✅ TEST PASSED: Traffic successfully routed to UK")
    }

    @Test
    fun test_routesToFrance() = runBlocking {
        println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("🧪 TEST: Routing to France VPN")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        
        // GIVEN: A rule routing our test package to France VPN
        settingsRepo.createAppRule(TEST_PACKAGE_NAME, FR_VPN_ID)
        println("✓ Created app rule: $TEST_PACKAGE_NAME -> FR VPN")

        // WHEN: The VPN service is started
        startVpnEngine()

        // Verify tunnel connection before checking IP
        val tunnelId = "nordvpn_FR"
        verifyTunnelConnected(tunnelId, timeoutMs = 60000)
        
        // THEN: Our IP should be in France
        delay(5000) // Wait for routing to stabilize
        val vpnIpInfo = IpCheckService.api.getIpInfo()
        println("📍 Resulting IP: ${vpnIpInfo.normalizedIpAddress}")
        println("📍 Resulting Country: ${vpnIpInfo.normalizedCountryCode}")
        println("📍 Resulting City: ${vpnIpInfo.city}")
        
        assertEquals(
            "❌ Traffic was not routed to France! Expected FR, got ${vpnIpInfo.normalizedCountryCode}",
            "FR",
            vpnIpInfo.normalizedCountryCode
        )
        println("✅ TEST PASSED: Traffic successfully routed to France")
    }

    @Test
    fun test_routesToDirectInternet() = runBlocking {
        println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("🧪 TEST: Routing to Direct Internet (no rule)")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        
        // GIVEN: No rule exists for our test package (cleared in @Before)
        // No app rule is created - should route to direct internet

        // WHEN: The VPN service is started (it will find no rule)
        startVpnEngine()

        // THEN: Our IP should be the default, non-VPN IP
        delay(5000) // Wait a bit for any routing to stabilize
        val vpnIpInfo = IpCheckService.api.getIpInfo()
        println("📍 Resulting IP: ${vpnIpInfo.normalizedIpAddress}")
        println("📍 Resulting Country: ${vpnIpInfo.normalizedCountryCode}")
        println("📍 Baseline Country: $defaultCountry")
        
        assertEquals(
            "❌ Traffic was not routed directly! Expected $defaultCountry, got ${vpnIpInfo.normalizedCountryCode}",
            defaultCountry,
            vpnIpInfo.normalizedCountryCode
        )
        assertNotEquals(
            "❌ Traffic was mistakenly routed to UK!",
            "GB",
            vpnIpInfo.normalizedCountryCode
        )
        assertNotEquals(
            "❌ Traffic was mistakenly routed to FR!",
            "FR",
            vpnIpInfo.normalizedCountryCode
        )
        println("✅ TEST PASSED: Traffic successfully routed to Direct Internet")
    }

    // --- HELPER FUNCTIONS ---

    /**
     * Launches the MainActivity manually to ensure Hilt test application is used.
     */
    private fun launchMainActivity() {
        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        appContext.startActivity(intent)
        // Wait for activity to launch and UI to render
        Thread.sleep(3000)
        
        // Also wait for the window to be ready
        device.wait(Until.hasObject(By.pkg(appContext.packageName).depth(0)), 5000)
    }
    
    /**
     * Starts the VPN engine by clicking the toggle in the UI and handling system dialogs.
     * Uses improved permission handling logic from VpnToggleTest.
     */
    private fun startVpnEngine() = runBlocking {
        println("🔌 Starting VPN engine...")
        
        // Ensure MainActivity is launched
        launchMainActivity()
        
        // Wait a bit more for Compose UI to fully render
        delay(2000)
        
        // First, check if VPN permission is already granted
        println("   Checking VPN permission status...")
        val permissionIntent = android.net.VpnService.prepare(appContext)
        
        if (permissionIntent != null) {
            println("   VPN permission NOT granted - will need to handle dialog")
        } else {
            println("   ✅ VPN permission already granted")
        }
        
        // Find the "Start Service" toggle using multiple strategies
        var startToggle = device.wait(
            Until.findObject(By.res(appContext.packageName, "start_service_toggle")),
            5000
        )
        
        // If not found by testTag, try finding by text
        if (startToggle == null) {
            println("   Toggle not found by testTag, trying by text 'Start VPN'...")
            val startVpnText = device.wait(Until.findObject(By.text("Start VPN")), 5000)
            if (startVpnText != null) {
                startToggle = startVpnText.parent
            }
        }
        
        // Last resort: try finding any Switch
        if (startToggle == null) {
            println("   Trying to find any Switch component...")
            startToggle = device.wait(Until.findObject(By.clazz("android.widget.Switch")), 3000)
        }
        
        assertNotNull("❌ Could not find 'start_service_toggle' in UI. Is MainActivity displayed?", startToggle)

        // Click the toggle using bounds center (better for Compose Switch)
        if (!startToggle!!.isChecked) {
            println("   Clicking VPN toggle...")
            val bounds = startToggle.visibleBounds
            if (bounds.width() > 0 && bounds.height() > 0) {
                device.click(bounds.centerX(), bounds.centerY())
                println("   Clicked toggle at center: (${bounds.centerX()}, ${bounds.centerY()})")
            } else {
                startToggle.click()
                println("   Clicked toggle directly")
            }
        } else {
            println("   VPN toggle already enabled")
        }

        // Wait a moment for permission dialog to appear (if needed)
        delay(1000)

        // Handle VPN permission dialog if needed
        if (permissionIntent != null) {
            println("   Handling VPN permission dialog...")
            handlePermissionDialog()
        } else {
            println("   Permission already granted, skipping dialog")
        }

        // Wait for the VPN to start and establish interface
        println("   Waiting 15 seconds for VPN engine to initialize and establish interface...")
        delay(15000) // Give enough time for VPN interface establishment and VpnConnectionManager initialization
        println("   ✓ VPN engine started")
    }
    
    /**
     * Handles the VPN permission dialog with multiple strategies.
     */
    private fun handlePermissionDialog() = runBlocking {
        println("   Waiting for VPN permission dialog...")
        
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
            allowButton = device.wait(
                Until.findObject(By.res("android:id/button1")),
                3000
            )
        }
        
        if (allowButton == null) {
            allowButton = device.wait(
                Until.findObject(By.res("android:id/positive")),
                2000
            )
        }
        
        if (allowButton != null) {
            println("   Found VPN permission dialog button, clicking...")
            allowButton.click()
            delay(2000) // Wait for permission to be granted
            
            // Verify dialog is gone
            val dialogStillVisible = device.hasObject(By.text("OK")) || 
                                    device.hasObject(By.text("Allow")) ||
                                    device.hasObject(By.res("android:id/button1"))
            
            if (dialogStillVisible) {
                println("   Dialog still visible, clicking again...")
                allowButton.click()
                delay(1000)
            }
            println("   ✅ VPN permission dialog handled")
        } else {
            println("   No VPN permission dialog found (may already be granted)")
        }
        
        // Give extra time for permission to propagate
        delay(1000)
    }
    
    /**
     * Verifies that a VPN tunnel is connected, waiting up to timeoutMs milliseconds.
     * Throws AssertionError if tunnel doesn't connect.
     */
    private fun verifyTunnelConnected(tunnelId: String, timeoutMs: Long = 60000) {
        println("🔍 Verifying tunnel connection: $tunnelId")
        
        val startTime = System.currentTimeMillis()
        var connected = false
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                // Try to access VpnConnectionManager to check tunnel status
                // Note: We need to get the instance through the service or app context
                val connectionManager = com.multiregionvpn.core.VpnConnectionManager.getInstance()
                
                if (connectionManager.isTunnelConnected(tunnelId)) {
                    connected = true
                    println("   ✅ Tunnel $tunnelId is connected!")
                    break
                } else {
                    println("   ⏳ Tunnel $tunnelId not connected yet... (${(System.currentTimeMillis() - startTime)/1000}s)")
                    Thread.sleep(2000) // Check every 2 seconds
                }
            } catch (e: IllegalStateException) {
                // VpnConnectionManager not initialized - this means VPN service hasn't started it yet
                println("   ⏳ VpnConnectionManager not initialized yet... (${(System.currentTimeMillis() - startTime)/1000}s)")
                Thread.sleep(2000)
            } catch (e: Exception) {
                println("   ⚠️  Error checking tunnel status: ${e.message}")
                Thread.sleep(2000)
            }
        }
        
        if (!connected) {
            // Capture diagnostics before failing
            captureDiagnostics("tunnel_not_connected_$tunnelId")
            
            throw AssertionError(
                "❌ Tunnel $tunnelId did not connect within ${timeoutMs/1000} seconds.\n" +
                "This usually means:\n" +
                "1. OpenVPN 3 native library isn't working\n" +
                "2. VPN credentials are invalid\n" +
                "3. VPN server is unreachable\n" +
                "4. Configuration is incorrect\n" +
                "\nCheck logcat for details: adb logcat -s VpnConnectionManager NativeOpenVpnClient"
            )
        }
    }
    
    /**
     * Captures diagnostic information to help debug connection issues.
     */
    private fun captureDiagnostics(context: String) {
        println("\n📊 CAPTURING DIAGNOSTICS: $context")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        
        // Check VpnConnectionManager status
        try {
            val connectionManager = com.multiregionvpn.core.VpnConnectionManager.getInstance()
            println("   ✓ VpnConnectionManager initialized")
            // Try to check if any tunnels exist
            println("   (Cannot check tunnel list from test - requires service access)")
        } catch (e: IllegalStateException) {
            println("   ❌ VpnConnectionManager NOT initialized: ${e.message}")
            println("   This means VPN service hasn't called VpnConnectionManager.initialize()")
        }
        
        // Check for recent VPN-related logcat entries
        println("\n   Recent VPN logs (check manually):")
        println("   adb logcat -d -s VpnConnectionManager NativeOpenVpnClient PacketRouter VpnEngineService")
        
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
    }


    /**
     * Stops the VPN service and cleans up test data.
     */
    @After
    fun teardown() = runBlocking {
        println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("🧹 Cleaning up...")
        
        // Stop the VPN service
        try {
            val stopToggle = device.wait(
                Until.findObject(By.res(appContext.packageName, "start_service_toggle")),
                2000
            )
            if (stopToggle != null && stopToggle.isChecked) {
                println("   Stopping VPN service...")
                stopToggle.click()
                Thread.sleep(2000) // Give it time to stop
            }
        } catch (e: Exception) {
            println("   ⚠️  Could not stop VPN: ${e.message}")
        }

        // Also try to stop via service intent
        try {
            val stopIntent = Intent(appContext, VpnEngineService::class.java).apply {
                action = VpnEngineService.ACTION_STOP
            }
            appContext.startService(stopIntent)
        } catch (e: Exception) {
            println("   ⚠️  Could not stop VPN service: ${e.message}")
        }

        // Clean up the database
        settingsRepo.clearAllAppRules()
        settingsRepo.clearAllVpnConfigs()
        println("✓ Test data cleaned up")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
    }

}

