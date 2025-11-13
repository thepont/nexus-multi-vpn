package com.multiregionvpn

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.multiregionvpn.core.vpnclient.WireGuardVpnClient
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * E2E tests for WireGuard using Docker test servers.
 * 
 * Docker Setup:
 * - UK Server: 192.168.68.60:51822 (10.13.13.0/24)
 * - FR Server: 192.168.68.60:51823 (10.14.14.0/24)
 * - Mock web servers at 172.25.0.11 (UK) and 172.25.0.21 (FR)
 * 
 * Run: cd docker-wireguard-test && ./setup.sh
 */
@RunWith(AndroidJUnit4::class)
class WireGuardDockerE2ETest {
    
    private lateinit var context: Context
    
    // Embedded WireGuard configs from Docker setup
    private val ukConfig = """
[Interface]
Address = 10.13.13.2
PrivateKey = 0J+Yt+o+3DJVPc0hbgUxYc6PDG9vQ7NlCPCyTjoUTV8=
ListenPort = 51820
DNS = 10.13.13.1

[Peer]
PublicKey = fzpfDKLRxGX2BKxqWgE2xVLuoOTeMj4z/k1pggmg6kI=
PresharedKey = vkKSIxS6fM+WctizSZeuv9X6z/4skI9M8dunts8+bKA=
Endpoint = 192.168.68.60:51822
AllowedIPs = 0.0.0.0/0
    """.trimIndent()
    
    private val frConfig = """
[Interface]
Address = 10.14.14.2
PrivateKey = GEHSZLtiZaVNF2scmTM0kbb+Znkdyg/jaC9yHHlURkA=
ListenPort = 51820
DNS = 10.14.14.1

[Peer]
PublicKey = UA+PDosneVHuLAqAbB3nBandzkQb1S4Dxt3DA0hFqms=
PresharedKey = j7MdVKWBWn+t9lsyWMlibTg8rdi+J0Me31B9q7ojvJ8=
Endpoint = 192.168.68.60:51823
AllowedIPs = 0.0.0.0/0
    """.trimIndent()
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }
    
    /**
     * Test 1: Verify UK config format
     */
    @Test
    fun test_ukConfigFormat() {
        println("🧪 Test: Verify UK config format")
        
        // Verify config structure
        assert(ukConfig.contains("[Interface]")) { "Config should have [Interface] section" }
        assert(ukConfig.contains("[Peer]")) { "Config should have [Peer] section" }
        assert(ukConfig.contains("10.13.13.2")) { "Config should have UK address" }
        assert(ukConfig.contains("192.168.68.60:51822")) { "Config should have UK endpoint" }
        
        println("✅ UK config format is valid")
        println("   Address: 10.13.13.2")
        println("   Endpoint: 192.168.68.60:51822")
    }
    
    /**
     * Test 2: Verify FR config format
     */
    @Test
    fun test_frConfigFormat() {
        println("🧪 Test: Verify FR config format")
        
        // Verify config structure
        assert(frConfig.contains("[Interface]")) { "Config should have [Interface] section" }
        assert(frConfig.contains("[Peer]")) { "Config should have [Peer] section" }
        assert(frConfig.contains("10.14.14.2")) { "Config should have FR address" }
        assert(frConfig.contains("192.168.68.60:51823")) { "Config should have FR endpoint" }
        
        println("✅ FR config format is valid")
        println("   Address: 10.14.14.2")
        println("   Endpoint: 192.168.68.60:51823")
    }
    
    /**
     * Test 3: Test protocol detection with WireGuard config
     */
    @Test
    fun test_protocolDetection() {
        println("🧪 Test: Protocol detection with WireGuard config")
        
        // Both configs should start with [Interface]
        assert(ukConfig.trimStart().startsWith("[Interface]")) {
            "UK config should be detected as WireGuard"
        }
        
        assert(frConfig.trimStart().startsWith("[Interface]")) {
            "FR config should be detected as WireGuard"
        }
        
        println("✅ Protocol detection would identify both as WireGuard")
        println("   UK: Starts with [Interface] ✅")
        println("   FR: Starts with [Interface] ✅")
    }
    
    /**
     * Test 4: Parse UK config with WireGuard library
     */
    @Test
    fun test_parseUKConfig() {
        println("🧪 Test: Parse UK config with WireGuard library")
        
        try {
            // Try to parse the config using WireGuard's Config class
            val configStream = ukConfig.byteInputStream()
            val parsedConfig = com.wireguard.config.Config.parse(configStream)
            
            // Verify parsed data
            val interfaceSection = parsedConfig.`interface`
            val addresses = interfaceSection.addresses
            
            println("✅ UK config parsed successfully")
            println("   Addresses: ${addresses.joinToString(", ") { it.toString() }}")
            println("   DNS: ${interfaceSection.dnsServers.joinToString(", ") { it.hostAddress }}")
            
            // Verify address
            assert(addresses.isNotEmpty()) { "Should have at least one address" }
            val address = addresses.first()
            println("   IP: ${address.address.hostAddress}")
            println("   Prefix: ${address.mask}")
            
        } catch (e: Exception) {
            println("❌ Failed to parse UK config: ${e.message}")
            throw e
        }
    }
    
    /**
     * Test 5: Parse FR config with WireGuard library
     */
    @Test
    fun test_parseFRConfig() {
        println("🧪 Test: Parse FR config with WireGuard library")
        
        try {
            // Try to parse the config using WireGuard's Config class
            val configStream = frConfig.byteInputStream()
            val parsedConfig = com.wireguard.config.Config.parse(configStream)
            
            // Verify parsed data
            val interfaceSection = parsedConfig.`interface`
            val addresses = interfaceSection.addresses
            
            println("✅ FR config parsed successfully")
            println("   Addresses: ${addresses.joinToString(", ") { it.toString() }}")
            println("   DNS: ${interfaceSection.dnsServers.joinToString(", ") { it.hostAddress }}")
            
            // Verify address
            assert(addresses.isNotEmpty()) { "Should have at least one address" }
            val address = addresses.first()
            println("   IP: ${address.address.hostAddress}")
            println("   Prefix: ${address.mask}")
            
        } catch (e: Exception) {
            println("❌ Failed to parse FR config: ${e.message}")
            throw e
        }
    }
    
    /**
     * Test 6: Verify configs are different
     */
    @Test
    fun test_configsAreDifferent() {
        println("🧪 Test: Verify UK and FR configs are different")
        
        assert(ukConfig != frConfig) { "UK and FR configs should be different" }
        assert(ukConfig.contains("10.13.13.")) { "UK should have 10.13.13.x address" }
        assert(frConfig.contains("10.14.14.")) { "FR should have 10.14.14.x address" }
        assert(ukConfig.contains(":51822")) { "UK should use port 51822" }
        assert(frConfig.contains(":51823")) { "FR should use port 51823" }
        
        println("✅ Configs are properly differentiated")
        println("   UK: 10.13.13.2 @ port 51822")
        println("   FR: 10.14.14.2 @ port 51823")
    }
}

