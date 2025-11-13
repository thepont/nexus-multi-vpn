package com.multiregionvpn

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.multiregionvpn.core.VpnEngineService
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Ensures starting and stopping the VPN service updates the exposed state
 * and clears connection mappings.
 */
@RunWith(AndroidJUnit4::class)
class VpnToggleStateTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() = runBlocking {
        stopVpn()
        waitForStop()
    }

    @Test
    fun startAndStopUpdatesServiceState() = runBlocking {
        assertFalse(VpnEngineService.isRunning())

        startVpn()
        waitForStart()
        assertTrue(VpnEngineService.isRunning())

        stopVpn()
        waitForStop()
        assertFalse(VpnEngineService.isRunning())
        val snapshot = VpnEngineService.getConnectionTrackerSnapshot()
        assertTrue("Expected no mappings after stop", snapshot.isEmpty())
    }

    private fun startVpn() {
        val intent = Intent(context, VpnEngineService::class.java).apply {
            action = VpnEngineService.ACTION_START
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun stopVpn() {
        val intent = Intent(context, VpnEngineService::class.java).apply {
            action = VpnEngineService.ACTION_STOP
        }
        context.startService(intent)
    }

    private suspend fun waitForStart(timeoutMs: Long = 5_000L) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (VpnEngineService.isRunning()) return
            delay(250)
        }
        assertTrue("VpnEngineService should be running", VpnEngineService.isRunning())
    }

    private suspend fun waitForStop(timeoutMs: Long = 5_000L) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (!VpnEngineService.isRunning()) {
                val snapshot = VpnEngineService.getConnectionTrackerSnapshot()
                if (snapshot.isEmpty()) return
            }
            delay(250)
        }
        assertFalse("VpnEngineService should be stopped", VpnEngineService.isRunning())
    }
}

