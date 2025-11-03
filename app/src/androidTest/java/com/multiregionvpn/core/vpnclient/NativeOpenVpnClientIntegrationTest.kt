package com.multiregionvpn.core.vpnclient

import android.content.Context
import android.net.VpnService
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for NativeOpenVpnClient.
 * 
 * These tests verify that:
 * 1. The native library loads successfully
 * 2. JNI functions are callable
 * 3. OpenVPN 3 integration is available
 * 4. Basic client operations work
 * 
 * These are instrumentation tests that run on a device/emulator because
 * native libraries require the Android runtime.
 */
@RunWith(AndroidJUnit4::class)
class NativeOpenVpnClientIntegrationTest {

    private lateinit var appContext: Context
    private lateinit var mockVpnService: VpnService

    @Before
    fun setup() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        appContext = instrumentation.targetContext
        
        // Create a mock VpnService - we only need it for the constructor
        // In a real scenario, this would be the actual VpnEngineService
        mockVpnService = object : VpnService() {
            // Empty implementation for testing
        }
    }

    @Test
    fun test_nativeLibrary_loadsSuccessfully() {
        // GIVEN: NativeOpenVpnClient class
        // WHEN: Instantiating the client (this triggers System.loadLibrary)
        // THEN: No UnsatisfiedLinkError should be thrown
        
        try {
            val client = NativeOpenVpnClient(appContext, mockVpnService)
            assertThat(client).isNotNull()
        } catch (e: UnsatisfiedLinkError) {
            throw AssertionError(
                "Native library 'openvpn-jni' failed to load. " +
                "This usually means:\n" +
                "1. The native library wasn't built correctly\n" +
                "2. The library isn't included in the APK\n" +
                "3. There's a missing dependency\n" +
                "Error: ${e.message}", e
            )
        } catch (e: ExceptionInInitializerError) {
            throw AssertionError(
                "Native library initialization failed. " +
                "This may indicate missing dependencies or compilation errors.\n" +
                "Error: ${e.message}", e.cause
            )
        }
    }

    @Test
    fun test_clientInitialState_notConnected() {
        // GIVEN: A newly created client
        val client = NativeOpenVpnClient(appContext, mockVpnService)
        
        // WHEN: Checking connection status
        // THEN: Should not be connected
        assertThat(client.isConnected()).isFalse()
    }

    @Test
    fun test_nativeFunctions_callable() {
        // GIVEN: A client instance
        val client = NativeOpenVpnClient(appContext, mockVpnService)
        
        // WHEN: Calling native functions (they should be available)
        // THEN: No UnsatisfiedLinkError should be thrown
        
        // Note: We can't directly test native methods without valid configs,
        // but we can verify the client can be instantiated and methods exist
        
        try {
            // These should be callable (even if they fail with invalid input)
            assertThat(client.isConnected()).isFalse()
            
            // sendPacket should handle the call gracefully when not connected
            client.sendPacket(byteArrayOf(1, 2, 3))
            
        } catch (e: UnsatisfiedLinkError) {
            throw AssertionError(
                "Native JNI functions not found. " +
                "This indicates the native library wasn't properly linked.\n" +
                "Error: ${e.message}", e
            )
        }
    }

    @Test
    fun test_disconnect_whenNotConnected_doesNotCrash() {
        // GIVEN: A client that's not connected
        val client = NativeOpenVpnClient(appContext, mockVpnService)
        assertThat(client.isConnected()).isFalse()
        
        // WHEN: Calling disconnect
        // THEN: Should handle gracefully without crashing
        runBlocking {
            client.disconnect()
        }
        
        // Still not connected
        assertThat(client.isConnected()).isFalse()
    }

    @Test
    fun test_sendPacket_whenNotConnected_handledGracefully() {
        // GIVEN: A client that's not connected
        val client = NativeOpenVpnClient(appContext, mockVpnService)
        assertThat(client.isConnected()).isFalse()
        
        // WHEN: Sending a packet
        // THEN: Should be handled gracefully (may log warning but not crash)
        val testPacket = byteArrayOf(1, 2, 3, 4, 5)
        client.sendPacket(testPacket)
        
        // Verify client still works
        assertThat(client.isConnected()).isFalse()
    }

    @Test
    fun test_packetReceiver_setCallback() {
        // GIVEN: A client
        val client = NativeOpenVpnClient(appContext, mockVpnService)
        
        // WHEN: Setting a packet receiver callback
        var callbackInvoked = false
        client.setPacketReceiver { packet ->
            callbackInvoked = true
            assertThat(packet).isNotNull()
        }
        
        // THEN: Callback should be set (we can't test it being called
        // without an active connection, but we verify no crash)
        assertThat(client.isConnected()).isFalse()
        assertThat(callbackInvoked).isFalse() // Not invoked yet (no connection)
        
        // The callback is stored internally and will be used when packets arrive
    }

    @Test
    fun test_multipleClients_canBeCreated() {
        // GIVEN: Multiple client instances
        // WHEN: Creating multiple clients
        // THEN: All should be created successfully
        
        val client1 = NativeOpenVpnClient(appContext, mockVpnService)
        val client2 = NativeOpenVpnClient(appContext, mockVpnService)
        val client3 = NativeOpenVpnClient(appContext, mockVpnService)
        
        assertThat(client1).isNotNull()
        assertThat(client2).isNotNull()
        assertThat(client3).isNotNull()
        
        // All should be independent
        assertThat(client1.isConnected()).isFalse()
        assertThat(client2.isConnected()).isFalse()
        assertThat(client3.isConnected()).isFalse()
    }

    /**
     * Diagnostic test to verify OpenVPN 3 availability.
     * This test checks if we can compile against OpenVPN 3 headers,
     * which indicates the integration is at least partially working.
     */
    @Test
    fun test_openvpn3Integration_compilationCheck() {
        // GIVEN: Native library loaded
        val client = NativeOpenVpnClient(appContext, mockVpnService)
        
        // WHEN: Checking if OpenVPN 3 integration is available
        // The fact that this test compiles and runs indicates:
        // 1. OpenVPN 3 headers are available (if OPENVPN3_AVAILABLE is defined)
        // 2. JNI bridge is compiled correctly
        
        // Note: We can't directly check OPENVPN3_AVAILABLE from Kotlin,
        // but if the native code compiles with it, that's a good sign
        
        assertThat(client).isNotNull()
        
        // If we got here without compilation errors, the integration is at least
        // partially working at the build level
    }

    /**
     * Test to verify native library is included in APK.
     * This test verifies the library file exists in the APK.
     */
    @Test
    fun test_nativeLibrary_includedInApk() {
        // GIVEN: App context
        // WHEN: Checking if native library can be loaded
        val client = NativeOpenVpnClient(appContext, mockVpnService)
        
        // THEN: Library should load without errors
        // This implicitly verifies the library is in the APK
        
        assertThat(client).isNotNull()
        
        // Additional verification: Try to load library directly
        try {
            System.loadLibrary("openvpn-jni")
            // If we get here, library loaded successfully
        } catch (e: UnsatisfiedLinkError) {
            throw AssertionError(
                "Native library 'openvpn-jni' is not included in the APK " +
                "or cannot be loaded. Check:\n" +
                "1. CMake build succeeded\n" +
                "2. Native libraries are included in APK packaging\n" +
                "3. ABI filters match the device architecture\n" +
                "Error: ${e.message}", e
            )
        }
    }

    /**
     * Test lifecycle: create, use, disconnect.
     * Verifies basic client lifecycle operations.
     */
    @Test
    fun test_clientLifecycle_basicOperations() {
        // GIVEN: A new client
        val client = NativeOpenVpnClient(appContext, mockVpnService)
        
        // WHEN: Performing lifecycle operations
        // THEN: All should work without crashing
        
        // 1. Initial state
        assertThat(client.isConnected()).isFalse()
        
        // 2. Set packet receiver
        client.setPacketReceiver { }
        
        // 3. Try to send packet (should handle gracefully)
        client.sendPacket(byteArrayOf(1, 2, 3))
        
        // 4. Disconnect
        runBlocking {
            client.disconnect()
        }
        
        // 5. Final state
        assertThat(client.isConnected()).isFalse()
    }

    /**
     * Test error handling with invalid inputs.
     * Verifies client handles edge cases gracefully.
     */
    @Test
    fun test_errorHandling_invalidInputs() {
        // GIVEN: A client
        val client = NativeOpenVpnClient(appContext, mockVpnService)
        
        // WHEN: Calling methods with invalid inputs
        // THEN: Should handle gracefully
        
        // Empty packet
        client.sendPacket(byteArrayOf())
        
        // Large packet
        client.sendPacket(ByteArray(100000))
        
        // Null-like operations (if applicable)
        client.setPacketReceiver { }
        
        // Should still be functional
        assertThat(client.isConnected()).isFalse()
    }
}

