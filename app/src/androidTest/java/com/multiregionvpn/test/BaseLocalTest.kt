package com.multiregionvpn.test

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.UiDevice
import com.multiregionvpn.core.VpnEngineService
import com.multiregionvpn.data.database.AppDatabase
import com.multiregionvpn.data.repository.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith

import dagger.hilt.android.testing.HiltAndroidRule
import javax.inject.Inject

/**
 * Base class for local Docker Compose-based tests.
 * 
 * Provides common setup and teardown functionality:
 * - Docker Compose management
 * - VPN service initialization
 * - Database setup
 * - Permission handling
 */
@RunWith(AndroidJUnit4::class)
abstract class BaseLocalTest {
    
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val grantPermissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.INTERNET,
        android.Manifest.permission.ACCESS_NETWORK_STATE
    )
    
    protected lateinit var device: UiDevice
    protected lateinit var appContext: Context
    @Inject
    lateinit var settingsRepo: SettingsRepository
    protected lateinit var hostIp: String
    
    /**
     * Override this to specify which Docker Compose file to use
     */
    abstract fun getComposeFile(): DockerComposeManager.ComposeFile
    
    @Before
    open fun setup() = runBlocking {
        hiltRule.inject()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        device = UiDevice.getInstance(instrumentation)
        appContext = instrumentation.targetContext
        
        // Pre-approve VPN permission
        try {
            val appopsCommand = "appops set ${appContext.packageName} ACTIVATE_VPN allow"
            device.executeShellCommand(appopsCommand)
            println("✅ VPN permission pre-approved")
        } catch (e: Exception) {
            println("⚠️  Could not pre-approve VPN permission: ${e.message}")
        }
        
        // Get host machine IP (where Docker Compose runs)
        // For emulator: 10.0.2.2, for physical device: detect or configure
        hostIp = HostMachineManager.getHostIp()
        println("🌐 Host machine IP: $hostIp")
        
        // Get Docker Compose file
        val composeFile = getComposeFile()
        
        // Start Docker Compose
        startDockerCompose(composeFile)
        
        // Print setup instructions (Docker Compose must be running on host)
        DockerComposeManager.printSetupInstructions(composeFile)
        
        // Validate setup status (skip if we can't find project root - not critical for test execution)
        try {
            DockerComposeTestHelper.printSetupStatus(composeFile)
        } catch (e: Exception) {
            Log.w("BaseLocalTest", "Could not validate setup status", e)
            println("⚠️  Setup validation skipped (running on Android)")
        }
        
        // Note: Docker Compose must be started manually on the host machine
        // We can't start it from Android - just validate it's accessible
        println("ℹ️  Docker Compose should be running on host machine")
        println("   Services should be accessible at: $hostIp")
        
        // Wait a bit for services to be ready (if already running)
        delay(2000)
    }
    
    @After
    open fun tearDown() = runBlocking {
        // Stop Docker Compose
        stopDockerCompose(getComposeFile())

        // Stop VPN service
        try {
            val stopIntent = Intent(appContext, VpnEngineService::class.java).apply {
                action = VpnEngineService.ACTION_STOP
            }
            appContext.stopService(stopIntent)
            delay(2000)
        } catch (e: Exception) {
                println("⚠️  Error stopping VPN service: ${e.message}")
            }
            
            // Note: Docker Compose runs on host machine, not Android
            // We don't stop it here - it should be managed manually on the host
            println("ℹ️  Docker Compose runs on host machine - manage it manually")
            println("   To stop: docker-compose -f ${getComposeFile().fileName} down")
    }
    
    /**
     * Starts the VPN engine service
     */
    protected fun startVpnEngine() {
        val intent = Intent(appContext, VpnEngineService::class.java).apply {
            action = VpnEngineService.ACTION_START
        }
        appContext.startForegroundService(intent)
        runBlocking {
            delay(5000) // Wait for service to start
        }
    }
    
    /**
     * Stops the VPN engine service
     */
    protected fun stopVpnEngine() {
        val intent = Intent(appContext, VpnEngineService::class.java).apply {
            action = VpnEngineService.ACTION_STOP
        }
        appContext.stopService(intent)
        runBlocking {
            delay(2000) // Wait for service to stop
        }
    }

    private fun startDockerCompose(composeFile: DockerComposeManager.ComposeFile) {
        try {
            val projectRoot = System.getProperty("user.dir")
            val command = "docker-compose -f ${DockerComposeManager.getComposeFilePath(composeFile)} up -d"
            val process = Runtime.getRuntime().exec(command, null, java.io.File(projectRoot, "app/src/androidTest/resources/docker-compose"))
            process.waitFor()
        } catch (e: Exception) {
            println("⚠️  Could not start Docker Compose: ${e.message}")
        }
    }

    private fun stopDockerCompose(composeFile: DockerComposeManager.ComposeFile) {
        try {
            val projectRoot = System.getProperty("user.dir")
            val command = "docker-compose -f ${DockerComposeManager.getComposeFilePath(composeFile)} down"
            val process = Runtime.getRuntime().exec(command, null, java.io.File(projectRoot, "app/src/androidTest/resources/docker-compose"))
            process.waitFor()
        } catch (e: Exception) {
            println("⚠️  Could not stop Docker Compose: ${e.message}")
        }
    }
}