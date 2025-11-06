package com.multiregionvpn

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.multiregionvpn.data.database.AppRule
import com.multiregionvpn.data.database.VpnConfig
import com.multiregionvpn.data.repository.SettingsRepository
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
 * E2E tests for WireGuard multi-tunnel routing using Docker test environment.
 * 
 * Setup:
 * 1. Docker WireGuard servers running (UK + FR)
 * 2. Client configs in app/src/androidTest/assets/
 * 3. Mock web servers returning country-specific responses
 * 
 * See docker-wireguard-test/README.md for setup instructions.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class WireGuardE2ETest {
    
    @get:Rule
    var hiltRule = HiltAndroidRule(this)
    
    @Inject
    lateinit var settingsRepository: SettingsRepository
    
    private lateinit var context: Context
    
    @Before
    fun setup() {
        hiltRule.inject()
        context = ApplicationProvider.getApplicationContext()
        
        // Clean database before each test
        runBlocking {
            settingsRepository.getAllVpnConfigs().first().forEach { config ->
                settingsRepository.deleteVpnConfig(config.id)
            }
            settingsRepository.getAllAppRules().first().forEach { rule ->
                settingsRepository.deleteAppRule(rule.packageName)
            }
        }
    }
    
    @After
    fun teardown() {
        println("🧹 Cleaning up WireGuard test...")
        // Clean up
        runBlocking {
            settingsRepository.getAllVpnConfigs().first().forEach { config ->
                settingsRepository.deleteVpnConfig(config.id)
            }
            settingsRepository.getAllAppRules().first().forEach { rule ->
                settingsRepository.deleteAppRule(rule.packageName)
            }
        }
        println("✅ WireGuard test cleanup complete")
    }
    
    /**
     * Test 1: Load WireGuard UK config and verify it can be parsed
     */
    @Test
    fun test_loadWireGuardUKConfig() = runBlocking {
        println("🧪 Test: Load WireGuard UK config")
        
        // Load config from assets
        val ukConfig = context.assets.open("wireguard_uk.conf").bufferedReader().use { it.readText() }
        
        println("📋 UK Config loaded (${ukConfig.length} bytes)")
        println("First 100 chars: ${ukConfig.take(100)}")
        
        // Verify config starts with [Interface]
        assert(ukConfig.trimStart().startsWith("[Interface]")) {
            "WireGuard config should start with [Interface]"
        }
        
        println("✅ UK Config is valid WireGuard format")
    }
    
    /**
     * Test 2: Load WireGuard FR config and verify it can be parsed
     */
    @Test
    fun test_loadWireGuardFRConfig() = runBlocking {
        println("🧪 Test: Load WireGuard FR config")
        
        // Load config from assets
        val frConfig = context.assets.open("wireguard_fr.conf").bufferedReader().use { it.readText() }
        
        println("📋 FR Config loaded (${frConfig.length} bytes)")
        println("First 100 chars: ${frConfig.take(100)}")
        
        // Verify config starts with [Interface]
        assert(frConfig.trimStart().startsWith("[Interface]")) {
            "WireGuard config should start with [Interface]"
        }
        
        println("✅ FR Config is valid WireGuard format")
    }
    
    /**
     * Test 3: Create VPN configs in database with WireGuard configs
     */
    @Test
    fun test_createWireGuardVpnConfigs() = runBlocking {
        println("🧪 Test: Create WireGuard VPN configs in database")
        
        // Load configs from assets
        val ukConfig = context.assets.open("wireguard_uk.conf").bufferedReader().use { it.readText() }
        val frConfig = context.assets.open("wireguard_fr.conf").bufferedReader().use { it.readText() }
        
        // Create UK VPN config
        val ukVpnConfig = VpnConfig(
            id = "wireguard_uk",
            name = "WireGuard UK (Docker)",
            regionId = "gb",
            templateId = "wireguard_docker",
            serverHostname = "192.168.68.60"  // Docker host IP
        )
        
        // Create FR VPN config  
        val frVpnConfig = VpnConfig(
            id = "wireguard_fr",
            name = "WireGuard France (Docker)",
            regionId = "fr",
            templateId = "wireguard_docker",
            serverHostname = "192.168.68.60"  // Docker host IP
        )
        
        // Save to database
        settingsRepository.saveVpnConfig(ukVpnConfig)
        settingsRepository.saveVpnConfig(frVpnConfig)
        
        // Verify they were saved
        val savedUK = settingsRepository.getVpnConfigById("wireguard_uk")
        val savedFR = settingsRepository.getVpnConfigById("wireguard_fr")
        
        assert(savedUK != null) { "UK config should be saved" }
        assert(savedFR != null) { "FR config should be saved" }
        
        println("✅ WireGuard VPN configs saved successfully")
        println("   UK: ${savedUK?.name}")
        println("   FR: ${savedFR?.name}")
    }
    
    /**
     * Test 4: Create app rules with WireGuard VPN configs
     */
    @Test
    fun test_createAppRulesWithWireGuard() = runBlocking {
        println("🧪 Test: Create app rules with WireGuard VPN configs")
        
        // Load and save configs
        settingsRepository.saveVpnConfig(VpnConfig(
            id = "wireguard_uk",
            name = "WireGuard UK",
            regionId = "gb",
            templateId = "wireguard_docker",
            serverHostname = "192.168.68.60"
        ))
        
        settingsRepository.saveVpnConfig(VpnConfig(
            id = "wireguard_fr",
            name = "WireGuard France",
            regionId = "fr",
            templateId = "wireguard_docker",
            serverHostname = "192.168.68.60"
        ))
        
        // Create app rules
        settingsRepository.saveAppRule(AppRule(
            packageName = "com.android.chrome",
            vpnConfigId = "wireguard_uk"
        ))
        
        settingsRepository.saveAppRule(AppRule(
            packageName = "com.android.vending",
            vpnConfigId = "wireguard_fr"
        ))
        
        // Verify
        val rules = settingsRepository.getAllAppRules().first()
        assert(rules.size == 2) { "Should have 2 app rules" }
        
        println("✅ App rules created successfully")
        println("   Chrome -> WireGuard UK")
        println("   Play Store -> WireGuard FR")
    }
}

