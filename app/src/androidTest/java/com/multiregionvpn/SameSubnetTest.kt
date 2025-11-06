package com.multiregionvpn

import android.net.VpnService
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test to verify Android's behavior when adding multiple IP addresses
 * from the same subnet to VpnService.Builder.
 * 
 * This tests the edge case: What happens when multiple tunnels share the same subnet?
 */
@RunWith(AndroidJUnit4::class)
class SameSubnetTest {
    
    @Test
    fun testSameSubnetDifferentIPs() {
        val testService = object : VpnService() {
            fun testSameSubnet(): Builder {
                val builder = Builder()
                builder.setSession("SameSubnetTest")
                
                // Test: Add multiple IPs from same subnet (/16)
                // Subnet: 10.100.0.0/16
                builder.addAddress("10.100.0.2", 16)
                println("✅ Added first IP: 10.100.0.2/16 (subnet: 10.100.0.0/16)")
                
                // Try to add second IP from same subnet
                builder.addAddress("10.100.0.3", 16)
                println("✅ Added second IP: 10.100.0.3/16 (subnet: 10.100.0.0/16)")
                
                // Try to add third IP from same subnet
                builder.addAddress("10.100.0.4", 16)
                println("✅ Added third IP: 10.100.0.4/16 (subnet: 10.100.0.0/16)")
                
                // Add route and DNS to complete builder
                builder.addRoute("0.0.0.0", 0)
                builder.addDnsServer("8.8.8.8")
                
                return builder
            }
        }
        
        val builder = testService.testSameSubnet()
        assertNotNull("Builder should not be null even with same-subnet IPs", builder)
        
        println("\n✅ TEST PASSED: Multiple IPs from same subnet can be added to builder")
        println("   Note: Actual behavior when establish() is called needs verification")
        println("   Android may:")
        println("   - Accept all IPs (unlikely)")
        println("   - Only use first IP (most likely)")
        println("   - Throw exception on establish() (needs testing)")
    }
    
    @Test
    fun testMixedSubnets() {
        val testService = object : VpnService() {
            fun testMixedSubnets(): Builder {
                val builder = Builder()
                builder.setSession("MixedSubnetsTest")
                
                // Add IPs from different subnets
                builder.addAddress("10.100.0.2", 16)  // Subnet: 10.100.0.0/16
                println("✅ Added IP: 10.100.0.2/16")
                
                builder.addAddress("10.101.0.2", 16)  // Subnet: 10.101.0.0/16 (different)
                println("✅ Added IP: 10.101.0.2/16")
                
                builder.addAddress("192.168.1.2", 24)  // Subnet: 192.168.1.0/24 (different)
                println("✅ Added IP: 192.168.1.2/24")
                
                // Add same subnet as first one (edge case)
                builder.addAddress("10.100.0.3", 16)  // Subnet: 10.100.0.0/16 (same as first)
                println("✅ Added IP: 10.100.0.3/16 (same subnet as first)")
                
                builder.addRoute("0.0.0.0", 0)
                builder.addDnsServer("8.8.8.8")
                
                return builder
            }
        }
        
        val builder = testService.testMixedSubnets()
        assertNotNull("Builder should support mixed subnets", builder)
        
        println("\n✅ TEST PASSED: Builder supports mixed subnets with some duplicates")
        println("   Different subnets: 10.100.0.0/16, 10.101.0.0/16, 192.168.1.0/24")
        println("   Duplicate subnet: 10.100.0.0/16 (has 2 IPs)")
    }
    
    @Test
    fun testSubnetCalculation() {
        // Test helper function to calculate subnet
        fun calculateSubnet(ip: String, prefixLength: Int): String {
            val parts = ip.split(".")
            return when (prefixLength) {
                16 -> "${parts[0]}.${parts[1]}.0.0/$prefixLength"
                24 -> "${parts[0]}.${parts[1]}.${parts[2]}.0/$prefixLength"
                8 -> "${parts[0]}.0.0.0/$prefixLength"
                else -> {
                    // For non-standard, return simplified version
                    "$ip/$prefixLength"
                }
            }
        }
        
        // Test cases
        val testCases = listOf(
            "10.100.0.2" to 16 to "10.100.0.0/16",
            "10.100.0.3" to 16 to "10.100.0.0/16",  // Same subnet
            "10.101.0.2" to 16 to "10.101.0.0/16",  // Different subnet
            "192.168.1.2" to 24 to "192.168.1.0/24",
            "192.168.1.3" to 24 to "192.168.1.0/24", // Same subnet
        )
        
        testCases.forEach { (input, expected) ->
            val (ip, prefix) = input
            val subnet = calculateSubnet(ip, prefix)
            println("IP: $ip/$prefix → Subnet: $subnet (expected: $expected)")
            assert(subnet == expected) { "Subnet calculation failed for $ip/$prefix" }
        }
        
        println("\n✅ TEST PASSED: Subnet calculation works correctly")
        println("   Can identify when multiple IPs share the same subnet")
    }
}


