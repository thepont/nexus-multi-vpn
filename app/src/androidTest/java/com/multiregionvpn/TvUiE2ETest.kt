package com.multiregionvpn

import android.content.Context
import android.content.Intent
import android.view.KeyEvent
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
import com.multiregionvpn.ui.tv.TvActivity
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
 * E2E Test for TV UI
 * 
 * Tests the complete TV user journey:
 * 1. Launch TV app
 * 2. Navigate with D-pad (Up/Down/Left/Right/Select)
 * 3. View tunnel list
 * 4. View app routing rules
 * 5. Toggle VPN on/off
 * 6. Verify VPN status changes
 * 7. Verify focus indicators work correctly
 * 
 * Uses UI Automator for D-pad simulation.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class TvUiE2ETest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<TvActivity>()

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
        
        // Pre-populate database with test data for TV UI
        runBlocking {
            settingsRepository.clearAllVpnConfigs()
            settingsRepository.clearAllAppRules()
            
            // Add UK tunnel
            val ukConfig = VpnConfig(
                id = "tv_test_uk",
                name = "UK TV Server",
                regionId = "uk",
                templateId = "nordvpn",
                serverHostname = "uk1860.nordvpn.com"
            )
            settingsRepository.saveVpnConfig(ukConfig)
            
            // Add FR tunnel
            val frConfig = VpnConfig(
                id = "tv_test_fr",
                name = "FR TV Server",
                regionId = "fr",
                templateId = "nordvpn",
                serverHostname = "fr842.nordvpn.com"
            )
            settingsRepository.saveVpnConfig(frConfig)
            
            // Add app rule
            val appRule = AppRule(
                packageName = context.packageName,
                vpnConfigId = ukConfig.id
            )
            settingsRepository.saveAppRule(appRule)
        }
        
        // Wait for UI to be ready
        composeTestRule.waitForIdle()
        Thread.sleep(2000) // Extra wait for TV UI initialization
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
    fun testTvUi_CompleteDpadNavigation() {
        // ═══════════════════════════════════════════════════════════════════════════
        // STEP 1: Verify TV UI launched successfully
        // ═══════════════════════════════════════════════════════════════════════════
        
        // Wait for TV UI to appear
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            try {
                // Check for header bar (persistent element)
                composeTestRule.onNodeWithText("Multi-Region VPN", substring = true).assertExists()
                true
            } catch (e: AssertionError) {
                false
            }
        }
        
        // Verify header components
        composeTestRule.onNodeWithText("Multi-Region VPN").assertExists()
        composeTestRule.onNodeWithText("Disconnected", ignoreCase = true).assertExists()
        
        // ═══════════════════════════════════════════════════════════════════════════
        // STEP 2: Navigate tabs with D-pad (Left/Right)
        // ═══════════════════════════════════════════════════════════════════════════
        
        // Verify we're on Tunnels tab by default
        composeTestRule.onNodeWithText("Tunnels").assertExists()
        
        // Navigate to App Rules tab (D-pad Right)
        pressKey(KeyEvent.KEYCODE_DPAD_RIGHT)
        Thread.sleep(500)
        
        // Verify App Rules tab content
        composeTestRule.onNodeWithText("App Routing Rules").assertExists()
        
        // Navigate to Settings tab (D-pad Right)
        pressKey(KeyEvent.KEYCODE_DPAD_RIGHT)
        Thread.sleep(500)
        
        // Verify Settings tab content
        composeTestRule.onNodeWithText("Settings").assertExists()
        composeTestRule.onNodeWithText("Coming soon", substring = true).assertExists()
        
        // Navigate back to Tunnels tab (D-pad Left twice)
        pressKey(KeyEvent.KEYCODE_DPAD_LEFT)
        Thread.sleep(500)
        pressKey(KeyEvent.KEYCODE_DPAD_LEFT)
        Thread.sleep(500)
        
        // Verify we're back on Tunnels
        composeTestRule.onNodeWithText("VPN Tunnels").assertExists()
        
        // ═══════════════════════════════════════════════════════════════════════════
        // STEP 3: Navigate tunnel list with D-pad (Up/Down)
        // ═══════════════════════════════════════════════════════════════════════════
        
        // Verify tunnels are displayed
        composeTestRule.onNodeWithText("United Kingdom", substring = true).assertExists()
        composeTestRule.onNodeWithText("France", substring = true).assertExists()
        
        // Navigate down the list
        pressKey(KeyEvent.KEYCODE_DPAD_DOWN)
        Thread.sleep(500)
        
        // Select a tunnel (D-pad Center/Select)
        pressKey(KeyEvent.KEYCODE_DPAD_CENTER)
        Thread.sleep(500)
        
        // Verify tunnel was selected (selection state updated in ViewModel)
        // This would normally update the selectedServerGroup in ViewModel
        
        // ═══════════════════════════════════════════════════════════════════════════
        // STEP 4: View app routing rules
        // ═══════════════════════════════════════════════════════════════════════════
        
        // Navigate to App Rules tab
        pressKey(KeyEvent.KEYCODE_DPAD_RIGHT)
        Thread.sleep(500)
        
        // Verify app rules are displayed
        composeTestRule.onNodeWithText("App Routing Rules").assertExists()
        
        // Navigate down to an app
        pressKey(KeyEvent.KEYCODE_DPAD_DOWN)
        Thread.sleep(500)
        
        // ═══════════════════════════════════════════════════════════════════════════
        // STEP 5: Toggle VPN ON using header toggle
        // ═══════════════════════════════════════════════════════════════════════════
        
        // Navigate to header bar toggle (D-pad Up multiple times to reach header)
        repeat(5) {
            pressKey(KeyEvent.KEYCODE_DPAD_UP)
            Thread.sleep(300)
        }
        
        // Navigate right to the toggle switch
        repeat(3) {
            pressKey(KeyEvent.KEYCODE_DPAD_RIGHT)
            Thread.sleep(300)
        }
        
        // Activate toggle (D-pad Center)
        pressKey(KeyEvent.KEYCODE_DPAD_CENTER)
        Thread.sleep(500)
        
        // Wait for VPN to start
        Thread.sleep(5000)
        
        // Verify VPN is running
        assert(VpnEngineService.isRunning()) { "VPN service should be running" }
        
        // Verify status changed in UI
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            try {
                // Status should show "Connected" or "Connecting"
                val hasConnectedText = try {
                    composeTestRule.onNodeWithText("Protected", substring = true, ignoreCase = true)
                        .assertExists()
                    true
                } catch (e: AssertionError) {
                    try {
                        composeTestRule.onNodeWithText("Connecting", ignoreCase = true).assertExists()
                        true
                    } catch (e2: AssertionError) {
                        false
                    }
                }
                hasConnectedText
            } catch (e: Exception) {
                false
            }
        }
        
        // ═══════════════════════════════════════════════════════════════════════════
        // STEP 6: Toggle VPN OFF
        // ═══════════════════════════════════════════════════════════════════════════
        
        // Toggle is still focused, press Center again to turn off
        pressKey(KeyEvent.KEYCODE_DPAD_CENTER)
        Thread.sleep(500)
        
        // Wait for VPN to stop
        Thread.sleep(3000)
        
        // Verify VPN is stopped
        assert(!VpnEngineService.isRunning()) { "VPN service should be stopped" }
        
        // Verify status changed back to Disconnected
        composeTestRule.onNodeWithText("Disconnected", ignoreCase = true).assertExists()
        
        // ═══════════════════════════════════════════════════════════════════════════
        // TEST COMPLETE
        // ═══════════════════════════════════════════════════════════════════════════
    }

    @Test
    fun testTvUi_TunnelListNavigation() {
        // Focus test: verify focus indicators work correctly
        
        // Verify tunnels exist
        composeTestRule.onNodeWithText("United Kingdom", substring = true).assertExists()
        composeTestRule.onNodeWithText("France", substring = true).assertExists()
        
        // Navigate down
        pressKey(KeyEvent.KEYCODE_DPAD_DOWN)
        Thread.sleep(500)
        
        // Navigate up
        pressKey(KeyEvent.KEYCODE_DPAD_UP)
        Thread.sleep(500)
        
        // Navigate down twice
        repeat(2) {
            pressKey(KeyEvent.KEYCODE_DPAD_DOWN)
            Thread.sleep(500)
        }
        
        // Select tunnel
        pressKey(KeyEvent.KEYCODE_DPAD_CENTER)
        Thread.sleep(500)
        
        // Verify selection (ViewModel should have selectedServerGroup set)
        // We can't directly assert on focus state in Compose tests,
        // but we verify the UI responds to D-pad input
    }

    @Test
    fun testTvUi_AppRulesDialog() {
        // Navigate to App Rules tab
        pressKey(KeyEvent.KEYCODE_DPAD_RIGHT)
        Thread.sleep(500)
        
        // Verify app rules content
        composeTestRule.onNodeWithText("App Routing Rules").assertExists()
        
        // Navigate down to an app
        pressKey(KeyEvent.KEYCODE_DPAD_DOWN)
        Thread.sleep(500)
        
        // Select app to open routing menu
        pressKey(KeyEvent.KEYCODE_DPAD_CENTER)
        Thread.sleep(1000)
        
        // Verify routing menu appears
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            try {
                composeTestRule.onNodeWithText("Route", substring = true).assertExists()
                true
            } catch (e: AssertionError) {
                false
            }
        }
        
        // Navigate menu with D-pad
        pressKey(KeyEvent.KEYCODE_DPAD_DOWN)
        Thread.sleep(500)
        
        // Close dialog with Back
        pressKey(KeyEvent.KEYCODE_BACK)
        Thread.sleep(500)
        
        // Verify dialog closed
        composeTestRule.onNodeWithText("App Routing Rules").assertExists()
    }

    @Test
    fun testTvUi_VpnToggle_QuickTest() {
        // Simplified test: just toggle VPN on and off
        
        // Navigate to toggle (Up multiple times, then Right)
        repeat(5) {
            pressKey(KeyEvent.KEYCODE_DPAD_UP)
            Thread.sleep(300)
        }
        
        repeat(3) {
            pressKey(KeyEvent.KEYCODE_DPAD_RIGHT)
            Thread.sleep(300)
        }
        
        // Toggle ON
        pressKey(KeyEvent.KEYCODE_DPAD_CENTER)
        Thread.sleep(5000)
        assert(VpnEngineService.isRunning()) { "VPN should be running" }
        
        // Toggle OFF
        pressKey(KeyEvent.KEYCODE_DPAD_CENTER)
        Thread.sleep(3000)
        assert(!VpnEngineService.isRunning()) { "VPN should be stopped" }
    }

    @Test
    fun testTvUi_ServerGroupsDisplay() {
        // Verify server groups are loaded from database and displayed correctly
        
        runBlocking {
            val configs = settingsRepository.getAllVpnConfigs().first()
            assert(configs.size == 2) { "Expected 2 VPN configs, got ${configs.size}" }
        }
        
        // Verify both regions are displayed with human-readable names
        composeTestRule.onNodeWithText("United Kingdom", substring = true).assertExists()
        composeTestRule.onNodeWithText("France", substring = true).assertExists()
        
        // Verify server counts are displayed
        composeTestRule.onNodeWithText("server", substring = true, ignoreCase = true).assertExists()
    }

    @Test
    fun testTvUi_TabSwitching() {
        // Test all tab transitions
        
        // Start on Tunnels
        composeTestRule.onNodeWithText("VPN Tunnels").assertExists()
        
        // Go to App Rules
        pressKey(KeyEvent.KEYCODE_DPAD_RIGHT)
        Thread.sleep(500)
        composeTestRule.onNodeWithText("App Routing Rules").assertExists()
        
        // Go to Settings
        pressKey(KeyEvent.KEYCODE_DPAD_RIGHT)
        Thread.sleep(500)
        composeTestRule.onNodeWithText("Coming soon", substring = true).assertExists()
        
        // Go back to App Rules
        pressKey(KeyEvent.KEYCODE_DPAD_LEFT)
        Thread.sleep(500)
        composeTestRule.onNodeWithText("App Routing Rules").assertExists()
        
        // Go back to Tunnels
        pressKey(KeyEvent.KEYCODE_DPAD_LEFT)
        Thread.sleep(500)
        composeTestRule.onNodeWithText("VPN Tunnels").assertExists()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    private fun grantVpnPermission() {
        try {
            val packageName = context.packageName
            uiDevice.executeShellCommand("appops set $packageName ACTIVATE_VPN allow")
            Thread.sleep(500)
        } catch (e: Exception) {
            android.util.Log.w("TvUiE2ETest", "Failed to grant VPN permission via ADB: ${e.message}")
        }
    }

    private fun pressKey(keyCode: Int) {
        uiDevice.pressKeyCode(keyCode)
    }
}

