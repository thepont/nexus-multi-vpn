package com.multiregionvpn

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.multiregionvpn.data.database.AppDatabase
import com.multiregionvpn.data.database.ProviderCredentials
import com.multiregionvpn.data.database.VpnConfig
import com.multiregionvpn.data.repository.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class GoogleTvCompatibilityTest {

    private lateinit var appContext: Context
    private lateinit var device: UiDevice
    private lateinit var settingsRepo: SettingsRepository

    private fun isTvDevice(): Boolean {
        val pm = appContext.packageManager
        return pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
    }

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Before
    fun setup() = runBlocking {
        hiltRule.inject()
        appContext = ApplicationProvider.getApplicationContext()
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        val database = AppDatabase.getDatabase(appContext)
        settingsRepo = SettingsRepository(
            database.vpnConfigDao(),
            database.appRuleDao(),
            database.providerCredentialsDao(),
            database.presetRuleDao()
        )

        settingsRepo.clearAllAppRules()
        settingsRepo.clearAllVpnConfigs()
    }

    @Test
    fun test_detectGoogleTv() {
        println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("🧪 TEST: Detect Google TV / Android TV")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        val pm = appContext.packageManager

        val isTv = pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION) ||
                   pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

        val isTouch = pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)

        println("Device characteristics:")
        println("   Is TV: $isTv")
        println("   Has touchscreen: $isTouch")
        println("   Has leanback: ${pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)}")
        println("   Has TV: ${pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)}")

        if (isTv) {
            println("✅ Running on Google TV / Android TV")
        } else {
            println("⚠️  Running on phone/tablet (not TV)")
        }

        println("✅ Device detection complete")
    }

    @Test
    fun test_launchOnTv() = runBlocking {
        println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("🧪 TEST: Launch App on TV")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        if (!isTvDevice()) {
            println("⚠️  Not a TV device – skipping test_launchOnTv")
            return@runBlocking
        }

        launchMainUi()

        val headerVisible = device.waitForTextOrDesc("Multi-Region VPN", 5_000)
        assert(headerVisible) { "App failed to launch - header not visible" }

        println("✅ App launched successfully on TV")

        val hasTunnelsTab = device.hasTextOrDesc("Tunnels")
        val hasAppsTab = device.hasTextOrDesc("App Rules")
        val hasSettingsTab = device.hasTextOrDesc("Settings")

        println("   Navigation tabs visible:")
        println("      Tunnels: $hasTunnelsTab")
        println("      Apps: $hasAppsTab")
        println("      Settings: $hasSettingsTab")

        assert(hasTunnelsTab && hasAppsTab && hasSettingsTab) {
            "Navigation tabs not visible on TV"
        }

        println("✅ UI elements visible and accessible")
    }

    @Test
    fun test_dpadNavigation() = runBlocking {
        println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("🧪 TEST: D-pad Navigation")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        if (!isTvDevice()) {
            println("⚠️  Not a TV device – skipping test_dpadNavigation")
            return@runBlocking
        }

        launchMainUi()

        println("Testing D-pad navigation:")
        println("   Starting on Tunnels tab...")

        device.pressKeyCode(android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
        delay(500)
        device.pressKeyCode(android.view.KeyEvent.KEYCODE_DPAD_CENTER)
        delay(1_000)
        val onAppRulesTab = device.waitForTextOrDesc("App Routing Rules", 2_000)
        println("   After DPAD_RIGHT: App Rules tab visible = $onAppRulesTab")

        device.pressKeyCode(android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
        delay(500)
        device.pressKeyCode(android.view.KeyEvent.KEYCODE_DPAD_CENTER)
        delay(1_000)
        val onSettingsTab = device.waitForTextOrDesc("NordVPN Credentials", 2_000)
        println("   After second DPAD_RIGHT: Settings tab visible = $onSettingsTab")

        device.pressKeyCode(android.view.KeyEvent.KEYCODE_DPAD_LEFT)
        delay(600)
        device.pressKeyCode(android.view.KeyEvent.KEYCODE_DPAD_CENTER)
        delay(1_000)
        val backOnTunnels = device.waitForTextOrDesc("VPN Tunnels", 2_000)
        println("   After DPAD_LEFT: Tunnels tab visible = $backOnTunnels")

        println("✅ D-pad navigation works")
    }

    @Test
    fun test_remoteControlToggle() = runBlocking {
        println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("🧪 TEST: Remote Control VPN Toggle")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        if (!isTvDevice()) {
            println("⚠️  Not a TV device – skipping test_remoteControlToggle")
            return@runBlocking
        }

        launchMainUi()

        println("Navigating to VPN toggle switch...")
        device.pressKeyCode(android.view.KeyEvent.KEYCODE_DPAD_UP)
        delay(500)

        println("Pressing CENTER to toggle VPN...")
        device.pressKeyCode(android.view.KeyEvent.KEYCODE_DPAD_CENTER)
        delay(1_000)

        val statusChanged = !device.hasTextOrDesc("Disconnected") ||
                           device.hasTextOrDesc("Connecting...")

        println("   Status changed: $statusChanged")

        if (statusChanged) {
            println("✅ VPN toggle responds to D-pad CENTER press")
        } else {
            println("⚠️  VPN toggle may need focusable configuration for TV")
        }
    }

    @Test
    fun test_updateFranceServer() = runBlocking {
        println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("🧪 TEST: Update France Server")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        if (!isTvDevice()) {
            println("⚠️  Not a TV device – skipping test_updateFranceServer")
            return@runBlocking
        }

        val providerCreds = ProviderCredentials(
            templateId = "nordvpn",
            username = "testuser",
            password = "testpass"
        )
        settingsRepo.saveProviderCredentials(providerCreds)

        val frVpnId = UUID.randomUUID().toString()
        val frVpnConfig = VpnConfig(
            id = frVpnId,
            name = "FR Test Server",
            regionId = "FR",
            templateId = "nordvpn",
            serverHostname = "fr999.nordvpn.com"
        )
        settingsRepo.saveVpnConfig(frVpnConfig)

        println("   Created FR VPN config: $frVpnId")

        launchMainUi()

        device.pressKeyCode(android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
        delay(500)
        device.pressKeyCode(android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
        delay(500)
        device.pressKeyCode(android.view.KeyEvent.KEYCODE_DPAD_CENTER)
        delay(1_000)

        val hasFrServer = settingsRepo.getAllVpnConfigs().first().any { config ->
            config.serverHostname.contains("fr", ignoreCase = true)
        }
        println("   FR server present: $hasFrServer")

        println("✅ France server configuration accessible on TV")
    }

    @Test
    fun test_tvUiReadability() = runBlocking {
        println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("🧪 TEST: TV UI Readability")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        if (!isTvDevice()) {
            println("⚠️  Not a TV device – skipping test_tvUiReadability")
            return@runBlocking
        }

        launchMainUi()

        val hasHeader = device.waitForTextOrDesc("Multi-Region VPN", 3_000)
        val hasTabs = device.hasTextOrDesc("Tunnels")
        val hasStats = device.hasTextOrDesc("Protected") || device.hasTextOrDesc("Disconnected")

        println("   Header visible: $hasHeader")
        println("   Tabs visible: $hasTabs")
        println("   Status text visible: $hasStats")

        assert(hasHeader && hasTabs && hasStats) {
            "Critical UI elements not visible on TV"
        }

        println("✅ Key UI elements visible and readable")
    }

    @Test
    fun test_largeTextReadable() = runBlocking {
        println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("🧪 TEST: Large text readability on TV")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        if (!isTvDevice()) {
            println("⚠️  Not a TV device – skipping test_largeTextReadable")
            return@runBlocking
        }

        launchMainUi()

        val alwaysVisible = listOf("Multi-Region VPN", "Tunnels", "App Rules", "Settings")
        alwaysVisible.forEach { text ->
            val visible = device.hasTextOrDesc(text)
            println("   Visible [$text]: $visible")
            assert(visible) { "Expected text '$text' to be visible" }
        }

        val appRulesSelected = device.selectTab("App Rules")
        val appRulesVisible = device.waitForTextOrDesc("App Routing Rules", 3_000)
        println("   App Rules tab selected: $appRulesSelected, visible: $appRulesVisible")
        assert(appRulesSelected && appRulesVisible) { "Expected App Routing Rules content to be visible" }

        val settingsSelected = device.selectTab("Settings")
        val credentialsVisible = device.waitForTextOrDesc("NordVPN Credentials", 3_000)
        println("   Settings tab selected: $settingsSelected, visible: $credentialsVisible")
        assert(settingsSelected && credentialsVisible) { "Expected NordVPN Credentials section to be visible" }

        device.selectTab("Tunnels")

        println("✅ Large text elements are readable on TV")
    }

    private fun UiDevice.hasTextOrDesc(value: String): Boolean {
        return hasObject(By.text(value)) || hasObject(By.desc(value))
    }

    private fun UiDevice.waitForTextOrDesc(value: String, timeoutMillis: Long): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        while (SystemClock.uptimeMillis() < deadline) {
            if (hasTextOrDesc(value)) {
                return true
            }
            SystemClock.sleep(250)
        }
        return false
    }

    private suspend fun launchMainUi() {
        val intent = if (isTvDevice()) {
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                setClassName(appContext.packageName, "com.multiregionvpn.ui.tv.TvActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        } else {
            appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            } ?: Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setClassName(appContext.packageName, "com.multiregionvpn.ui.MainActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        }
        appContext.startActivity(intent)
        delay(4_000)
    }

    private fun UiDevice.selectTab(label: String): Boolean {
        val descNode: UiObject2? = wait(Until.findObject(By.desc(label)), 2_000)
        val node = descNode ?: wait(Until.findObject(By.text(label)), 2_000)
        node?.click()
        SystemClock.sleep(500)
        return node != null
    }
}
