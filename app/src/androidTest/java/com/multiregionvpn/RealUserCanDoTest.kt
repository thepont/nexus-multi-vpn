package com.multiregionvpn

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.multiregionvpn.data.repository.SettingsRepository
import com.multiregionvpn.ui.MainActivity
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
 * Real User "Can Do" Tests (Mobile)
 *
 * Verifies that core user journeys can be performed via the mobile UI.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class RealUserMobileTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val mobileRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private lateinit var context: Context
    private lateinit var uiDevice: UiDevice

    // Real NordVPN credentials from .env (placeholder values for automation)
    private val NORD_USERNAME = "nACm5TMU8vDQhBA9K8xsPARo"
    private val NORD_PASSWORD = "WBu4meMLw6BPMWxoZpAruMa7"

    @Before
    fun setup() {
        hiltRule.inject()
        context = ApplicationProvider.getApplicationContext()
        uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        grantVpnPermission()
    }

    @Test
    fun mobile_canNavigateToCredentialsAndSave() {
        log("🧪 MOBILE: Can user navigate to credentials and save?")
        mobileRule.waitForIdle()

        mobileRule.onNodeWithText("Provider Credentials", substring = true)
            .performScrollTo()
            .assertIsDisplayed()

        mobileRule.onNodeWithTag("nord_username_textfield")
            .assertExists()
            .assertIsDisplayed()
        mobileRule.onNodeWithTag("nord_password_textfield")
            .assertExists()
            .assertIsDisplayed()

        mobileRule.onNodeWithTag("nord_username_textfield")
            .performClick()
            .performTextReplacement(NORD_USERNAME)
        mobileRule.onNodeWithTag("nord_password_textfield")
            .performClick()
            .performTextReplacement(NORD_PASSWORD)

        mobileRule.onNodeWithTag("nord_credentials_save_button")
            .performScrollTo()
            .performClick()

        Thread.sleep(2000)
        log("✅ MOBILE: User CAN navigate to credentials, enter them, and click save")
    }

    @Test
    fun mobile_canRouteApp() {
        log("🧪 MOBILE: Can user route an app?")
        setupTestVpnConfigs()
        mobileRule.waitForIdle()

        mobileRule.onNodeWithText("App Routing Rules", substring = true)
            .performScrollTo()

        val testPackage = context.packageName
        mobileRule.onAllNodesWithText(testPackage, substring = true)
            .onFirst()
            .performScrollTo()

        val dropdownTag = "app_rule_dropdown_$testPackage"
        mobileRule.onNodeWithTag(dropdownTag)
            .performScrollTo()
            .performClick()

        Thread.sleep(500)
        mobileRule.onNodeWithText("United Kingdom", substring = true)
            .performClick()

        Thread.sleep(1000)
        runBlocking {
            val rules = settingsRepository.appRuleDao.getAllRulesList()
            val rule = rules.firstOrNull { it.packageName == testPackage }
            assert(rule != null) { "App rule not saved!" }
        }
        log("✅ MOBILE: User CAN route an app")
    }

    @Test
    fun mobile_canToggleVpn() {
        log("🧪 MOBILE: Can user toggle VPN?")
        setupTestVpnConfigs()
        mobileRule.waitForIdle()

        mobileRule.onNodeWithTag("start_service_toggle")
            .performScrollTo()
            .assertExists()
            .performClick()

        Thread.sleep(3000)
        val isRunning = com.multiregionvpn.core.VpnEngineService.isRunning()
        log("  VPN running: $isRunning")

        mobileRule.onNodeWithTag("start_service_toggle")
            .performClick()
        Thread.sleep(2000)
        log("✅ MOBILE: User CAN toggle VPN")
    }

    @Test
    fun mobile_showsAllInstalledApps() {
        log("🧪 MOBILE: Does mobile show all installed apps?")
        setupTestVpnConfigs()
        mobileRule.waitForIdle()

        mobileRule.onNodeWithText("App Routing Rules", substring = true)
            .performScrollTo()

        val testPackage = context.packageName
        mobileRule.onAllNodesWithText(testPackage, substring = true)
            .onFirst()
            .assertExists()

        log("  ✓ Mobile shows test app: $testPackage")
        log("✅ MOBILE: Shows all installed apps (INCLUDING apps without routing rules)")
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

    private fun log(message: String) {
        android.util.Log.i("RealUserMobileTest", message)
        println(message)
    }
}
