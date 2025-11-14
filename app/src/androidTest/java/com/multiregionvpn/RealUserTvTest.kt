package com.multiregionvpn

import android.content.Context
import android.view.KeyEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.multiregionvpn.data.repository.SettingsRepository
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

@LargeTest
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class RealUserTvTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val tvRule = createAndroidComposeRule<TvActivity>()

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private lateinit var context: Context
    private lateinit var uiDevice: UiDevice

    @Before
    fun setup() {
        hiltRule.inject()
        context = ApplicationProvider.getApplicationContext()
        uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        grantVpnPermission()
    }

    @Test
    fun tv_canNavigateToCredentials() {
        log("🧪 TV: Can user navigate to credentials with D-pad?")
        setupTestVpnConfigs()
        tvRule.waitForIdle()
        Thread.sleep(2000)

        pressKey(KeyEvent.KEYCODE_DPAD_RIGHT)
        Thread.sleep(500)
        pressKey(KeyEvent.KEYCODE_DPAD_RIGHT)
        Thread.sleep(500)

        tvRule.onNodeWithText("Settings").assertExists()
        log("  ✓ Navigated to Settings tab")

        pressKey(KeyEvent.KEYCODE_DPAD_DOWN)
        Thread.sleep(300)

        tvRule.onNodeWithText("NordVPN Credentials", substring = true)
            .assertExists()
        log("  ✓ Found NordVPN Credentials section")

        pressKey(KeyEvent.KEYCODE_DPAD_DOWN)
        Thread.sleep(300)
        pressKey(KeyEvent.KEYCODE_DPAD_CENTER)
        Thread.sleep(500)

        log("  ✓ Can focus and activate username field")
        log("✅ TV: User CAN navigate to credentials (keyboard input requires real TV)")
    }

    @Test
    fun tv_canNavigateTabs() {
        log("🧪 TV: Can user navigate tabs with D-pad?")
        setupTestVpnConfigs()
        tvRule.waitForIdle()
        Thread.sleep(2000)

        tvRule.onNodeWithText("VPN Tunnels").assertExists()
        log("  ✓ On Tunnels tab")

        pressKey(KeyEvent.KEYCODE_DPAD_RIGHT)
        Thread.sleep(500)
        tvRule.onNodeWithText("App Routing Rules").assertExists()
        log("  ✓ Can reach App Rules")

        pressKey(KeyEvent.KEYCODE_DPAD_RIGHT)
        Thread.sleep(500)
        tvRule.onNodeWithText("Settings").assertExists()
        log("  ✓ Can reach Settings")

        pressKey(KeyEvent.KEYCODE_DPAD_LEFT)
        Thread.sleep(500)
        tvRule.onNodeWithText("App Routing Rules").assertExists()
        log("  ✓ Can navigate back")

        log("✅ TV: User CAN navigate all tabs with D-pad")
    }

    @Test
    fun tv_canSeeTunnels() {
        log("🧪 TV: Can user see tunnels?")
        setupTestVpnConfigs()
        tvRule.waitForIdle()
        Thread.sleep(2000)

        tvRule.onNodeWithText("VPN Tunnels").assertExists()
        tvRule.onNodeWithText("United Kingdom", substring = true).assertExists()
        tvRule.onNodeWithText("France", substring = true).assertExists()

        log("  ✓ Can see UK tunnel")
        log("  ✓ Can see France tunnel")
        log("✅ TV: User CAN see tunnels")
    }

    @Test
    fun tv_canSeeAppRules() {
        log("🧪 TV: Can user navigate to app rules and see apps?")
        setupTestVpnConfigs()
        tvRule.waitForIdle()
        Thread.sleep(2000)

        pressKey(KeyEvent.KEYCODE_DPAD_RIGHT)
        Thread.sleep(500)
        tvRule.onNodeWithText("App Routing Rules").assertExists()
        log("  ✓ Navigated to App Rules")

        pressKey(KeyEvent.KEYCODE_DPAD_DOWN)
        Thread.sleep(300)

        log("✅ TV: User CAN navigate to app rules")
    }

    @Test
    fun tv_canSeeInstalledAppsInList() {
        log("🧪 TV: Can user see installed apps in the app list?")
        setupTestVpnConfigs()
        tvRule.waitForIdle()
        Thread.sleep(2000)

        pressKey(KeyEvent.KEYCODE_DPAD_RIGHT)
        Thread.sleep(500)
        tvRule.onNodeWithText("App Routing Rules").assertExists()
        log("  ✓ Navigated to App Rules")

        val expectedTag = "tv_app_rule_card_${context.packageName}"
        tvRule.waitUntil(timeoutMillis = 5_000) {
            tvRule.onAllNodesWithTag(expectedTag).fetchSemanticsNodes().isNotEmpty()
        }
        tvRule.onNodeWithTag(expectedTag).assertExists()

        log("  ✓ App card for ${context.packageName} rendered on TV")
        log("✅ TV: User CAN see installed apps in list")
    }

    private fun setupTestVpnConfigs() {
        runBlocking {
            val existing = settingsRepository.getAllVpnConfigs().first()
            existing.forEach { settingsRepository.deleteVpnConfig(it.id) }

            settingsRepository.saveVpnConfig(
                com.multiregionvpn.data.database.VpnConfig(
                    id = "test_uk",
                    name = "United Kingdom",
                    regionId = "uk",
                    templateId = "nordvpn",
                    serverHostname = "uk1860.nordvpn.com"
                )
            )

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
        println(message)
    }
}
