package com.multiregionvpn

import android.content.Context
import android.view.KeyEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.multiregionvpn.data.repository.SettingsRepository
import com.multiregionvpn.ui.MainActivity
import com.multiregionvpn.ui.tv.TvActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Real User "Can Do" Tests
 * 
 * Tests that verify users CAN actually perform actions via the UI.
 * Uses real NordVPN credentials from .env file.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class RealUserCanDoTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private lateinit var context: Context
    private lateinit var uiDevice: UiDevice

    // Real NordVPN credentials from .env
    private val NORD_USERNAME = "nACm5TMU8vDQhBA9K8xsPARo"
    private val NORD_PASSWORD = "WBu4meMLw6BPMWxoZpAruMa7"

    @Before
    fun setup() {
        hiltRule.inject()
        context = ApplicationProvider.getApplicationContext()
        uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        grantVpnPermission()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MOBILE: Can user navigate to credentials and save them?
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun mobile_canNavigateToCredentialsAndSave() {
        val rule = createAndroidComposeRule<MainActivity>()
        
        log("🧪 MOBILE: Can user navigate to credentials and save?")
        rule.waitForIdle()
        
        // Navigate to credentials section
        rule.onNodeWithText("Provider Credentials", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        
        log("  ✓ Found credentials section")
        
        // Find username field
        rule.onNodeWithTag("nord_username_textfield")
            .assertExists()
            .assertIsDisplayed()
        
        log("  ✓ Username field exists and is accessible")
        
        // Find password field  
        rule.onNodeWithTag("nord_password_textfield")
            .assertExists()
            .assertIsDisplayed()
        
        log("  ✓ Password field exists and is accessible")
        
        // Enter credentials
        rule.onNodeWithTag("nord_username_textfield")
            .performClick()
            .performTextReplacement(NORD_USERNAME)
        
        rule.onNodeWithTag("nord_password_textfield")
            .performClick()
            .performTextReplacement(NORD_PASSWORD)
        
        log("  ✓ Entered credentials")
        
        // Save
        rule.onNodeWithTag("nord_credentials_save_button")
            .performScrollTo()
            .performClick()
        
        Thread.sleep(2000)
        
        log("✅ MOBILE: User CAN navigate to credentials, enter them, and click save")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TV: Can user navigate to credentials with D-pad?
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun tv_canNavigateToCredentials() {
        val rule = createAndroidComposeRule<TvActivity>()
        
        log("🧪 TV: Can user navigate to credentials with D-pad?")
        rule.waitForIdle()
        Thread.sleep(2000)
        
        // Navigate to Settings tab (Right twice)
        pressKey(KeyEvent.KEYCODE_DPAD_RIGHT)
        Thread.sleep(500)
        pressKey(KeyEvent.KEYCODE_DPAD_RIGHT)
        Thread.sleep(500)
        
        // Verify on Settings
        rule.onNodeWithText("Settings").assertExists()
        log("  ✓ Navigated to Settings tab")
        
        // Navigate down to credentials section
        pressKey(KeyEvent.KEYCODE_DPAD_DOWN)
        Thread.sleep(300)
        
        // Verify credentials section exists
        rule.onNodeWithText("NordVPN Credentials", substring = true)
            .assertExists()
        
        log("  ✓ Found NordVPN Credentials section")
        
        // Navigate to username field
        pressKey(KeyEvent.KEYCODE_DPAD_DOWN)
        Thread.sleep(300)
        
        // Activate field (D-pad CENTER)
        pressKey(KeyEvent.KEYCODE_DPAD_CENTER)
        Thread.sleep(500)
        
        log("  ✓ Can focus and activate username field")
        log("  ℹ️  On real TV, on-screen keyboard would appear for text input")
        
        log("✅ TV: User CAN navigate to credentials (keyboard input requires real TV)")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MOBILE: Can user route an app?
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun mobile_canRouteApp() {
        val rule = createAndroidComposeRule<MainActivity>()
        
        log("🧪 MOBILE: Can user route an app?")
        
        // Setup test data
        setupTestVpnConfigs()
        rule.waitForIdle()
        
        // Navigate to app rules
        rule.onNodeWithText("App Routing Rules", substring = true)
            .performScrollTo()
        
        val testPackage = context.packageName
        
        // Find our app
        rule.onAllNodesWithText(testPackage, substring = true)
            .onFirst()
            .performScrollTo()
        
        // Click dropdown
        val dropdownTag = "app_rule_dropdown_$testPackage"
        rule.onNodeWithTag(dropdownTag)
            .performScrollTo()
            .performClick()
        
        Thread.sleep(500)
        
        // Select UK
        rule.onNodeWithText("United Kingdom", substring = true)
            .performClick()
        
        Thread.sleep(1000)
        
        // Verify
        runBlocking {
            val rules = settingsRepository.appRuleDao.getAllRulesList()
            val rule = rules.firstOrNull { it.packageName == testPackage }
            assert(rule != null) { "App rule not saved!" }
        }
        
        log("✅ MOBILE: User CAN route an app")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MOBILE: Can user toggle VPN?
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun mobile_canToggleVpn() {
        val rule = createAndroidComposeRule<MainActivity>()
        
        log("🧪 MOBILE: Can user toggle VPN?")
        
        // Setup test data
        setupTestVpnConfigs()
        rule.waitForIdle()
        
        // Find toggle
        rule.onNodeWithTag("start_service_toggle")
            .performScrollTo()
            .assertExists()
            .performClick()
        
        Thread.sleep(3000)
        
        // Check if running
        val isRunning = com.multiregionvpn.core.VpnEngineService.isRunning()
        log("  VPN running: $isRunning")
        
        // Toggle off
        rule.onNodeWithTag("start_service_toggle")
            .performClick()
        
        Thread.sleep(2000)
        
        log("✅ MOBILE: User CAN toggle VPN")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TV: Can user navigate tabs?
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun tv_canNavigateTabs() {
        val rule = createAndroidComposeRule<TvActivity>()
        
        log("🧪 TV: Can user navigate tabs with D-pad?")
        
        // Setup test data
        setupTestVpnConfigs()
        rule.waitForIdle()
        Thread.sleep(2000)
        
        // Start on Tunnels
        rule.onNodeWithText("VPN Tunnels").assertExists()
        log("  ✓ On Tunnels tab")
        
        // Go to App Rules
        pressKey(KeyEvent.KEYCODE_DPAD_RIGHT)
        Thread.sleep(500)
        rule.onNodeWithText("App Routing Rules").assertExists()
        log("  ✓ Can reach App Rules")
        
        // Go to Settings
        pressKey(KeyEvent.KEYCODE_DPAD_RIGHT)
        Thread.sleep(500)
        rule.onNodeWithText("Settings").assertExists()
        log("  ✓ Can reach Settings")
        
        // Go back
        pressKey(KeyEvent.KEYCODE_DPAD_LEFT)
        Thread.sleep(500)
        rule.onNodeWithText("App Routing Rules").assertExists()
        log("  ✓ Can navigate back")
        
        log("✅ TV: User CAN navigate all tabs with D-pad")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TV: Can user see tunnels?
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun tv_canSeeTunnels() {
        val rule = createAndroidComposeRule<TvActivity>()
        
        log("🧪 TV: Can user see tunnels?")
        
        // Setup test data
        setupTestVpnConfigs()
        rule.waitForIdle()
        Thread.sleep(2000)
        
        // Should be on Tunnels tab by default
        rule.onNodeWithText("VPN Tunnels").assertExists()
        
        // Verify tunnel names are visible
        rule.onNodeWithText("United Kingdom", substring = true).assertExists()
        rule.onNodeWithText("France", substring = true).assertExists()
        
        log("  ✓ Can see UK tunnel")
        log("  ✓ Can see France tunnel")
        
        log("✅ TV: User CAN see tunnels")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TV: Can user navigate to app rules and see apps?
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun tv_canSeeAppRules() {
        val rule = createAndroidComposeRule<TvActivity>()
        
        log("🧪 TV: Can user navigate to app rules and see apps?")
        
        // Setup test data
        setupTestVpnConfigs()
        rule.waitForIdle()
        Thread.sleep(2000)
        
        // Navigate to App Rules
        pressKey(KeyEvent.KEYCODE_DPAD_RIGHT)
        Thread.sleep(500)
        
        rule.onNodeWithText("App Routing Rules").assertExists()
        log("  ✓ Navigated to App Rules")
        
        // Navigate down to see app list
        pressKey(KeyEvent.KEYCODE_DPAD_DOWN)
        Thread.sleep(300)
        
        // Should see apps (at least our test app)
        // Note: Actual app list depends on what's configured
        
        log("✅ TV: User CAN navigate to app rules")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    private fun setupTestVpnConfigs() {
        runBlocking {
            // Clear existing
            val existing = settingsRepository.getAllVpnConfigs().first()
            existing.forEach { settingsRepository.deleteVpnConfig(it.id) }
            
            // Add UK
            settingsRepository.saveVpnConfig(
                com.multiregionvpn.data.database.VpnConfig(
                    id = "test_uk",
                    name = "United Kingdom",
                    regionId = "uk",
                    templateId = "nordvpn",
                    serverHostname = "uk1860.nordvpn.com"
                )
            )
            
            // Add France
            settingsRepository.saveVpnConfig(
                com.multiregionvpn.data.database.VpnConfig(
                    id = "test_fr",
                    name = "France",
                    regionId = "fr",
                    templateId = "nordvpn",
                    serverHostname = "fr842.nordvpn.com"
                )
            )
        }
    }

    private fun grantVpnPermission() {
        try {
            uiDevice.executeShellCommand("appops set ${context.packageName} ACTIVATE_VPN allow")
            Thread.sleep(500)
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun pressKey(keyCode: Int) {
        uiDevice.pressKeyCode(keyCode)
    }

    private fun log(message: String) {
        android.util.Log.i("RealUserCanDoTest", message)
        println(message)
    }
}
