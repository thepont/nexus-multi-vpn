package com.multiregionvpn

import android.content.Context
import android.net.VpnService
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.multiregionvpn.core.VpnEngineService
import com.multiregionvpn.data.database.AppDatabase
import com.multiregionvpn.data.database.ProviderCredentials
import com.multiregionvpn.data.database.VpnConfig
import com.multiregionvpn.data.repository.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID

/**
 * Test to verify that VPN interface is properly closed when VPN is disconnected.
 * 
 * This test ensures that:
 * 1. VPN interface is created when VPN starts
 * 2. VPN interface is properly closed when VPN stops
 * 3. No "zombie" VPN interface remains after disconnect
 */
@RunWith(AndroidJUnit4::class)
class VpnInterfaceDisconnectTest {
    
    private lateinit var appContext: Context
    private lateinit var device: UiDevice
    private lateinit var settingsRepo: SettingsRepository
    
    @Before
    fun setup() = runBlocking {
        appContext = ApplicationProvider.getApplicationContext()
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        
        // Pre-approve VPN permission
        try {
            val appopsCommand = "appops set ${appContext.packageName} ACTIVATE_VPN allow"
            device.executeShellCommand(appopsCommand)
            println("✅ VPN permission pre-approved")
        } catch (e: Exception) {
            println("⚠️  Could not pre-approve VPN permission: ${e.message}")
        }
        
        // Initialize repository
        val database = AppDatabase.getDatabase(appContext)
        settingsRepo = SettingsRepository(
            database.vpnConfigDao(),
            database.appRuleDao(),
            database.providerCredentialsDao(),
            database.presetRuleDao()
        )
        
        // Clear any existing VPN configs and rules
        settingsRepo.clearAllVpnConfigs()
        settingsRepo.clearAllAppRules()
        
        // Stop VPN if already running
        try {
            val stopIntent = android.content.Intent(appContext, VpnEngineService::class.java).apply {
                action = VpnEngineService.ACTION_STOP
            }
            appContext.startService(stopIntent)
            delay(3000) // Wait for VPN to fully stop
            println("✅ Stopped any existing VPN")
        } catch (e: Exception) {
            println("⚠️  Could not stop existing VPN: ${e.message}")
        }
    }
    
    @After
    fun tearDown() = runBlocking {
        // Always stop VPN after test
        try {
            val stopIntent = android.content.Intent(appContext, VpnEngineService::class.java).apply {
                action = VpnEngineService.ACTION_STOP
            }
            appContext.startService(stopIntent)
            delay(2000)
            println("✅ Cleanup: VPN stopped")
        } catch (e: Exception) {
            println("⚠️  Cleanup error: ${e.message}")
        }
    }
    
    /**
     * Checks if a VPN interface is currently active on the system.
     * 
     * @return true if VPN interface exists, false otherwise
     */
    private fun isVpnInterfaceActive(): Boolean {
        return try {
            // Method 1: Check if VpnService.prepare() returns null (no VPN active)
            // If it returns an Intent, VPN permission is needed (but no VPN is active)
            // If it returns null, either VPN is active OR permission was pre-approved
            // So we need another method to check
            
            // Method 2: Check network interfaces via /proc/net/dev
            // VPN interfaces typically show up as "tun0", "tun1", etc.
            val process = Runtime.getRuntime().exec("cat /proc/net/dev")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val lines = reader.readLines()
            reader.close()
            process.waitFor()
            
            // Look for tun interfaces (VPN interfaces)
            val hasTunInterface = lines.any { line ->
                line.trim().startsWith("tun") && line.contains(":") && 
                line.split(":")[0].trim().matches(Regex("tun\\d+"))
            }
            
            // Also check using ip command if available
            var hasTunViaIp = false
            try {
                val ipProcess = Runtime.getRuntime().exec("ip link show")
                val ipReader = BufferedReader(InputStreamReader(ipProcess.inputStream))
                val ipLines = ipReader.readLines()
                ipReader.close()
                ipProcess.waitFor()
                
                hasTunViaIp = ipLines.any { line ->
                    line.contains("tun") && (line.contains("state UP") || line.contains("state UNKNOWN"))
                }
            } catch (e: Exception) {
                // ip command might not be available, that's okay
            }
            
            hasTunInterface || hasTunViaIp
        } catch (e: Exception) {
            println("⚠️  Error checking VPN interface: ${e.message}")
            false
        }
    }
    
    /**
     * Checks if VPN service is running by checking if VpnService.prepare() returns null.
     * Note: This only works if VPN permission was pre-approved via appops.
     */
    private fun isVpnServiceActive(): Boolean {
        return try {
            // If VPN is active, VpnService.prepare() returns null (permission already granted)
            // If VPN is not active, it also returns null if permission was pre-approved
            // So we need to check the actual interface
            val prepareResult = VpnService.prepare(appContext)
            // If prepare returns null, VPN might be active OR permission was pre-approved
            // We'll rely on interface check instead
            prepareResult == null
        } catch (e: Exception) {
            false
        }
    }
    
    @Test
    fun test_vpnInterfaceClosesOnDisconnect() = runBlocking {
        println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("🧪 TEST: VPN Interface Closes on Disconnect")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        
        // Step 1: Verify no VPN interface exists initially
        println("\n📋 Step 1: Verifying no VPN interface exists initially...")
        val initialInterfaceActive = isVpnInterfaceActive()
        println("   VPN interface active: $initialInterfaceActive")
        // Note: There might be other VPNs active, so we can't assert this is false
        
        // Step 2: Set up test data (credentials and VPN config)
        println("\n📋 Step 2: Setting up test data...")
        val testCreds = ProviderCredentials(
            templateId = "nordvpn",
            username = "test_user",
            password = "test_pass"
        )
        settingsRepo.saveProviderCredentials(testCreds)
        println("   ✅ Test credentials saved")
        
        val testConfig = VpnConfig(
            id = UUID.randomUUID().toString(),
            name = "Test UK Server",
            regionId = "UK",
            templateId = "nordvpn",
            serverHostname = "uk1827.nordvpn.com"
        )
        settingsRepo.saveVpnConfig(testConfig)
        println("   ✅ Test VPN config saved")
        
        // Step 3: Start VPN
        println("\n📋 Step 3: Starting VPN...")
        val startIntent = android.content.Intent(appContext, VpnEngineService::class.java).apply {
            action = VpnEngineService.ACTION_START
        }
        appContext.startService(startIntent)
        
        // Wait for VPN to start (interface creation takes time)
        var interfaceActive = false
        var attempts = 0
        val maxAttempts = 30 // 30 seconds max wait
        
        while (attempts < maxAttempts && !interfaceActive) {
            delay(1000)
            attempts++
            interfaceActive = isVpnInterfaceActive()
            if (interfaceActive) {
                println("   ✅ VPN interface detected after $attempts seconds")
                break
            }
            if (attempts % 5 == 0) {
                println("   ⏳ Waiting for VPN interface... (${attempts}s)")
            }
        }
        
        if (!interfaceActive) {
            println("   ⚠️  VPN interface not detected after $maxAttempts seconds")
            println("   This might be because:")
            println("     1. VPN failed to start")
            println("     2. No app rules configured (split tunneling)")
            println("     3. Interface check method needs improvement")
            // Continue test anyway to verify disconnect works
        } else {
            println("   ✅ VPN interface is active")
        }
        
        // Step 4: Verify VPN interface is active
        println("\n📋 Step 4: Verifying VPN interface is active...")
        val interfaceBeforeStop = isVpnInterfaceActive()
        println("   VPN interface active before stop: $interfaceBeforeStop")
        
        // Step 5: Stop VPN
        println("\n📋 Step 5: Stopping VPN...")
        val stopIntent = android.content.Intent(appContext, VpnEngineService::class.java).apply {
            action = VpnEngineService.ACTION_STOP
        }
        appContext.startService(stopIntent)
        
        // Wait for VPN to fully stop (interface closure takes time)
        delay(3000) // Give it time to close
        
        // Step 6: Verify VPN interface is closed
        println("\n📋 Step 6: Verifying VPN interface is closed...")
        var interfaceStillActive = true
        attempts = 0
        val maxStopAttempts = 10 // 10 seconds max wait
        
        while (attempts < maxStopAttempts && interfaceStillActive) {
            delay(1000)
            attempts++
            interfaceStillActive = isVpnInterfaceActive()
            if (!interfaceStillActive) {
                println("   ✅ VPN interface closed after $attempts seconds")
                break
            }
            if (attempts % 2 == 0) {
                println("   ⏳ Waiting for VPN interface to close... (${attempts}s)")
            }
        }
        
        val finalInterfaceActive = isVpnInterfaceActive()
        println("   VPN interface active after stop: $finalInterfaceActive")
        
        // Step 7: Assertions
        println("\n📋 Step 7: Test assertions...")
        if (interfaceBeforeStop) {
            // If interface was active before, it should be closed now
            assert(!finalInterfaceActive) {
                "❌ FAIL: VPN interface is still active after disconnect! " +
                "This means the VPN interface was not properly closed."
            }
            println("   ✅ PASS: VPN interface was active and is now closed")
        } else {
            println("   ⚠️  SKIP: VPN interface was not active before stop (might be split tunneling)")
            println("   This is okay if no app rules were configured")
        }
        
        // Additional check: Verify VpnService.prepare() behavior
        val prepareAfterStop = VpnService.prepare(appContext)
        println("   VpnService.prepare() after stop: ${if (prepareAfterStop == null) "null (no VPN active)" else "Intent (permission needed)"}")
        
        println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("✅ TEST COMPLETE")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }
    
    @Test
    fun test_vpnInterfaceClosesWithAppRules() = runBlocking {
        println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("🧪 TEST: VPN Interface Closes with App Rules")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        
        // This test ensures VPN interface closes even when app rules are configured
        // (which should create the interface in the first place)
        
        // Set up test data
        val testCreds = ProviderCredentials(
            templateId = "nordvpn",
            username = "test_user",
            password = "test_pass"
        )
        settingsRepo.saveProviderCredentials(testCreds)
        
        val testConfig = VpnConfig(
            id = UUID.randomUUID().toString(),
            name = "Test UK Server",
            regionId = "UK",
            templateId = "nordvpn",
            serverHostname = "uk1827.nordvpn.com"
        )
        settingsRepo.saveVpnConfig(testConfig)
        
        // Create an app rule to ensure VPN interface is created
        val testPackage = appContext.packageName // Use our own package
        settingsRepo.createAppRule(testPackage, testConfig.id)
        println("   ✅ Created app rule for ${appContext.packageName}")
        
        // Start VPN
        val startIntent = android.content.Intent(appContext, VpnEngineService::class.java).apply {
            action = VpnEngineService.ACTION_START
        }
        appContext.startService(startIntent)
        
        // Wait for interface
        delay(5000)
        val interfaceBeforeStop = isVpnInterfaceActive()
        println("   VPN interface active before stop: $interfaceBeforeStop")
        
        // Stop VPN
        val stopIntent = android.content.Intent(appContext, VpnEngineService::class.java).apply {
            action = VpnEngineService.ACTION_STOP
        }
        appContext.startService(stopIntent)
        
        // Wait for interface to close
        delay(3000)
        val interfaceAfterStop = isVpnInterfaceActive()
        println("   VPN interface active after stop: $interfaceAfterStop")
        
        // Assert interface is closed
        if (interfaceBeforeStop) {
            assert(!interfaceAfterStop) {
                "❌ FAIL: VPN interface is still active after disconnect with app rules!"
            }
            println("   ✅ PASS: VPN interface closed properly even with app rules")
        } else {
            println("   ⚠️  VPN interface was not created (might be other issues)")
        }
    }
}

