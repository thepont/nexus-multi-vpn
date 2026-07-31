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

    @Test
    @Ignore("Native libraries require Android runtime - use instrumentation tests on device/emulator")
    fun test_connect_deletesAuthFileImmediately() = kotlinx.coroutines.test.runTest {
        val context = mockk<Context>(relaxed = true)
        val vpnService = mockk<VpnService>(relaxed = true)

        // Create a temporary auth file
        val authFile = java.io.File.createTempFile("auth_test", ".txt")
        authFile.writeText("username\npassword\n")
        assertThat(authFile.exists()).isTrue()

        val client = NativeOpenVpnClient(context, vpnService)
        try {
            // This will try to read the auth file and then call nativeConnect.
            // Since JNI won't load on JVM, this will throw an UnsatisfiedLinkError or similar exception,
            // but the try-finally block in connect() must still delete the file!
            client.connect("client\nremote 127.0.0.1 1194", authFile.absolutePath)
        } catch (e: Throwable) {
            // Expect exception because JNI is not available
        }

        // Verify file was deleted regardless of JNI or connection success/failure
        assertThat(authFile.exists()).isFalse()
    }
    
    // Note: Actual connection tests require:
    // 1. OpenVPN 3 library integrated
    // 2. Valid VPN configuration
    // 3. Network access
    // These will be added once OpenVPN 3 is fully integrated
}

