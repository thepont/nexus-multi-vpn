package com.multiregionvpn.core.vpnclient

import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.Ignore
import org.junit.Test
import android.content.Context
import android.net.VpnService

/**
 * Unit tests for NativeOpenVpnClient
 * 
 * NOTE: These tests are ignored because native libraries cannot be loaded
 * in JVM unit tests - they require Android runtime (use instrumentation tests instead).
 */
class NativeOpenVpnClientTest {

    @Test
    @Ignore("Native libraries require Android runtime - use instrumentation tests on device/emulator")
    fun test_nativeLibraryLoads() {
        // This test should be run as an instrumentation test on a device/emulator
        // Native libraries cannot load in unit test environment
        val context = mockk<Context>(relaxed = true)
        val vpnService = mockk<VpnService>(relaxed = true)
        
        val client = NativeOpenVpnClient(context, vpnService)
        assertThat(client).isNotNull()
        assertThat(client.isConnected()).isFalse()
    }
    
    @Test
    @Ignore("Native libraries require Android runtime - use instrumentation tests on device/emulator")
    fun test_initialState() {
        // This test should be run as an instrumentation test on a device/emulator
        // Native libraries cannot load in unit test environment
        val context = mockk<Context>(relaxed = true)
        val vpnService = mockk<VpnService>(relaxed = true)
        val client = NativeOpenVpnClient(context, vpnService)
        
        assertThat(client.isConnected()).isFalse()
    }
    
    // Note: Actual connection tests require:
    // 1. OpenVPN 3 library integrated
    // 2. Valid VPN configuration
    // 3. Network access
    // These will be added once OpenVPN 3 is fully integrated
}

