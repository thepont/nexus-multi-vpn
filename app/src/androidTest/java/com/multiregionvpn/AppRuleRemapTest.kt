package com.multiregionvpn

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.multiregionvpn.core.VpnEngineService
import com.multiregionvpn.data.database.AppDatabase
import com.multiregionvpn.data.database.ProviderCredentials
import com.multiregionvpn.data.database.VpnConfig
import com.multiregionvpn.data.repository.SettingsRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * Ensures that when an app rule changes from one tunnel to another, the
 * ConnectionTracker remaps the UID to the new tunnel.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AppRuleRemapTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    private lateinit var context: Context
    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val testPackage = InstrumentationRegistry.getInstrumentation().targetContext.packageName

    private lateinit var ukConfigId: String
    private lateinit var frConfigId: String

    @Before
    fun setUp() = runBlocking {
        hiltRule.inject()
        context = ApplicationProvider.getApplicationContext()

        // Clear previous state
        settingsRepository.clearAllAppRules()
        settingsRepository.clearAllVpnConfigs()

        // Minimal credentials/configs for local-test template
        settingsRepository.saveProviderCredentials(
            ProviderCredentials(
                templateId = "local-test",
                username = "testuser",
                password = "testpass"
            )
        )

        ukConfigId = UUID.randomUUID().toString()
        frConfigId = UUID.randomUUID().toString()

        settingsRepository.saveVpnConfig(
            VpnConfig(
                id = ukConfigId,
                name = "UK Test Tunnel",
                regionId = "UK",
                templateId = "local-test",
                serverHostname = "10.0.2.2:1199"
            )
        )

        settingsRepository.saveVpnConfig(
            VpnConfig(
                id = frConfigId,
                name = "FR Test Tunnel",
                regionId = "FR",
                templateId = "local-test",
                serverHostname = "10.0.2.2:1299"
            )
        )

        settingsRepository.createAppRule(testPackage, ukConfigId)

        startVpnService()
        waitForMapping(expectedTunnelSuffix = "UK")
    }

    @After
    fun tearDown() = runBlocking {
        sendStopIntent()
        waitForServiceStopped()
        settingsRepository.clearAllAppRules()
        settingsRepository.clearAllVpnConfigs()
    }

    @Test
    fun appRuleRemapsToNewTunnel() = runBlocking {
        // Switch rule to France
        settingsRepository.updateAppRule(testPackage, frConfigId)

        // Wait until mapping reflects France
        waitForMapping(expectedTunnelSuffix = "FR")

        val snapshot = VpnEngineService.getConnectionTrackerSnapshot()
        val tunnelId = snapshot[testPackage]
        assertEquals("local-test_FR", tunnelId)
    }

    private fun startVpnService() {
        val intent = Intent(context, VpnEngineService::class.java).apply {
            action = VpnEngineService.ACTION_START
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun sendStopIntent() {
        val stopIntent = Intent(context, VpnEngineService::class.java).apply {
            action = VpnEngineService.ACTION_STOP
        }
        context.startService(stopIntent)
        // Give the service a moment to process the stop intent
        runBlocking {
            delay(500)
        }
    }

    private suspend fun waitForMapping(expectedTunnelSuffix: String, timeoutMs: Long = 10_000L) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            val snapshot = VpnEngineService.getConnectionTrackerSnapshot()
            val tunnelId = snapshot[testPackage]
            if (tunnelId != null && tunnelId.endsWith(expectedTunnelSuffix)) {
                return
            }
            delay(250)
        }
        val snapshot = VpnEngineService.getConnectionTrackerSnapshot()
        assertTrue(
            "Expected mapping to end with $expectedTunnelSuffix but got $snapshot",
            snapshot[testPackage]?.endsWith(expectedTunnelSuffix) == true
        )
    }

    private suspend fun waitForServiceStopped(timeoutMs: Long = 15_000L) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (!VpnEngineService.isRunning()) {
                println("✅ VpnEngineService stopped successfully")
                return
            }
            delay(500)
        }
        
        // If still running after timeout, try force stopping
        if (VpnEngineService.isRunning()) {
            println("⚠️  Service still running after timeout, attempting force stop...")
            try {
                val forceStopIntent = Intent(context, VpnEngineService::class.java).apply {
                    action = VpnEngineService.ACTION_STOP
                }
                context.stopService(forceStopIntent)
                delay(2000)
            } catch (e: Exception) {
                println("⚠️  Force stop failed: ${e.message}")
            }
        }
        
        // Final check - if still running, log but don't fail (service cleanup is best effort)
        if (VpnEngineService.isRunning()) {
            println("⚠️  VpnEngineService still running after cleanup attempts - this may affect subsequent tests")
            // Don't fail the test - service cleanup is best effort
            // The service will be cleaned up when the app is uninstalled or device is reset
        } else {
            println("✅ VpnEngineService stopped after force stop")
        }
    }
}

