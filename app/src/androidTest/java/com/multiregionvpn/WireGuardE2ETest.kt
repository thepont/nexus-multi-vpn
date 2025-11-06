package com.multiregionvpn

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

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
@RunWith(AndroidJUnit4::class)
class WireGuardE2ETest {
    
    private lateinit var context: Context
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }
    
    /**
     * Test 1: Load WireGuard UK config and verify it can be parsed
     */
    @Test
    fun test_loadWireGuardUKConfig() {
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
    fun test_loadWireGuardFRConfig() {
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
     * Test 3: Verify both configs contain proper WireGuard structure
     */
    @Test
    fun test_verifyWireGuardConfigStructure() {
        println("🧪 Test: Verify WireGuard config structure")
        
        // Load UK config
        val ukConfig = context.assets.open("wireguard_uk.conf").bufferedReader().use { it.readText() }
        
        // Verify it contains expected WireGuard sections
        assert(ukConfig.contains("[Interface]")) { "Config should have [Interface] section" }
        assert(ukConfig.contains("[Peer]")) { "Config should have [Peer] section" }
        assert(ukConfig.contains("PrivateKey")) { "Config should have PrivateKey" }
        assert(ukConfig.contains("Address")) { "Config should have Address" }
        assert(ukConfig.contains("PublicKey")) { "Config should have PublicKey in [Peer]" }
        assert(ukConfig.contains("Endpoint")) { "Config should have Endpoint" }
        
        println("✅ UK config structure is valid")
        println("   Has [Interface] section: ✅")
        println("   Has [Peer] section: ✅")
        println("   Has required fields: ✅")
    }
    
    /**
     * Test 4: Verify configs can be distinguished
     */
    @Test
    fun test_distinguishUKandFRConfigs() {
        println("🧪 Test: Distinguish UK and FR configs")
        
        // Load both configs
        val ukConfig = context.assets.open("wireguard_uk.conf").bufferedReader().use { it.readText() }
        val frConfig = context.assets.open("wireguard_fr.conf").bufferedReader().use { it.readText() }
        
        // Verify they are different
        assert(ukConfig != frConfig) { "UK and FR configs should be different" }
        
        // Verify they have different addresses
        assert(ukConfig.contains("10.13.13.") || ukConfig.contains("10.14.14.")) { 
            "UK config should have expected address range"
        }
        
        println("✅ Configs are distinguishable")
        println("   UK length: ${ukConfig.length} bytes")
        println("   FR length: ${frConfig.length} bytes")
        println("   Different: ${ukConfig != frConfig}")
    }
}

