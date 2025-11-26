package com.multiregionvpn

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.test.uiautomator.By
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
import android.util.Log
import kotlinx.coroutines.flow.first

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
    private lateinit var device: UiDevice
    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val testPackage = InstrumentationRegistry.getInstrumentation().targetContext.packageName

    private lateinit var ukConfigId: String
    private lateinit var frConfigId: String

    @Before
    fun setUp() = runBlocking {
        hiltRule.inject()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        context = ApplicationProvider.getApplicationContext()
        device = UiDevice.getInstance(instrumentation)
        Log.d("AppRuleRemapTest", "Test package name: $testPackage")

        // Pre-approve VPN permission using appops (App Operations)
        // This is the recommended way to grant VPN permission for integration tests.
        try {
            val appopsCommand = "appops set ${context.packageName} ACTIVATE_VPN allow"
            device.executeShellCommand(appopsCommand)
            println("✅ VPN permission pre-approved via appops")
        } catch (e: Exception) {
            println("⚠️  Could not pre-approve VPN permission via appops: ${e.message}")
            // Will need to handle dialog manually if appops doesn't work
        }

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
        // Add a small delay for the app rule to be processed (already added in previous step)
        // Removed explicit delay, relying on initialRulesProcessed for synchronization
        // delay(1000) 

        startVpnService()

        // Wait for VpnEngineService to process initial rules
        runBlocking {
            Log.d("AppRuleRemapTest", "Waiting for initial rules to be processed by VpnEngineService...")
            VpnEngineService.initialRulesProcessed.first { it == true }
            Log.d("AppRuleRemapTest", "VpnEngineService has processed initial rules.")
        }

        waitForMapping(expectedTunnelSuffix = "UK", timeoutMs = 60_000L)
    }

    @After
    fun tearDown() = runBlocking {
        // CRITICAL: Always reset TestGlobalModeOverride to prevent test pollution
        try {
            VpnEngineService.setTestGlobalModeOverride(null)
        } catch (e: Exception) {
            println("⚠️  Could not reset TestGlobalModeOverride: ${e.message}")
        }
        
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

    private fun startVpnService() = runBlocking {
        // Check if VPN permission is granted
        val permissionIntent = android.net.VpnService.prepare(context)
        
        if (permissionIntent != null) {
            println("⚠️  VPN permission not granted - handling dialog...")
            // Start the service - this will trigger the permission dialog
            val intent = Intent(context, VpnEngineService::class.java).apply {
                action = VpnEngineService.ACTION_START
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            
            // Wait for and handle VPN permission dialog
            delay(1000) // Give dialog time to appear
            handleVpnPermissionDialog()
        } else {
            // Permission already granted, just start the service
            val intent = Intent(context, VpnEngineService::class.java).apply {
                action = VpnEngineService.ACTION_START
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
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

