package com.multiregionvpn.core

import com.multiregionvpn.ui.shared.VpnStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before

class VpnEngineServiceLifecycleTest {

    private lateinit var service: TestVpnEngineService

    @Before
    fun setUp() {
        VpnServiceStateTracker.reset()
        service = TestVpnEngineService()
    }

    @After
    fun tearDown() {
        service.onDestroy()
        VpnServiceStateTracker.reset()
    }

    @Test
    fun `service reports connected when tunnels become ready`() = runBlocking {
        service.emitReadiness(setOf("nordvpn_UK"))

        waitUntil { VpnServiceStateTracker.status.value == VpnStatus.CONNECTED }

        assertEquals(0, service.onAllTunnelsLostCount)
    }

    @Test
    fun `service triggers shutdown when last tunnel is lost`() = runBlocking {
        service.emitReadiness(setOf("nordvpn_UK"))
        waitUntil { VpnServiceStateTracker.status.value == VpnStatus.CONNECTED }

        service.emitReadiness(emptySet())

        waitUntil {
            service.onAllTunnelsLostCount == 1
        }
        assertEquals(VpnStatus.CONNECTING, VpnServiceStateTracker.status.value)
    }

    @Test
    fun `service stays running while tunnels remain ready`() = runBlocking {
        service.emitReadiness(setOf("nordvpn_UK"))
        waitUntil { VpnServiceStateTracker.status.value == VpnStatus.CONNECTED }

        service.emitReadiness(setOf("nordvpn_UK", "wireguard_FR"))
        delay(50)

        assertEquals(0, service.onAllTunnelsLostCount)
        assertTrue(
            VpnServiceStateTracker.status.value == VpnStatus.CONNECTED,
            "VPN should remain in CONNECTED state while tunnels are ready"
        )
    }

    private suspend fun waitUntil(timeoutMs: Long = 1_000, condition: () -> Boolean) {
        withTimeout(timeoutMs) {
            while (!condition()) {
                delay(10)
            }
        }
    }

    private class TestVpnEngineService : VpnEngineService() {
        var onAllTunnelsLostCount: Int = 0

        fun emitReadiness(readyIds: Set<String>) {
            handleTunnelReadinessChange(readyIds)
        }

        override fun onAllTunnelsLost() {
            onAllTunnelsLostCount++
        }
    }
}

