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
import java.io.FileInputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule

/**
 * Ensures that when an app rule changes from one tunnel to another, the
 * ConnectionTracker remaps the UID to the new tunnel.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AppRuleRemapTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var settingsRepository: SettingsRepository

    private val testPackage = InstrumentationRegistry.getInstrumentation().targetContext.packageName

    private lateinit var ukConfigId: String
    private lateinit var frConfigId: String

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Before
    fun setUp() = runBlocking {
        hiltRule.inject()
        context = ApplicationProvider.getApplicationContext()
        database = AppDatabase.getDatabase(context)
        settingsRepository = SettingsRepository(
            database.vpnConfigDao(),
            database.appRuleDao(),
            database.providerCredentialsDao(),
            database.presetRuleDao()
        )

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
        
        grantVpnPermission()

        ukConfigId = UUID.randomUUID().toString()
        frConfigId = UUID.randomUUID().toString()

        settingsRepository.saveVpnConfig(
            VpnConfig(
                id = ukConfigId,
                name = "UK Test Tunnel",
                regionId = "UK",
                templateId = "local-test",
                serverHostname = "10.0.2.2:1194"
            )
        )

        settingsRepository.saveVpnConfig(
            VpnConfig(
                id = frConfigId,
                name = "FR Test Tunnel",
                regionId = "FR",
                templateId = "local-test",
                serverHostname = "10.0.2.2:1199"
            )
        )

        settingsRepository.createAppRule(testPackage, ukConfigId)

        startVpnService()
        waitForMapping(expectedTunnelSuffix = "UK")
    }

    private fun grantVpnPermission() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val command = "appops set ${context.packageName} ACTIVATE_VPN allow"
        instrumentation.uiAutomation.executeShellCommand(command).use { parcelFileDescriptor ->
            // Drain command output to ensure execution completes
            parcelFileDescriptor.fileDescriptor?.let { fd ->
                FileInputStream(fd).use { stream ->
                    stream.readBytes()
                }
            }
        }
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
                return
            }
            delay(250)
        }
        // Force-stop as a last resort to avoid leaking between tests
        VpnEngineService.getRunningInstance()?.stopSelf()
        delay(500)
    }
}

