package com.multiregionvpn

import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.multiregionvpn.core.VpnEngineService
import com.multiregionvpn.core.VpnConnectionManager
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
 * Test Suite 1: NordVpnE2ETest.kt (The "Live" Test)
 * 
 * Purpose: End-to-end (E2E) "black-box" validation that the entire application pipeline
 * works with the live, production NordVPN API and authentication system.
 * 
 * This test validates:
 * 1. Routing to UK VPN (using real NordVPN servers)
 * 2. Routing to FR VPN (using real NordVPN servers)
 * 3. Routing to Direct Internet (no rule)
 * 
 * Status: Retained as-is - provides production environment validation.
 */
@RunWith(AndroidJUnit4::class)
class NordVpnE2ETest {

    // Grant runtime permissions automatically before each test
    // Note: VPN permission (BIND_VPN_SERVICE) is special and requires VpnService.prepare() which shows a system dialog.
    // GrantPermissionRule only works for runtime permissions, not for special permissions like VPN.
    // We'll handle VPN permission separately via the permission dialog.
    @get:Rule
    val grantPermissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.INTERNET,
        android.Manifest.permission.ACCESS_NETWORK_STATE,
        // Note: BIND_VPN_SERVICE, FOREGROUND_SERVICE, and FOREGROUND_SERVICE_DATA_SYNC are not runtime permissions
        // and cannot be granted via GrantPermissionRule. They must be granted via manifest or system dialogs.
    )

    private lateinit var settingsRepo: SettingsRepository

    private lateinit var device: UiDevice
    private lateinit var appContext: Context
    private lateinit var testContext: Context
    private lateinit var defaultCountry: String

    // --- Test Configuration ---
    // CRITICAL: Use test instrumentation package name (com.multiregionvpn.test), NOT target app package
    // The test runner's HTTP calls must be routed through VPN, so we need the test package
    // - targetContext.packageName = "com.multiregionvpn" (the app being tested)
    // - context.packageName = "com.multiregionvpn.test" (the test instrumentation runner)
    private val TEST_PACKAGE_NAME = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.packageName
    private val UK_VPN_ID = "test-uk-${UUID.randomUUID().toString().take(8)}"
    private val FR_VPN_ID = "test-fr-${UUID.randomUUID().toString().take(8)}"
    
    // Real NordVPN server hostnames - these are actual servers that exist
    // Format: {country_code}{number}.nordvpn.com
    // Using servers from API recommendations that are confirmed to exist
    private val UK_SERVER_HOSTNAME = "uk1827.nordvpn.com" // Real UK server (from API recommendations)
    private val FR_SERVER_HOSTNAME = "fr985.nordvpn.com" // Real FR server (from API recommendations)

    @Before
    fun setup() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        device = UiDevice.getInstance(instrumentation)
        appContext = instrumentation.targetContext
        testContext = instrumentation.context

        // Note: App data should be cleared before running tests via:
        // adb shell pm clear com.multiregionvpn

        // Pre-approve VPN permission using appops (App Operations)
        // This is the recommended way to grant VPN permission for integration tests.
        // Command: adb shell appops set <package.name> ACTIVATE_VPN allow
        // This makes VpnService.prepare() return null immediately (permission already granted).
        // NOTE: This should ideally be run before tests via CI/CD script, but we try here too.
        try {
            // Use UiAutomator's shell command execution
            val appopsCommand = "appops set ${appContext.packageName} ACTIVATE_VPN allow"
            val result = device.executeShellCommand(appopsCommand)
            println("✅ VPN permission pre-approved via appops: $result")
        } catch (e: Exception) {
            println("⚠️  Could not pre-approve VPN permission via appops: ${e.message}")
            println("   This may need to be run manually: adb shell appops set ${appContext.packageName} ACTIVATE_VPN allow")
            println("   The test will try to handle the permission dialog instead")
        }
        
        // Initialize repository manually (without Hilt for E2E tests)
        val database = androidx.room.Room.databaseBuilder(
            appContext.applicationContext,
            AppDatabase::class.java,
            "region_router_db"
        )
            .addCallback(AppDatabase.PresetRuleCallback())
            .build()
        settingsRepo = SettingsRepository(
            database.vpnConfigDao(),
            database.appRuleDao(),
            database.providerCredentialsDao(),
            database.presetRuleDao()
        )

        // 1. Stop VPN if running from previous test
        println("🛑 Stopping VPN if running...")
        try {
            val stopIntent = Intent(appContext, VpnEngineService::class.java).apply {
                action = VpnEngineService.ACTION_STOP
            }
            appContext.startService(stopIntent)
            delay(2000)  // Wait for VPN to fully stop
            println("✅ VPN stopped (or wasn't running)")
        } catch (e: Exception) {
            println("⚠️  Could not stop VPN: ${e.message}")
        }
        
        // 2. Clear all old test data
        settingsRepo.clearAllAppRules()
        settingsRepo.clearAllVpnConfigs()

        // 3. Get our baseline IP (Direct Internet) - make call BEFORE VPN starts
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
        
        // CRITICAL: Wait for Room Flow to emit the change
        // Without this delay, VPN starts before Flow emits updated rules
        delay(500)
        
        // Verify rule was actually saved
        val savedRule = settingsRepo.getAppRuleByPackageName(TEST_PACKAGE_NAME)
        println("✓ Verified app rule saved: ${savedRule?.packageName} -> ${savedRule?.vpnConfigId}")
        assert(savedRule != null) { "App rule was not saved!" }
        assert(savedRule?.vpnConfigId == UK_VPN_ID) { "App rule has wrong VPN config!" }

        // WHEN: The VPN service is started
        startVpnEngine()

        // Verify tunnel is FULLY READY for routing (connected + IP + DNS)
        val tunnelId = "nordvpn_UK"
        verifyTunnelReadyForRouting(tunnelId, timeoutMs = 120000)
        
        // CRITICAL: Wait for routing to stabilize after interface re-establishments
        // The interface is re-established multiple times (Flow detect, IP received, DNS received)
        // Android's routing tables need time to stabilize
        println("⏳ Waiting 5 seconds for routing to stabilize after interface re-establishments...")
        delay(5000)
        
        // THEN: Our IP should be in the UK
        println("⏳ Making HTTP request to verify routing...")
        println("   Tunnel is fully ready and routing has stabilized")
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
        delay(500)  // Wait for Room to commit

        // WHEN: The VPN service is started
        startVpnEngine()

        // Verify tunnel is FULLY READY for routing (connected + IP + DNS)
        val tunnelId = "nordvpn_FR"
        verifyTunnelReadyForRouting(tunnelId, timeoutMs = 120000)
        
        // THEN: Our IP should be in France
        // No additional delays needed - verifyTunnelReadyForRouting ensures everything is ready
        println("⏳ Making HTTP request to verify routing...")
        println("   Tunnel is fully ready - connection, IP, and DNS are all configured")
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

    @Test
    fun test_switchRegions_UKtoFR() = runBlocking {
        println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("🧪 TEST: Switch Regions (UK → FR)")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        
        // PHASE 1: Route to UK
        println("\n📍 PHASE 1: Initial routing to UK")
        settingsRepo.createAppRule(TEST_PACKAGE_NAME, UK_VPN_ID)
        println("✓ Created app rule: $TEST_PACKAGE_NAME -> UK VPN")

        startVpnEngine()
        verifyTunnelReadyForRouting("nordvpn_UK", timeoutMs = 120000)
        
        val ukIpInfo = IpCheckService.api.getIpInfo()
        println("📍 UK IP: ${ukIpInfo.normalizedIpAddress}, Country: ${ukIpInfo.normalizedCountryCode}")
        assertEquals("GB", ukIpInfo.normalizedCountryCode)
        println("✅ Phase 1 complete: Confirmed UK routing")
        
        // PHASE 2: Switch to FR
        println("\n📍 PHASE 2: Switching to France")
        settingsRepo.updateAppRule(TEST_PACKAGE_NAME, FR_VPN_ID)
        println("✓ Updated app rule: $TEST_PACKAGE_NAME -> FR VPN")
        
        // Wait for routing to update
        delay(5000)
        verifyTunnelReadyForRouting("nordvpn_FR", timeoutMs = 120000)
        
        val frIpInfo = IpCheckService.api.getIpInfo()
        println("📍 FR IP: ${frIpInfo.normalizedIpAddress}, Country: ${frIpInfo.normalizedCountryCode}")
        assertEquals(
            "❌ Failed to switch to France! Expected FR, got ${frIpInfo.normalizedCountryCode}",
            "FR",
            frIpInfo.normalizedCountryCode
        )
        println("✅ Phase 2 complete: Confirmed switch to France")
        println("✅ TEST PASSED: Successfully switched regions UK → FR")
    }

    @Test
    fun test_multiTunnel_BothUKandFRActive() = runBlocking {
        println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("🧪 TEST: Multi-Tunnel Coexistence (UK + FR)")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        
        // GIVEN: Create app rules for BOTH regions to force both tunnels to establish
        // This is necessary because VpnEngineService only creates tunnels for VPN configs with app rules
        settingsRepo.createAppRule(TEST_PACKAGE_NAME, UK_VPN_ID)
        println("✓ Created app rule: $TEST_PACKAGE_NAME -> UK VPN")
        
        // Create a dummy app rule for FR to force FR tunnel creation
        // (In a real scenario, a different app would have the FR rule)
        settingsRepo.createAppRule("com.dummy.app.france", FR_VPN_ID)
        println("✓ Created dummy app rule: com.dummy.app.france -> FR VPN")
        println("   (This forces VpnEngineService to create FR tunnel even though we won't route to it)")

        // WHEN: VPN service starts, it should establish BOTH tunnels
        // (UK for our test app, FR for the dummy app - both tunnels coexist)
        startVpnEngine()
        
        // Verify both tunnels become ready
        println("\n📍 Verifying UK tunnel (in use)...")
        verifyTunnelReadyForRouting("nordvpn_UK", timeoutMs = 120000)
        println("✅ UK tunnel ready")
        
        println("\n📍 Verifying FR tunnel (standby)...")
        verifyTunnelReadyForRouting("nordvpn_FR", timeoutMs = 120000)
        println("✅ FR tunnel ready")
        
        // THEN: Our traffic should route to UK (the configured tunnel)
        val vpnIpInfo = IpCheckService.api.getIpInfo()
        println("📍 Resulting IP: ${vpnIpInfo.normalizedIpAddress}")
        println("📍 Resulting Country: ${vpnIpInfo.normalizedCountryCode}")
        
        assertEquals(
            "❌ Traffic not routed to UK! Expected GB, got ${vpnIpInfo.normalizedCountryCode}",
            "GB",
            vpnIpInfo.normalizedCountryCode
        )
        
        // Verify VpnConnectionManager reports both tunnels as connected
        val connectionManager = VpnConnectionManager.getInstance()
        val ukConnected = connectionManager.isTunnelConnected("nordvpn_UK")
        val frConnected = connectionManager.isTunnelConnected("nordvpn_FR")
        
        assertTrue("❌ UK tunnel not connected!", ukConnected)
        assertTrue("❌ FR tunnel not connected!", frConnected)
        
        println("✅ Both tunnels active and ready")
        println("✅ TEST PASSED: Multi-tunnel architecture working (UK + FR coexist)")
    }

    @Test
    fun test_rapidSwitching_UKtoFRtoUK() = runBlocking {
        println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("🧪 TEST: Rapid Region Switching (UK → FR → UK)")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        
        // Start with UK
        println("\n📍 Switch 1: UK")
        settingsRepo.createAppRule(TEST_PACKAGE_NAME, UK_VPN_ID)
        startVpnEngine()
        verifyTunnelReadyForRouting("nordvpn_UK", timeoutMs = 120000)
        
        val uk1IpInfo = IpCheckService.api.getIpInfo()
        assertEquals("GB", uk1IpInfo.normalizedCountryCode)
        println("✅ Confirmed UK (switch 1)")
        
        // Switch to FR
        println("\n📍 Switch 2: FR")
        settingsRepo.updateAppRule(TEST_PACKAGE_NAME, FR_VPN_ID)
        delay(3000) // Brief delay for routing update
        verifyTunnelReadyForRouting("nordvpn_FR", timeoutMs = 120000)
        
        val frIpInfo = IpCheckService.api.getIpInfo()
        assertEquals("FR", frIpInfo.normalizedCountryCode)
        println("✅ Confirmed FR (switch 2)")
        
        // Switch back to UK
        println("\n📍 Switch 3: UK (again)")
        settingsRepo.updateAppRule(TEST_PACKAGE_NAME, UK_VPN_ID)
        delay(3000) // Brief delay for routing update
        // UK tunnel should still be connected from before
        
        val uk2IpInfo = IpCheckService.api.getIpInfo()
        assertEquals(
            "❌ Failed to switch back to UK! Expected GB, got ${uk2IpInfo.normalizedCountryCode}",
            "GB",
            uk2IpInfo.normalizedCountryCode
        )
        println("✅ Confirmed UK (switch 3)")
        
        println("✅ TEST PASSED: Rapid switching handled correctly (UK → FR → UK)")
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
     * 
     * NOTE: If UI toggle doesn't work, we fall back to directly starting the service.
     */
    private fun startVpnEngine() = runBlocking {
        println("🔌 Starting VPN engine...")
        
        // Check VPN permission status
        // If appops was used to pre-approve VPN, VpnService.prepare() will return null immediately.
        // Otherwise, we'll need to handle the permission dialog.
        println("   Checking VPN permission status...")
        val vpnPermissionIntent = android.net.VpnService.prepare(appContext)
        if (vpnPermissionIntent != null) {
            println("   ⚠️  VPN permission NOT pre-approved - permission dialog will appear")
            println("   Attempting to grant via permission dialog...")
            println("   NOTE: For CI/CD, run: adb shell appops set ${appContext.packageName} ACTIVATE_VPN allow")
            
            // Launch permission dialog
            try {
                appContext.startActivity(vpnPermissionIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                delay(5000) // Wait longer for dialog to appear
                
                // Handle permission dialog using UiAutomator
                handlePermissionDialog()
                delay(3000) // Wait for permission to be granted
                
                // Verify permission was granted
                val checkAfter = android.net.VpnService.prepare(appContext)
                if (checkAfter != null) {
                    println("   ⚠️  VPN permission still not granted after dialog handling")
                    println("   VPN interface establishment will likely fail")
                    println("   Recommendation: Run 'adb shell appops set ${appContext.packageName} ACTIVATE_VPN allow' before tests")
                } else {
                    println("   ✅ VPN permission granted successfully via dialog")
                }
            } catch (e: Exception) {
                println("   ⚠️  Error handling VPN permission: ${e.message}")
                // Continue anyway - might already be granted
            }
        } else {
            println("   ✅ VPN permission already pre-approved (appops ACTIVATE_VPN allow worked!)")
        }
        
        // Now try direct service start (bypass UI for reliability)
        try {
            println("   Attempting direct service start (bypassing UI)...")
            val startIntent = Intent(appContext, VpnEngineService::class.java).apply {
                action = VpnEngineService.ACTION_START
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                appContext.startForegroundService(startIntent)
            } else {
                appContext.startService(startIntent)
            }
            println("   ✅ Direct service start sent - waiting for VPN to initialize...")
            delay(5000) // Wait for service to start
            
            // Check if service is actually running
            val serviceRunning = try {
                val connectionManager = VpnConnectionManager.getInstance()
                true
            } catch (e: Exception) {
                false
            }
            
            if (serviceRunning) {
                println("   ✅ VPN service started successfully via direct intent")
                delay(10000) // Give time for VPN interface to establish
                return@runBlocking
            } else {
                println("   ⚠️  Service started but VpnConnectionManager not yet initialized")
                // Continue - it might initialize soon
            }
        } catch (e: Exception) {
            println("   ⚠️  Direct service start failed: ${e.message}")
            println("   Falling back to UI toggle method...")
        }
        
        // Fallback: Use UI toggle method
        println("   Using UI toggle method...")
        
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

        // Check toggle state and ensure it's OFF before clicking
        val toggleState = startToggle!!.isChecked
        println("   VPN toggle current state: ${if (toggleState) "ON (checked)" else "OFF (unchecked)"}")
        
        if (toggleState) {
            // Toggle is already ON - this means VPN is already running
            // We need to turn it OFF first, then ON again to restart it
            println("   ⚠️  Toggle is already ON - turning OFF first, then ON again")
            val bounds = startToggle.visibleBounds
            if (bounds.width() > 0 && bounds.height() > 0) {
                device.click(bounds.centerX(), bounds.centerY())
                println("   Clicked toggle to turn OFF at center: (${bounds.centerX()}, ${bounds.centerY()})")
            } else {
                startToggle.click()
                println("   Clicked toggle to turn OFF directly")
            }
            // Wait for the stop action to complete
            delay(2000)
            
            // Now click again to turn it ON
            val toggleAfterStop = device.wait(
                Until.findObject(By.res(appContext.packageName, "start_service_toggle")),
                3000
            )
            if (toggleAfterStop != null && !toggleAfterStop.isChecked) {
                println("   Clicking toggle again to turn ON...")
                val bounds2 = toggleAfterStop.visibleBounds
                if (bounds2.width() > 0 && bounds2.height() > 0) {
                    device.click(bounds2.centerX(), bounds2.centerY())
                    println("   Clicked toggle to turn ON at center: (${bounds2.centerX()}, ${bounds2.centerY()})")
                } else {
                    toggleAfterStop.click()
                    println("   Clicked toggle to turn ON directly")
                }
            } else {
                println("   ⚠️  Could not find toggle after stop, or it's still checked")
            }
        } else {
            // Toggle is OFF - click to turn ON
            println("   Clicking VPN toggle to turn ON...")
            val bounds = startToggle.visibleBounds
            if (bounds.width() > 0 && bounds.height() > 0) {
                device.click(bounds.centerX(), bounds.centerY())
                println("   Clicked toggle at center: (${bounds.centerX()}, ${bounds.centerY()})")
            } else {
                startToggle.click()
                println("   Clicked toggle directly")
            }
        }

        // Wait a moment for permission dialog to appear (if needed)
        delay(2000)  // Increased wait time for Compose to process the click

        // Handle VPN permission dialog if needed
        if (permissionIntent != null) {
            println("   Handling VPN permission dialog...")
            handlePermissionDialog()
            // Wait a bit after granting permission for the service to start
            delay(2000)
        } else {
            println("   Permission already granted, skipping dialog")
        }

        // Verify that ACTION_START was sent by checking logs
        println("   Checking if VPN service received ACTION_START...")
        delay(1000)
        
        // Wait for the VPN to start and establish interface
        println("   Waiting 15 seconds for VPN engine to initialize and establish interface...")
        delay(15000) // Give enough time for VPN interface establishment and VpnConnectionManager initialization
        println("   ✓ VPN engine started")
    }
    
    /**
     * Handles the VPN permission dialog with multiple strategies.
     * VPN permission requires user confirmation via a system dialog.
     */
    private fun handlePermissionDialog() = runBlocking {
        println("   Waiting for VPN permission dialog...")
        delay(1000) // Give dialog time to appear
        
        // Try multiple button texts and resource IDs
        val buttonTexts = listOf("OK", "Allow", "Accept", "Yes")
        val buttonResources = listOf(
            "android:id/button1",
            "android:id/positiveButton",
            "com.android.permissioncontroller:id/grant_dialog_button_allow"
        )
        
        var allowButton: androidx.test.uiautomator.UiObject2? = null
        
        // Try button texts first
        for (text in buttonTexts) {
            allowButton = device.wait(
                Until.findObject(By.text(text)),
                2000
            )
            if (allowButton != null) {
                println("   Found permission dialog button: '$text'")
                break
            }
        }
        
        // Try resource IDs if text search failed
        if (allowButton == null) {
            for (resId in buttonResources) {
                allowButton = device.wait(
                    Until.findObject(By.res(resId)),
                    2000
                )
                if (allowButton != null) {
                    println("   Found permission dialog button by resource: '$resId'")
                    break
                }
            }
        }
        
        // Try clicking by coordinates if we can find the dialog
        if (allowButton == null) {
            // Sometimes the dialog appears but we can't find the button
            // Try to find any clickable element in the dialog area
            val dialog = device.wait(
                Until.findObject(By.pkg("com.android.permissioncontroller")),
                2000
            )
            if (dialog != null) {
                println("   Found permission dialog window, trying to click OK area...")
                // Click in the center-right area where OK buttons typically are
                val bounds = dialog.visibleBounds
                if (bounds.width() > 0 && bounds.height() > 0) {
                    val clickX = bounds.right - 100
                    val clickY = bounds.bottom - 100
                    device.click(clickX, clickY)
                    println("   Clicked dialog at ($clickX, $clickY)")
                    delay(1000)
                    return@runBlocking
                }
            }
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
     * Verifies that a VPN tunnel is fully ready for routing, waiting up to timeoutMs milliseconds.
     * This checks:
     * 1. OpenVPN connection is established
     * 2. Tunnel IP address has been assigned via DHCP
     * 3. DNS servers have been configured
     * 
     * Throws AssertionError if tunnel doesn't become ready.
     */
    private fun verifyTunnelReadyForRouting(tunnelId: String, timeoutMs: Long = 120000) {
        println("🔍 Verifying tunnel is ready for routing: $tunnelId")
        println("   Waiting for: OpenVPN connection + IP assignment + DNS configuration")
        
        val startTime = System.currentTimeMillis()
        var ready = false
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                // Try to access VpnConnectionManager to check tunnel readiness
                val connectionManager = com.multiregionvpn.core.VpnConnectionManager.getInstance()
                
                if (connectionManager.isTunnelReadyForRouting(tunnelId)) {
                    ready = true
                    println("   ✅ Tunnel $tunnelId is FULLY READY for routing!")
                    println("      - OpenVPN connection: ✅")
                    println("      - IP address assigned: ✅")
                    println("      - DNS configured: ✅")
                    break
                } else {
                    // Show partial readiness status
                    val connected = connectionManager.isTunnelConnected(tunnelId)
                    if (connected) {
                        println("   ⏳ Tunnel $tunnelId connected, waiting for IP + DNS... (${(System.currentTimeMillis() - startTime)/1000}s)")
                    } else {
                        println("   ⏳ Tunnel $tunnelId connecting... (${(System.currentTimeMillis() - startTime)/1000}s)")
                    }
                    Thread.sleep(2000) // Check every 2 seconds
                }
            } catch (e: IllegalStateException) {
                // VpnConnectionManager not initialized - this means VPN service hasn't started it yet
                println("   ⏳ VpnConnectionManager not initialized yet... (${(System.currentTimeMillis() - startTime)/1000}s)")
                Thread.sleep(2000)
            } catch (e: Exception) {
                println("   ⚠️  Error checking tunnel readiness: ${e.message}")
                Thread.sleep(2000)
            }
        }
        
        if (!ready) {
            // Capture diagnostics before failing
            captureDiagnostics("tunnel_not_ready_$tunnelId")
            
            throw AssertionError(
                "❌ Tunnel $tunnelId did not become ready for routing within ${timeoutMs/1000} seconds.\n" +
                "The tunnel may be:\n" +
                "1. Not connected (OpenVPN 3 connection failed)\n" +
                "2. Connected but IP not assigned (DHCP issue)\n" +
                "3. Connected with IP but DNS not configured (DNS push issue)\n" +
                "\nCheck logcat for details: adb logcat -s VpnConnectionManager NativeOpenVpnClient VpnEngineService"
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
        
        // CRITICAL: Close VPN connections FIRST before stopping service
        // This prevents race conditions and multiple disconnects
        var connectionsClosed = false
        try {
            val connectionManager = VpnConnectionManager.getInstance()
            println("   Closing all VPN connections...")
            connectionManager.closeAll()
            connectionsClosed = true
            println("   ✅ All VPN connections closed")
            
            // Wait for native resources to be freed
            // Socket pairs, OpenVPN 3 sessions, and file descriptors need time to clean up
            Thread.sleep(2000)
            println("   ✅ Native resources cleanup complete")
        } catch (e: IllegalStateException) {
            println("   VpnConnectionManager not initialized (no connections to close)")
        } catch (e: Exception) {
            println("   ⚠️  Could not close VPN connections: ${e.message}")
            e.printStackTrace()
        }

        // Now stop the service (only if connections were already closed)
        if (connectionsClosed) {
            try {
                val stopIntent = Intent(appContext, VpnEngineService::class.java).apply {
                    action = VpnEngineService.ACTION_STOP
                }
                appContext.startService(stopIntent)
                Thread.sleep(1000) // Brief wait for service to stop
                println("   ✅ VPN service stopped")
            } catch (e: Exception) {
                println("   ⚠️  Could not stop VPN service: ${e.message}")
            }
        }

        // Clean up the database
        settingsRepo.clearAllAppRules()
        settingsRepo.clearAllVpnConfigs()
        println("✓ Test data cleaned up")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
    }

}

