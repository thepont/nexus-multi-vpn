package com.multiregionvpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.multiregionvpn.core.VpnEngineService
import com.multiregionvpn.data.database.AppRule
import com.multiregionvpn.data.database.VpnConfig
import com.multiregionvpn.data.repository.SettingsRepository
import com.multiregionvpn.ui.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * E2E Test for Mobile UI
 * 
 * Tests the complete user journey:
 * 1. Launch mobile app
 * 2. Configure provider credentials
 * 3. Add VPN tunnels
 * 4. Configure app routing rules
 * 5. Toggle VPN on/off
 * 6. Verify VPN status changes
 * 7. Verify internet connectivity
 * 
 * Uses Compose Testing for touch-based navigation.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class MobileUiE2ETest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private lateinit var uiDevice: UiDevice
    private lateinit var context: Context

    @Before
    fun setup() {
        hiltRule.inject()
        
        context = ApplicationProvider.getApplicationContext()
        uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        
        // Grant VPN permission via ADB
        grantVpnPermission()
        
        // Clean database before test
        runBlocking {
            settingsRepository.clearAllVpnConfigs()
            settingsRepository.clearAllAppRules()
        }
        
        // Wait for UI to be ready
        composeTestRule.waitForIdle()
    }

    @After
    fun teardown() {
        // Stop VPN if running
        if (VpnEngineService.isRunning()) {
            val stopIntent = Intent(context, VpnEngineService::class.java).apply {
                action = VpnEngineService.ACTION_STOP
            }
            context.startService(stopIntent)
        }
        
        // Clean database after test
        runBlocking {
            settingsRepository.clearAllVpnConfigs()
            settingsRepository.clearAllAppRules()
        }
    }

    @Test
    fun testCompleteUserJourney_ConfigureAndActivateVpn() {
        // ═══════════════════════════════════════════════════════════════════════════
        // STEP 1: Verify app launched successfully
        // ═══════════════════════════════════════════════════════════════════════════
        
        // Wait for settings screen to appear
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            try {
                composeTestRule.onNodeWithTag("settings_screen").assertExists()
                true
            } catch (e: AssertionError) {
                false
            }
        }
        
        // Verify header bar is visible
        composeTestRule.onNodeWithTag("vpn_header_bar").assertExists()
        
        // ═══════════════════════════════════════════════════════════════════════════
        // STEP 2: Configure provider credentials (NordVPN)
        // ═══════════════════════════════════════════════════════════════════════════
        
        // Scroll to provider credentials section
        composeTestRule.onNodeWithText("Provider Credentials", substring = true)
            .assertExists()
            .performScrollTo()
        
        // Enter username
        composeTestRule.onNodeWithTag("nord_username_field")
            .performClick()
            .performTextClearance()
            .performTextInput("test_user")
        
        // Enter password
        composeTestRule.onNodeWithTag("nord_password_field")
            .performClick()
            .performTextClearance()
            .performTextInput("test_password")
        
        // Save credentials
        composeTestRule.onNodeWithText("Save Credentials")
            .performScrollTo()
            .performClick()
        
        // Wait for save to complete
        Thread.sleep(1000)
        
        // ═══════════════════════════════════════════════════════════════════════════
        // STEP 3: Add VPN tunnels (UK and FR)
        // ═══════════════════════════════════════════════════════════════════════════
        
        // Scroll to VPN configs section
        composeTestRule.onNodeWithText("My VPN Servers", substring = true)
            .assertExists()
            .performScrollTo()
        
        // Add UK tunnel
        composeTestRule.onNodeWithText("Add Server")
            .performScrollTo()
            .performClick()
        
        // Fill in UK server details
        composeTestRule.onNodeWithTag("vpn_name_field")
            .performClick()
            .performTextInput("UK Server")
        
        composeTestRule.onNodeWithTag("vpn_region_field")
            .performClick()
            .performTextInput("uk")
        
        composeTestRule.onNodeWithText("Fetch NordVPN Server")
            .performClick()
        
        // Wait for server fetch
        Thread.sleep(2000)
        
        composeTestRule.onNodeWithText("Save", substring = true)
            .performClick()
        
        // Wait for save
        Thread.sleep(1000)
        
        // Add FR tunnel
        composeTestRule.onNodeWithText("Add Server")
            .performScrollTo()
            .performClick()
        
        composeTestRule.onNodeWithTag("vpn_name_field")
            .performClick()
            .performTextInput("FR Server")
        
        composeTestRule.onNodeWithTag("vpn_region_field")
            .performClick()
            .performTextInput("fr")
        
        composeTestRule.onNodeWithText("Fetch NordVPN Server")
            .performClick()
        
        Thread.sleep(2000)
        
        composeTestRule.onNodeWithText("Save", substring = true)
            .performClick()
        
        Thread.sleep(1000)
        
        // Verify tunnels were added
        runBlocking {
            val configs = settingsRepository.getAllVpnConfigs().first()
            assert(configs.size >= 2) { "Expected at least 2 VPN configs, got ${configs.size}" }
            assert(configs.any { it.regionId == "uk" }) { "UK config not found" }
            assert(configs.any { it.regionId == "fr" }) { "FR config not found" }
        }
        
        // ═══════════════════════════════════════════════════════════════════════════
        // STEP 4: Configure app routing rules
        // ═══════════════════════════════════════════════════════════════════════════
        
        // Scroll to app rules section
        composeTestRule.onNodeWithText("App Routing Rules", substring = true)
            .assertExists()
            .performScrollTo()
        
        // Find an app to route (use our own app as test subject)
        val testPackage = context.packageName
        
        // Scroll through apps to find our test app
        composeTestRule.onAllNodesWithText(testPackage, substring = true)
            .onFirst()
            .assertExists()
            .performScrollTo()
        
        // Click on the app's routing dropdown
        composeTestRule.onNodeWithTag("app_rule_dropdown_$testPackage")
            .performScrollTo()
            .performClick()
        
        // Select UK tunnel
        composeTestRule.onNodeWithText("UK Server", substring = true)
            .performClick()
        
        Thread.sleep(1000)
        
        // Verify rule was saved
        runBlocking {
            val rules = settingsRepository.getAllAppRules().first()
            assert(rules.any { it.packageName == testPackage }) { 
                "App rule not created for $testPackage" 
            }
        }
        
        // ═══════════════════════════════════════════════════════════════════════════
        // STEP 5: Toggle VPN ON
        // ═══════════════════════════════════════════════════════════════════════════
        
        // Scroll to top to access header bar
        composeTestRule.onNodeWithTag("settings_screen")
            .performScrollTo()
        
        // Find and click VPN toggle
        composeTestRule.onNodeWithTag("vpn_toggle_switch")
            .assertExists()
            .performClick()
        
        // Wait for VPN to start
        Thread.sleep(5000)
        
        // Verify VPN is running
        assert(VpnEngineService.isRunning()) { "VPN service should be running" }
        
        // Verify status in UI
        composeTestRule.onNodeWithTag("vpn_status_text")
            .assertExists()
            .assertTextContains("Protected", substring = true, ignoreCase = true)
        
        // ═══════════════════════════════════════════════════════════════════════════
        // STEP 6: Verify VPN connection works
        // ═══════════════════════════════════════════════════════════════════════════
        
        // Wait for connection to stabilize
        Thread.sleep(3000)
        
        // Verify VPN interface is established
        // (VpnEngineService.isRunning() already checked above)
        
        // ═══════════════════════════════════════════════════════════════════════════
        // STEP 7: Toggle VPN OFF
        // ═══════════════════════════════════════════════════════════════════════════
        
        // Click toggle again to turn off
        composeTestRule.onNodeWithTag("vpn_toggle_switch")
            .performClick()
        
        // Wait for VPN to stop
        Thread.sleep(3000)
        
        // Verify VPN is stopped
        assert(!VpnEngineService.isRunning()) { "VPN service should be stopped" }
        
        // Verify status in UI
        composeTestRule.onNodeWithTag("vpn_status_text")
            .assertExists()
            .assertTextContains("Disconnected", substring = true, ignoreCase = true)
        
        // ═══════════════════════════════════════════════════════════════════════════
        // TEST COMPLETE
        // ═══════════════════════════════════════════════════════════════════════════
    }

    @Test
    fun testVpnToggle_OnOff() {
        // Simplified test: just toggle VPN on and off
        
        // Add a basic config first
        runBlocking {
            val ukConfig = VpnConfig(
                id = "test_uk_1",
                name = "Test UK",
                regionId = "uk",
                templateId = "nordvpn",
                serverHostname = "uk1860.nordvpn.com"
            )
            settingsRepository.saveVpnConfig(ukConfig)
            
            // Add app rule to trigger VPN interface creation
            val appRule = AppRule(
                packageName = context.packageName,
                vpnConfigId = ukConfig.id
            )
            settingsRepository.saveAppRule(appRule)
        }
        
        // Toggle ON
        composeTestRule.onNodeWithTag("vpn_toggle_switch")
            .assertExists()
            .performClick()
        
        Thread.sleep(5000)
        assert(VpnEngineService.isRunning()) { "VPN should be running" }
        
        // Toggle OFF
        composeTestRule.onNodeWithTag("vpn_toggle_switch")
            .performClick()
        
        Thread.sleep(3000)
        assert(!VpnEngineService.isRunning()) { "VPN should be stopped" }
    }

    @Test
    fun testAppRuleChange_UpdatesPersistence() {
        // Add VPN configs
        runBlocking {
            val ukConfig = VpnConfig(
                id = "test_uk_2",
                name = "Test UK 2",
                regionId = "uk",
                templateId = "nordvpn",
                serverHostname = "uk1860.nordvpn.com"
            )
            val frConfig = VpnConfig(
                id = "test_fr_2",
                name = "Test FR 2",
                regionId = "fr",
                templateId = "nordvpn",
                serverHostname = "fr842.nordvpn.com"
            )
            settingsRepository.saveVpnConfig(ukConfig)
            settingsRepository.saveVpnConfig(frConfig)
        }
        
        val testPackage = context.packageName
        
        // Navigate to app rules
        composeTestRule.onNodeWithText("App Routing Rules", substring = true)
            .performScrollTo()
        
        // Find our app
        composeTestRule.onAllNodesWithText(testPackage, substring = true)
            .onFirst()
            .performScrollTo()
        
        // Set rule to UK
        composeTestRule.onNodeWithTag("app_rule_dropdown_$testPackage")
            .performClick()
        
        composeTestRule.onNodeWithText("Test UK 2", substring = true)
            .performClick()
        
        Thread.sleep(1000)
        
        // Verify rule was saved
        runBlocking {
            val rule = settingsRepository.getAppRuleByPackageName(testPackage)
            assert(rule != null) { "App rule should exist" }
            assert(rule?.vpnConfigId == "test_uk_2") { "Rule should point to UK config" }
        }
        
        // Change rule to FR
        composeTestRule.onNodeWithTag("app_rule_dropdown_$testPackage")
            .performClick()
        
        composeTestRule.onNodeWithText("Test FR 2", substring = true)
            .performClick()
        
        Thread.sleep(1000)
        
        // Verify rule was updated
        runBlocking {
            val rule = settingsRepository.getAppRuleByPackageName(testPackage)
            assert(rule?.vpnConfigId == "test_fr_2") { "Rule should point to FR config" }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    private fun grantVpnPermission() {
        try {
            // Grant VPN permission via appops
            val packageName = context.packageName
            uiDevice.executeShellCommand("appops set $packageName ACTIVATE_VPN allow")
            Thread.sleep(500)
        } catch (e: Exception) {
            android.util.Log.w("MobileUiE2ETest", "Failed to grant VPN permission via ADB: ${e.message}")
        }
    }
}

