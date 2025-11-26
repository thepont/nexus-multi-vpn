package com.multiregionvpn.core.vpnclient

import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import android.net.VpnService

/**
 * Instrumentation tests for NativeOpenVpnClient
 */
@RunWith(AndroidJUnit4::class)
class NativeOpenVpnClientInstrumentationTest {

    @Test
    fun test_nativeLibraryLoads() {
        val context = mockk<Context>(relaxed = true)
        val vpnService = mockk<VpnService>(relaxed = true)
        
        val client = NativeOpenVpnClient(context, vpnService)
        assertThat(client).isNotNull()
        assertThat(client.isConnected()).isFalse()
    }
    
    @Test
    fun test_initialState() {
        val context = mockk<Context>(relaxed = true)
        val vpnService = mockk<VpnService>(relaxed = true)
        val client = NativeOpenVpnClient(context, vpnService)
        
        assertThat(client.isConnected()).isFalse()
    }
}
